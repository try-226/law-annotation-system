package com.law.annotation.revision;

import com.law.annotation.auth.AuthErrorCodes;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.law.ArticleSnapshot;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawRepository;
import com.law.annotation.law.PendingChangeSet;
import com.law.annotation.revision.dto.CreateRevisionTaskRequest;
import com.law.annotation.task.TaskErrorCodes;
import com.law.annotation.task.TaskService;
import com.law.annotation.task.dto.TaskDetailResponse;
import com.law.annotation.version.AnnotationVersionDocument;
import com.law.annotation.version.AnnotationVersionRepository;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RevisionService {

    private final LawRepository lawRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final AnnotationVersionRepository annotationVersionRepository;
    private final TaskService taskService;

    public RevisionService(
            LawRepository lawRepository,
            ContentVersionRepository contentVersionRepository,
            AnnotationVersionRepository annotationVersionRepository,
            TaskService taskService) {
        this.lawRepository = lawRepository;
        this.contentVersionRepository = contentVersionRepository;
        this.annotationVersionRepository = annotationVersionRepository;
        this.taskService = taskService;
    }

    public TaskDetailResponse create(
            CreateRevisionTaskRequest request,
            UserPrincipal currentUser) {
        requireAdmin(currentUser);
        if (request == null) {
            throw validation("request", "不能为空");
        }
        String lawId = requireIdentifier(request.getLawId(), "lawId");
        LawDocument law = lawRepository.findById(lawId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                TaskErrorCodes.LAW_NOT_FOUND,
                "法律不存在"));
        if (law.getDeletedAt() != null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TaskErrorCodes.LAW_DELETED,
                    "已删除的法律不能创建修订任务");
        }
        String baseAnnotationVersionId = law.getCurrentAnnotationVersionId();
        if (baseAnnotationVersionId == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    RevisionErrorCodes.CURRENT_ANNOTATION_REQUIRED,
                    "法律尚无正式标注版本，不能创建修订任务");
        }
        AnnotationVersionDocument baseAnnotation = annotationVersionRepository
                .findById(baseAnnotationVersionId)
                .filter(version -> lawId.equals(version.getLawId()))
                .orElseThrow(RevisionService::invalidBaseAnnotation);
        ContentVersionDocument baseContent = contentVersionRepository
                .findById(baseAnnotation.getContentVersionId())
                .filter(version -> lawId.equals(version.getLawId()))
                .orElseThrow(RevisionService::invalidBaseAnnotation);
        ContentVersionDocument latestContent = contentVersionRepository
                .findById(law.getCurrentContentVersionId())
                .filter(version -> lawId.equals(version.getLawId()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        TaskErrorCodes.CONTENT_VERSION_INVALID,
                        "法律当前内容版本无效"));
        validateBaseAnnotation(baseAnnotation, baseContent);

        RevisionScope scope = resolveScope(
                law,
                baseAnnotation,
                latestContent,
                request.isOverall(),
                request.getArticleIds());
        return taskService.createRevisionTask(
                lawId,
                request.getAnnotatorId(),
                request.getTaskName(),
                request.getRemark(),
                currentUser.id(),
                latestContent.getId(),
                baseAnnotation.getId(),
                scope);
    }

    private static RevisionScope resolveScope(
            LawDocument law,
            AnnotationVersionDocument baseAnnotation,
            ContentVersionDocument latestContent,
            boolean overall,
            List<String> requestedArticleIds) {
        PendingChangeSet pending = law.getPendingChangeSet();
        if (pending == null) {
            throw invalidPendingState("pendingChangeSet缺失");
        }
        if (!law.isPendingRevision() && !pending.isEmpty()) {
            throw invalidPendingState("非待修订法律包含pendingChangeSet");
        }
        if (law.isPendingRevision() && pending.isEmpty()) {
            throw invalidPendingState("待修订法律缺少semantic pendingChangeSet");
        }

        LinkedHashSet<String> requested = normalizeRequestedIds(requestedArticleIds);
        List<String> currentOrder = latestContent.getSemanticArticlesSnapshot().stream()
                .map(ArticleSnapshot::getArticleId)
                .toList();
        Set<String> currentIds = Set.copyOf(currentOrder);
        Set<String> baseIds = baseAnnotation.getArticleResults().keySet();

        if (!law.isPendingRevision()) {
            if (!Objects.equals(baseAnnotation.getContentVersionId(), latestContent.getId())) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        RevisionErrorCodes.BASIS_CHANGED,
                        "法律内容版本与当前正式标注版本不一致");
            }
            for (String articleId : requested) {
                if (!currentIds.contains(articleId)) {
                    throw articleNotInLatest(articleId);
                }
            }
            List<String> selected = currentOrder.stream().filter(requested::contains).toList();
            if (!overall && selected.isEmpty()) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        RevisionErrorCodes.SCOPE_EMPTY,
                        "标注修正型修订至少需要一个修订项目");
            }
            return new RevisionScope(
                    RevisionMode.ANNOTATION_ONLY, overall, selected, List.of());
        }

        Set<String> added = pending.getAddedArticleIds();
        Set<String> modified = pending.getModifiedArticleIds();
        Set<String> deleted = pending.getDeletedArticleIds();
        for (String articleId : added) {
            if (!currentIds.contains(articleId) || baseIds.contains(articleId)) {
                throw invalidPendingState("ADDED法条与基础A或latest C不一致");
            }
        }
        for (String articleId : modified) {
            if (!currentIds.contains(articleId) || !baseIds.contains(articleId)) {
                throw invalidPendingState("MODIFIED法条与基础A或latest C不一致");
            }
        }
        for (String articleId : deleted) {
            if (currentIds.contains(articleId) || !baseIds.contains(articleId)) {
                throw invalidPendingState("DELETED法条与基础A或latest C不一致");
            }
        }
        LinkedHashSet<String> mandatory = new LinkedHashSet<>();
        mandatory.addAll(added);
        mandatory.addAll(modified);
        for (String articleId : currentIds) {
            if (!mandatory.contains(articleId) && !baseIds.contains(articleId)) {
                throw invalidPendingState("scope外当前法条在基础A中缺少正式标注");
            }
        }
        for (String articleId : requested) {
            if (deleted.contains(articleId)) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        RevisionErrorCodes.DELETED_ARTICLE_REQUESTED,
                        "已删除法条不能进入修订范围",
                        List.of(new ErrorLocator("articleIds", articleId)));
            }
            if (!currentIds.contains(articleId)) {
                throw articleNotInLatest(articleId);
            }
            if (!mandatory.contains(articleId)) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        RevisionErrorCodes.CONTENT_CHANGE_SCOPE_INVALID,
                        "正文变更型修订不能额外加入未变化法条",
                        List.of(new ErrorLocator("articleIds", articleId)));
            }
        }
        List<String> mandatoryInOrder = currentOrder.stream()
                .filter(mandatory::contains)
                .toList();
        return new RevisionScope(
                RevisionMode.CONTENT_CHANGE,
                overall,
                mandatoryInOrder,
                mandatoryInOrder);
    }

    private static void validateBaseAnnotation(
            AnnotationVersionDocument annotation,
            ContentVersionDocument content) {
        Set<String> contentIds = content.getSemanticArticlesSnapshot().stream()
                .map(ArticleSnapshot::getArticleId)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, ?> results = annotation.getArticleResults();
        if (annotation.getOverallResult() == null
                || !results.keySet().equals(contentIds)
                || results.values().stream().anyMatch(Objects::isNull)) {
            throw invalidBaseAnnotation();
        }
    }

    private static LinkedHashSet<String> normalizeRequestedIds(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            if (value == null || value.isBlank()) {
                throw validation("articleIds[" + index + "]", "不能为空");
            }
            result.add(requireIdentifier(value, "articleIds[" + index + "]"));
        }
        return result;
    }

    private static void requireAdmin(UserPrincipal currentUser) {
        if (currentUser == null || currentUser.role() != Role.ADMIN) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    AuthErrorCodes.FORBIDDEN,
                    "无权执行此操作");
        }
    }

    private static String requireIdentifier(String value, String path) {
        if (value == null || value.isBlank()) {
            throw validation(path, "不能为空");
        }
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > 100
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw validation(path, "须为1至100个字符且不得包含控制字符");
        }
        return normalized;
    }

    private static ApiException invalidBaseAnnotation() {
        return new ApiException(
                HttpStatus.CONFLICT,
                RevisionErrorCodes.BASE_ANNOTATION_INVALID,
                "当前正式标注版本与其内容版本不一致");
    }

    private static ApiException invalidPendingState(String message) {
        return new ApiException(
                HttpStatus.CONFLICT,
                RevisionErrorCodes.CONTENT_CHANGE_SCOPE_INVALID,
                message);
    }

    private static ApiException articleNotInLatest(String articleId) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                RevisionErrorCodes.ARTICLE_NOT_IN_LATEST_CONTENT,
                "法条不属于当前最新内容版本",
                List.of(new ErrorLocator("articleIds", articleId)));
    }

    private static ApiException validation(String path, String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "COMMON.VALIDATION_FAILED",
                "请求参数校验失败",
                List.of(new ErrorLocator(path, message)));
    }
}
