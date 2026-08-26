package com.law.annotation.annotation;

import com.law.annotation.annotation.dto.AnnotationProgressResponse;
import com.law.annotation.annotation.dto.SaveArticleDraftRequest;
import com.law.annotation.annotation.dto.SaveOverallDraftRequest;
import com.law.annotation.common.enums.ItemType;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.field.FieldConfigScope;
import com.law.annotation.field.FieldConfigSnapshotItem;
import com.law.annotation.field.FixedAnnotationField;
import com.law.annotation.task.TaskArticleSnapshot;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskStructureNodeSnapshot;
import com.law.annotation.review.ReviewItemLocator;
import com.law.annotation.review.ReviewScopeType;
import com.law.annotation.revision.RevisionScope;
import com.law.annotation.common.enums.TaskType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

final class AnnotationDraftRules {

    private static final int MAX_KEYWORDS_LENGTH = 200;
    private static final int MAX_KEYWORD_COUNT = 20;
    private static final int MAX_SINGLE_KEYWORD_LENGTH = 30;
    private static final Pattern KEYWORD_SEPARATOR = Pattern.compile("[,，]", Pattern.UNICODE_CASE);

    private AnnotationDraftRules() {
    }

    static OverallDraftValues normalizeOverall(SaveOverallDraftRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return new OverallDraftValues(
                normalizeSelect(
                        request.getLawCategory(),
                        FixedAnnotationField.LAW_CATEGORY,
                        "overall.lawCategory"),
                normalizeKeywords(request.getOverallKeywords(), "overall.overallKeywords"),
                normalizeText(request.getSummary(), 2000, "overall.summary"),
                normalizeText(request.getOverallNote(), 1000, "overall.overallNote"));
    }

    static ArticleDraftValues normalizeArticle(SaveArticleDraftRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String itemType = normalizeSelect(
                request.getItemType(),
                FixedAnnotationField.ITEM_TYPE,
                "article.itemType");
        return new ArticleDraftValues(
                itemType == null ? null : ItemType.valueOf(itemType),
                normalizeKeywords(request.getKeywords(), "article.keywords"),
                normalizeText(request.getSubjects(), 200, "article.subjects"),
                normalizeText(request.getLegalLiability(), 1000, "article.legalLiability"),
                normalizeText(request.getAnnotationNote(), 1000, "article.annotationNote"));
    }

    static AnnotationProgressResponse progress(TaskDocument task, TaskDraftDocument draft) {
        if (task.getTaskType() == TaskType.REVISION && task.getRevisionScope() != null) {
            RevisionScope scope = task.getRevisionScope();
            Map<String, ArticleDraftValues> articles = draft == null
                    ? Map.of()
                    : draft.getPerArticleDrafts();
            int filledArticles = (int) scope.articleIds().stream()
                    .filter(articleId -> articles.containsKey(articleId)
                            && isArticleComplete(task, articles.get(articleId)))
                    .count();
            boolean overallCompleted = !scope.overall()
                    || (draft != null
                            && draft.getOverallDraft() != null
                            && isOverallComplete(task, draft.getOverallDraft()));
            return new AnnotationProgressResponse(
                    scope.articleIds().size(), filledArticles, overallCompleted);
        }
        int totalArticles = task.getContentVersionSnapshot().articles().size();
        Map<String, ArticleDraftValues> articles = draft == null
                ? Map.of()
                : draft.getPerArticleDrafts();
        int filledArticles = (int) task.getContentVersionSnapshot().articles().stream()
                .filter(article -> isArticleComplete(task, articles.get(article.articleId())))
                .count();
        return new AnnotationProgressResponse(
                totalArticles,
                filledArticles,
                isOverallComplete(task, draft == null ? null : draft.getOverallDraft()));
    }

    static List<ErrorLocator> missingRequired(TaskDocument task, TaskDraftDocument draft) {
        List<ReviewItemLocator> fullScope = new ArrayList<>();
        fullScope.add(ReviewItemLocator.overall());
        task.getContentVersionSnapshot().articles().forEach(
                article -> fullScope.add(ReviewItemLocator.article(article.articleId())));
        return missingRequired(task, draft, fullScope);
    }

    static List<ErrorLocator> missingRequiredForRevision(
            TaskDocument task,
            TaskDraftDocument draft) {
        RevisionScope revisionScope = task.getRevisionScope();
        if (revisionScope == null) {
            throw new IllegalArgumentException("REVISION任务缺少revisionScope");
        }
        List<ErrorLocator> locators = new ArrayList<>();
        if (revisionScope.overall()
                && (draft == null || draft.getOverallDraft() == null)) {
            locators.add(new ErrorLocator("overall", "修订范围内的整体标注尚未保存"));
        }
        Map<String, ArticleDraftValues> articles = draft == null
                ? Map.of()
                : draft.getPerArticleDrafts();
        revisionScope.articleIds().stream()
                .filter(articleId -> !articles.containsKey(articleId)
                        || articles.get(articleId) == null)
                .forEach(articleId -> locators.add(new ErrorLocator(
                        "articles." + articleId,
                        "修订范围内的法条标注尚未保存")));
        if (!locators.isEmpty()) {
            return List.copyOf(locators);
        }
        return missingRequired(task, draft, revisionScope.toReviewScope());
    }

    static List<ErrorLocator> missingRequired(
            TaskDocument task,
            TaskDraftDocument draft,
            List<ReviewItemLocator> scope) {
        List<ErrorLocator> locators = new ArrayList<>();
        OverallDraftValues overall = draft == null ? null : draft.getOverallDraft();
        boolean includeOverall = scope.stream()
                .anyMatch(item -> item.type() == ReviewScopeType.OVERALL);
        if (includeOverall) {
            for (FieldConfigSnapshotItem item : task.getFieldConfigSnapshot().overall()) {
                if (item.required() && !hasOverallValue(overall, item.fieldKey())) {
                    locators.add(new ErrorLocator(
                            "overall." + item.fieldKey(),
                            displayName(item.fieldKey(), FieldConfigScope.OVERALL) + "未填写"));
                }
            }
        }

        Map<String, ArticleDraftValues> articleDrafts = draft == null
                ? Map.of()
                : draft.getPerArticleDrafts();
        for (TaskArticleSnapshot article : task.getContentVersionSnapshot().articles()) {
            boolean included = scope.stream().anyMatch(item ->
                    item.type() == ReviewScopeType.ARTICLE
                            && article.articleId().equals(item.articleId()));
            if (!included) {
                continue;
            }
            ArticleDraftValues values = articleDrafts.get(article.articleId());
            for (FieldConfigSnapshotItem item : task.getFieldConfigSnapshot().article()) {
                if (item.required() && !hasArticleValue(values, item.fieldKey())) {
                    String displayName = displayName(item.fieldKey(), FieldConfigScope.ARTICLE);
                    locators.add(new ErrorLocator(
                            "articles." + article.articleId() + "." + item.fieldKey(),
                            articleLocation(task, article) + "：" + displayName + "未填写"));
                }
            }
        }
        return List.copyOf(locators);
    }

    static boolean containsArticle(TaskDocument task, String articleId) {
        return task.getContentVersionSnapshot().articles().stream()
                .anyMatch(article -> article.articleId().equals(articleId));
    }

    private static boolean isOverallComplete(TaskDocument task, OverallDraftValues values) {
        return task.getFieldConfigSnapshot().overall().stream()
                .filter(FieldConfigSnapshotItem::required)
                .allMatch(item -> hasOverallValue(values, item.fieldKey()));
    }

    private static boolean isArticleComplete(TaskDocument task, ArticleDraftValues values) {
        return task.getFieldConfigSnapshot().article().stream()
                .filter(FieldConfigSnapshotItem::required)
                .allMatch(item -> hasArticleValue(values, item.fieldKey()));
    }

    private static boolean hasOverallValue(OverallDraftValues values, String fieldKey) {
        if (values == null) {
            return false;
        }
        return switch (fieldKey) {
            case "lawCategory" -> hasText(values.lawCategory());
            case "overallKeywords" -> hasText(values.overallKeywords());
            case "summary" -> hasText(values.summary());
            case "overallNote" -> hasText(values.overallNote());
            default -> false;
        };
    }

    private static boolean hasArticleValue(ArticleDraftValues values, String fieldKey) {
        if (values == null) {
            return false;
        }
        return switch (fieldKey) {
            case "itemType" -> values.itemType() != null;
            case "keywords" -> hasText(values.keywords());
            case "subjects" -> hasText(values.subjects());
            case "legalLiability" -> hasText(values.legalLiability());
            case "annotationNote" -> hasText(values.annotationNote());
            default -> false;
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalizeSelect(
            String value,
            FixedAnnotationField field,
            String path) {
        String normalized = normalizeText(value, 100, path);
        if (normalized != null && !field.allowedValues().contains(normalized)) {
            throw validation(path, "值不在允许范围内");
        }
        return normalized;
    }

    private static String normalizeKeywords(String value, String path) {
        String normalized = normalizeText(value, MAX_KEYWORDS_LENGTH, path);
        if (normalized == null) {
            return null;
        }
        String[] parts = KEYWORD_SEPARATOR.split(normalized, -1);
        if (parts.length > MAX_KEYWORD_COUNT) {
            throw validation(path, "关键词最多20个");
        }
        List<String> keywords = new ArrayList<>(parts.length);
        for (String part : parts) {
            String keyword = part.trim();
            int length = keyword.codePointCount(0, keyword.length());
            if (length < 1 || length > MAX_SINGLE_KEYWORD_LENGTH) {
                throw validation(path, "单个关键词须为1至30个字符且不能为空");
            }
            keywords.add(keyword);
        }
        String result = String.join(",", keywords);
        if (result.codePointCount(0, result.length()) > MAX_KEYWORDS_LENGTH) {
            throw validation(path, "关键词总长度不能超过200个字符");
        }
        return result;
    }

    private static String normalizeText(String value, int maxLength, String path) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        int length = normalized.codePointCount(0, normalized.length());
        boolean containsControl = normalized.codePoints().anyMatch(Character::isISOControl);
        if (length > maxLength || containsControl) {
            throw validation(path, "长度不能超过" + maxLength + "个字符且不得包含控制字符");
        }
        return normalized;
    }

    private static String displayName(String fieldKey, FieldConfigScope scope) {
        return FixedAnnotationField.findByKey(fieldKey)
                .filter(field -> field.scope() == scope)
                .map(FixedAnnotationField::displayName)
                .orElse(fieldKey);
    }

    private static String articleLocation(TaskDocument task, TaskArticleSnapshot article) {
        Map<String, TaskStructureNodeSnapshot> nodes = new HashMap<>();
        task.getStructureSnapshot().forEach(node -> nodes.put(node.nodeId(), node));
        TaskStructureNodeSnapshot articleNode = task.getStructureSnapshot().stream()
                .filter(node -> node.articleIds().contains(article.articleId()))
                .findFirst()
                .orElse(null);
        List<String> titles = new ArrayList<>();
        while (articleNode != null) {
            if (articleNode.title() != null && !articleNode.title().isBlank()) {
                titles.addFirst(articleNode.title().trim());
            }
            articleNode = articleNode.parentNodeId() == null
                    ? null
                    : nodes.get(articleNode.parentNodeId());
        }
        titles.add(article.number());
        return String.join(" / ", titles);
    }

    private static ApiException validation(String path, String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "COMMON.VALIDATION_FAILED",
                "请求参数校验失败",
                List.of(new ErrorLocator(path, message)));
    }
}
