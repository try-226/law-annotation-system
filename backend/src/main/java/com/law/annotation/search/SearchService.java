package com.law.annotation.search;

import com.law.annotation.annotation.ArticleDraftValues;
import com.law.annotation.annotation.OverallDraftValues;
import com.law.annotation.annotation.TaskDraftDocument;
import com.law.annotation.annotation.TaskDraftRepository;
import com.law.annotation.auth.AuthErrorCodes;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.ItemType;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.law.ArticleSnapshot;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawStructureNode;
import com.law.annotation.search.dto.SearchHitResponse;
import com.law.annotation.task.TaskArticleSnapshot;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskErrorCodes;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.TaskStateRules;
import com.law.annotation.task.TaskStructureNodeSnapshot;
import com.law.annotation.version.AnnotationVersionDocument;
import com.law.annotation.version.AnnotationVersionRepository;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int SNIPPET_BEFORE = 32;
    private static final int SNIPPET_AFTER = 48;
    private static final Pattern COMMON_TEXT_WHITESPACE = Pattern.compile("[\\r\\n\\t ]+");

    private final SearchRepository searchRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final AnnotationVersionRepository annotationVersionRepository;
    private final TaskRepository taskRepository;
    private final TaskDraftRepository taskDraftRepository;

    public SearchService(
            SearchRepository searchRepository,
            ContentVersionRepository contentVersionRepository,
            AnnotationVersionRepository annotationVersionRepository,
            TaskRepository taskRepository,
            TaskDraftRepository taskDraftRepository) {
        this.searchRepository = searchRepository;
        this.contentVersionRepository = contentVersionRepository;
        this.annotationVersionRepository = annotationVersionRepository;
        this.taskRepository = taskRepository;
        this.taskDraftRepository = taskDraftRepository;
    }

    public PageResponse<SearchHitResponse> searchLaws(
            String q,
            SearchScope scope,
            int page,
            int size) {
        SearchQuery query = validate(q, scope, page, size);
        List<LawDocument> laws = searchRepository.findVisibleLawsMatching(
                query.pattern(), query.scope());
        Map<String, ContentVersionDocument> versions = indexUnique(
                contentVersionRepository.findByIdIn(laws.stream()
                        .map(LawDocument::getCurrentContentVersionId)
                        .toList()),
                ContentVersionDocument::getId);
        List<String> annotationIds = query.scope().includesAnnotation()
                ? laws.stream()
                        .map(LawDocument::getCurrentAnnotationVersionId)
                        .filter(Objects::nonNull)
                        .toList()
                : List.of();
        Map<String, AnnotationVersionDocument> annotations = annotationIds.isEmpty()
                ? Map.of()
                : indexUnique(
                        annotationVersionRepository.findByIdIn(annotationIds),
                        AnnotationVersionDocument::getId);

        List<SearchHitResponse> hits = new ArrayList<>();
        for (LawDocument law : laws) {
            ContentVersionDocument version = versions.get(law.getCurrentContentVersionId());
            if (version == null || !law.getId().equals(version.getLawId())) {
                throw dataInconsistent("法律当前内容版本引用无效");
            }
            List<ArticleValue> articles = version.getSemanticArticlesSnapshot().stream()
                    .sorted(Comparator.comparingInt(ArticleSnapshot::getOrder)
                            .thenComparing(ArticleSnapshot::getArticleId))
                    .map(ArticleValue::from)
                    .toList();
            StructureContext structure = StructureContext.fromLaw(law.getStructure(), articles);
            if (query.scope().includesLawText()) {
                addAdminLawTextHits(hits, law, articles, structure, query.pattern());
            }
            if (query.scope().includesAnnotation()
                    && law.getCurrentAnnotationVersionId() != null) {
                AnnotationVersionDocument annotation = annotations.get(
                        law.getCurrentAnnotationVersionId());
                if (annotation == null
                        || !law.getId().equals(annotation.getLawId())
                        || !Objects.equals(
                                law.getCurrentContentVersionId(),
                                annotation.getContentVersionId())) {
                    continue;
                }
                addAnnotationHits(
                        hits,
                        law.getId(),
                        law.getName(),
                        articles,
                        structure,
                        annotation.getOverallResult(),
                        annotation.getArticleResults(),
                        query.pattern());
            }
        }
        return page(hits, page, size);
    }

    public PageResponse<SearchHitResponse> searchTask(
            String taskId,
            String q,
            SearchScope scope,
            int page,
            int size,
            UserPrincipal principal) {
        SearchQuery query = validate(q, scope, page, size);
        if (principal == null || principal.role() != Role.ANNOTATOR) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    AuthErrorCodes.FORBIDDEN,
                    "无权执行此操作");
        }
        String validTaskId = validateIdentifier(taskId);
        TaskDocument task = taskRepository
                .findByTaskIdAndAnnotatorId(validTaskId, principal.id())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        TaskErrorCodes.NOT_FOUND,
                        "任务不存在"));
        if (!TaskStateRules.unfinishedStates().contains(task.getTaskState())) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    TaskErrorCodes.NOT_FOUND,
                    "任务不存在");
        }
        if (task.getContentVersionSnapshot() == null
                || task.getLawBaseInfoSnapshot() == null
                || task.getStructureSnapshot() == null) {
            throw dataInconsistent("任务快照不完整");
        }

        List<ArticleValue> articles = task.getContentVersionSnapshot().articles().stream()
                .sorted(Comparator.comparingInt(TaskArticleSnapshot::order)
                        .thenComparing(TaskArticleSnapshot::articleId))
                .map(ArticleValue::from)
                .toList();
        StructureContext structure = StructureContext.fromTask(
                task.getStructureSnapshot(), articles);
        String lawName = task.getLawBaseInfoSnapshot().name();
        List<SearchHitResponse> hits = new ArrayList<>();
        if (query.scope().includesLawText()) {
            addArticleLawTextHits(
                    hits, task.getLawId(), lawName, articles, structure, query.pattern());
        }
        if (query.scope().includesAnnotation()) {
            TaskDraftDocument draft = taskDraftRepository.findById(task.getTaskId()).orElse(null);
            if (draft != null) {
                addAnnotationHits(
                        hits,
                        task.getLawId(),
                        lawName,
                        articles,
                        structure,
                        draft.getOverallDraft(),
                        draft.getPerArticleDrafts(),
                        query.pattern());
            }
        }
        return page(hits, page, size);
    }

    private static void addAdminLawTextHits(
            List<SearchHitResponse> hits,
            LawDocument law,
            List<ArticleValue> articles,
            StructureContext structure,
            Pattern pattern) {
        addHit(hits, law.getId(), law.getName(), null, null, List.of(),
                SearchHitSource.LAW_NAME, "law.name", law.getName(), pattern);
        addHit(hits, law.getId(), law.getName(), null, null, List.of(),
                SearchHitSource.ISSUING_AUTHORITY,
                "law.issuingAuthority",
                law.getIssuingAuthority(),
                pattern);
        addArticleLawTextHits(
                hits, law.getId(), law.getName(), articles, structure, pattern);
    }

    private static void addArticleLawTextHits(
            List<SearchHitResponse> hits,
            String lawId,
            String lawName,
            List<ArticleValue> articles,
            StructureContext structure,
            Pattern pattern) {
        Set<String> locatedStructureArticles = new HashSet<>();
        for (StructureValue node : structure.nodes()) {
            if (!pattern.matcher(node.title()).find()) {
                continue;
            }
            List<ArticleValue> descendants = articles.stream()
                    .filter(article -> structure.descendantArticleIds(node.nodeId())
                            .contains(article.articleId()))
                    .toList();
            if (descendants.isEmpty()) {
                addHit(hits, lawId, lawName, null, null, structure.nodePath(node.nodeId()),
                        SearchHitSource.STRUCTURE_TITLE,
                        "structure.title",
                        node.title(),
                        pattern);
            } else {
                for (ArticleValue article : descendants) {
                    if (locatedStructureArticles.add(article.articleId())) {
                        addHit(hits, lawId, lawName, article.articleId(), article.number(),
                                structure.articlePath(article.articleId()),
                                SearchHitSource.STRUCTURE_TITLE,
                                "structure.title",
                                node.title(),
                                pattern);
                    }
                }
            }
        }
        for (ArticleValue article : articles) {
            List<String> path = structure.articlePath(article.articleId());
            addHit(hits, lawId, lawName, article.articleId(), article.number(), path,
                    SearchHitSource.ARTICLE_NUMBER,
                    "article.number",
                    article.number(),
                    pattern);
            addHit(hits, lawId, lawName, article.articleId(), article.number(), path,
                    SearchHitSource.ARTICLE_BODY,
                    "article.body",
                    article.body(),
                    pattern);
        }
    }

    private static void addAnnotationHits(
            List<SearchHitResponse> hits,
            String lawId,
            String lawName,
            List<ArticleValue> articles,
            StructureContext structure,
            OverallDraftValues overall,
            Map<String, ArticleDraftValues> articleResults,
            Pattern pattern) {
        if (overall != null) {
            addHit(hits, lawId, lawName, null, null, List.of(),
                    SearchHitSource.OVERALL_ANNOTATION,
                    "overallAnnotation.lawCategory",
                    overall.lawCategory(),
                    pattern);
            addHit(hits, lawId, lawName, null, null, List.of(),
                    SearchHitSource.OVERALL_ANNOTATION,
                    "overallAnnotation.overallKeywords",
                    overall.overallKeywords(),
                    pattern);
            addHit(hits, lawId, lawName, null, null, List.of(),
                    SearchHitSource.OVERALL_ANNOTATION,
                    "overallAnnotation.summary",
                    overall.summary(),
                    pattern);
            addHit(hits, lawId, lawName, null, null, List.of(),
                    SearchHitSource.OVERALL_ANNOTATION,
                    "overallAnnotation.overallNote",
                    overall.overallNote(),
                    pattern);
        }
        Map<String, ArticleDraftValues> results = articleResults == null
                ? Map.of()
                : articleResults;
        for (ArticleValue article : articles) {
            ArticleDraftValues values = results.get(article.articleId());
            if (values == null) {
                continue;
            }
            List<String> path = structure.articlePath(article.articleId());
            addHit(hits, lawId, lawName, article.articleId(), article.number(), path,
                    SearchHitSource.ARTICLE_ANNOTATION,
                    "articleAnnotation.itemType",
                    itemTypeLabel(values.itemType()),
                    pattern);
            addHit(hits, lawId, lawName, article.articleId(), article.number(), path,
                    SearchHitSource.ARTICLE_ANNOTATION,
                    "articleAnnotation.keywords",
                    values.keywords(),
                    pattern);
            addHit(hits, lawId, lawName, article.articleId(), article.number(), path,
                    SearchHitSource.ARTICLE_ANNOTATION,
                    "articleAnnotation.subjects",
                    values.subjects(),
                    pattern);
            addHit(hits, lawId, lawName, article.articleId(), article.number(), path,
                    SearchHitSource.ARTICLE_ANNOTATION,
                    "articleAnnotation.legalLiability",
                    values.legalLiability(),
                    pattern);
            addHit(hits, lawId, lawName, article.articleId(), article.number(), path,
                    SearchHitSource.ARTICLE_ANNOTATION,
                    "articleAnnotation.annotationNote",
                    values.annotationNote(),
                    pattern);
        }
    }

    private static void addHit(
            List<SearchHitResponse> hits,
            String lawId,
            String lawName,
            String articleId,
            String articleNumber,
            List<String> structurePath,
            SearchHitSource source,
            String field,
            String text,
            Pattern pattern) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return;
        }
        MatchSnippet snippet = snippet(text, matcher.start(), matcher.end());
        hits.add(new SearchHitResponse(
                lawId,
                lawName,
                articleId,
                articleNumber,
                structurePath,
                source,
                field,
                snippet.text(),
                snippet.highlightStart(),
                snippet.highlightEnd()));
    }

    private static MatchSnippet snippet(String text, int matchStart, int matchEnd) {
        int contentStart = Math.max(0, matchStart - SNIPPET_BEFORE);
        int contentEnd = Math.min(text.length(), matchEnd + SNIPPET_AFTER);
        String prefix = contentStart > 0 ? "…" : "";
        String suffix = contentEnd < text.length() ? "…" : "";
        String snippet = prefix + text.substring(contentStart, contentEnd) + suffix;
        int highlightStart = prefix.length() + matchStart - contentStart;
        return new MatchSnippet(
                snippet,
                highlightStart,
                highlightStart + matchEnd - matchStart);
    }

    private static SearchQuery validate(
            String q,
            SearchScope scope,
            int page,
            int size) {
        if (q == null) {
            throw queryInvalid("搜索关键词不能为空");
        }
        String normalized = COMMON_TEXT_WHITESPACE.matcher(q).replaceAll(" ").strip();
        int length = normalized.codePointCount(0, normalized.length());
        boolean containsControl = normalized.codePoints().anyMatch(Character::isISOControl);
        if (length < 1 || length > MAX_QUERY_LENGTH || containsControl) {
            throw queryInvalid("搜索关键词须为1至100个字符且不得包含非法控制字符");
        }
        if (scope == null) {
            throw queryInvalid("搜索范围不能为空");
        }
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    SearchErrorCodes.PAGE_INVALID,
                    "搜索分页参数不合法",
                    List.of(new ErrorLocator(
                            "page/size",
                            "page不能小于0，size须为1至100")));
        }
        Pattern pattern = Pattern.compile(
                Pattern.quote(normalized),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return new SearchQuery(normalized, scope, pattern);
    }

    private static String validateIdentifier(String value) {
        if (value == null || value.isBlank()) {
            throw queryInvalid("taskId不能为空");
        }
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > 100
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw queryInvalid("taskId须为1至100个字符且不得包含控制字符");
        }
        return normalized;
    }

    private static PageResponse<SearchHitResponse> page(
            List<SearchHitResponse> hits,
            int page,
            int size) {
        long offset = (long) page * size;
        int from = offset >= hits.size() ? hits.size() : (int) offset;
        int to = Math.min(hits.size(), from + size);
        int totalPages = hits.isEmpty() ? 0 : (hits.size() + size - 1) / size;
        return new PageResponse<>(
                hits.subList(from, to),
                page,
                size,
                hits.size(),
                totalPages);
    }

    private static <T> Map<String, T> indexUnique(
            Collection<T> values,
            Function<T, String> idExtractor) {
        Map<String, T> indexed = new LinkedHashMap<>();
        for (T value : values) {
            String id = idExtractor.apply(value);
            if (indexed.put(id, value) != null) {
                throw dataInconsistent("批量读取结果包含重复标识");
            }
        }
        return Map.copyOf(indexed);
    }

    private static String itemTypeLabel(ItemType itemType) {
        if (itemType == null) {
            return null;
        }
        return switch (itemType) {
            case DEFINITION -> "定义解释类";
            case RIGHTS_DUTIES -> "权利义务类";
            case AUTHORITY_DUTY -> "授权职责类";
            case PROHIBITION_RESTRICTION -> "禁止限制类";
            case PROCEDURE -> "程序规则类";
            case LIABILITY -> "法律责任类";
            case OTHER -> "其他";
        };
    }

    private static ApiException queryInvalid(String message) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                SearchErrorCodes.QUERY_INVALID,
                "搜索关键词不合法",
                List.of(new ErrorLocator("q", message)));
    }

    private static ApiException dataInconsistent(String message) {
        return new ApiException(
                HttpStatus.CONFLICT,
                SearchErrorCodes.DATA_INCONSISTENT,
                message);
    }

    private record SearchQuery(String text, SearchScope scope, Pattern pattern) {
    }

    private record MatchSnippet(String text, int highlightStart, int highlightEnd) {
    }

    private record ArticleValue(String articleId, String number, String body, int order) {

        private static ArticleValue from(ArticleSnapshot article) {
            return new ArticleValue(
                    article.getArticleId(),
                    article.getNumber(),
                    article.getBody(),
                    article.getOrder());
        }

        private static ArticleValue from(TaskArticleSnapshot article) {
            return new ArticleValue(
                    article.articleId(),
                    article.number(),
                    article.body(),
                    article.order());
        }
    }

    private record StructureValue(
            String nodeId,
            String title,
            String parentNodeId,
            int order,
            List<String> articleIds) {

        private StructureValue {
            articleIds = List.copyOf(articleIds);
        }

        private static StructureValue from(LawStructureNode node) {
            return new StructureValue(
                    node.getNodeId(),
                    node.getTitle(),
                    node.getParentNodeId(),
                    node.getOrder(),
                    node.getArticleIds());
        }

        private static StructureValue from(TaskStructureNodeSnapshot node) {
            return new StructureValue(
                    node.nodeId(),
                    node.title(),
                    node.parentNodeId(),
                    node.order(),
                    node.articleIds());
        }
    }

    private static final class StructureContext {

        private final List<StructureValue> nodes;
        private final Map<String, StructureValue> nodesById;
        private final Map<String, List<StructureValue>> childrenByParentId;
        private final Map<String, List<String>> articlePaths;
        private final Map<String, Set<String>> descendantArticleIds = new HashMap<>();

        private StructureContext(
                List<StructureValue> nodes,
                List<ArticleValue> articles) {
            this.nodes = nodes.stream()
                    .sorted(Comparator.comparingInt(StructureValue::order)
                            .thenComparing(StructureValue::nodeId))
                    .toList();
            this.nodesById = indexNodes(this.nodes);
            this.childrenByParentId = children(this.nodes);
            this.articlePaths = buildArticlePaths(articles);
        }

        static StructureContext fromLaw(
                List<LawStructureNode> nodes,
                List<ArticleValue> articles) {
            return new StructureContext(
                    nodes.stream().map(StructureValue::from).toList(), articles);
        }

        static StructureContext fromTask(
                List<TaskStructureNodeSnapshot> nodes,
                List<ArticleValue> articles) {
            return new StructureContext(
                    nodes.stream().map(StructureValue::from).toList(), articles);
        }

        List<StructureValue> nodes() {
            return nodes;
        }

        List<String> articlePath(String articleId) {
            return articlePaths.getOrDefault(articleId, List.of());
        }

        List<String> nodePath(String nodeId) {
            StructureValue node = nodesById.get(nodeId);
            return node == null ? List.of() : pathForNode(node);
        }

        Set<String> descendantArticleIds(String nodeId) {
            return descendantArticleIds.computeIfAbsent(
                    nodeId,
                    ignored -> descendants(nodeId, new HashSet<>()));
        }

        private Set<String> descendants(String nodeId, Set<String> visiting) {
            if (!visiting.add(nodeId)) {
                throw dataInconsistent("法律结构存在循环引用");
            }
            StructureValue node = nodesById.get(nodeId);
            if (node == null) {
                throw dataInconsistent("法律结构节点引用无效");
            }
            Set<String> result = new LinkedHashSet<>(node.articleIds());
            for (StructureValue child : childrenByParentId.getOrDefault(nodeId, List.of())) {
                result.addAll(descendants(child.nodeId(), visiting));
            }
            visiting.remove(nodeId);
            return Set.copyOf(result);
        }

        private Map<String, List<String>> buildArticlePaths(List<ArticleValue> articles) {
            Set<String> currentArticleIds = articles.stream()
                    .map(ArticleValue::articleId)
                    .collect(Collectors.toSet());
            Map<String, List<String>> paths = new HashMap<>();
            for (StructureValue node : nodes) {
                List<String> path = pathForNode(node);
                for (String articleId : node.articleIds()) {
                    if (currentArticleIds.contains(articleId)
                            && paths.put(articleId, path) != null) {
                        throw dataInconsistent("同一法条被多个结构节点直接引用");
                    }
                }
            }
            return Map.copyOf(paths);
        }

        private List<String> pathForNode(StructureValue node) {
            List<String> reversed = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            StructureValue current = node;
            while (current != null) {
                if (!visited.add(current.nodeId())) {
                    throw dataInconsistent("法律结构存在循环引用");
                }
                reversed.add(current.title());
                String parentId = current.parentNodeId();
                if (parentId == null || parentId.isBlank()) {
                    break;
                }
                current = nodesById.get(parentId);
                if (current == null) {
                    throw dataInconsistent("法律结构父节点引用无效");
                }
            }
            java.util.Collections.reverse(reversed);
            return List.copyOf(reversed);
        }

        private static Map<String, StructureValue> indexNodes(List<StructureValue> nodes) {
            Map<String, StructureValue> indexed = new HashMap<>();
            for (StructureValue node : nodes) {
                if (indexed.put(node.nodeId(), node) != null) {
                    throw dataInconsistent("法律结构包含重复节点标识");
                }
            }
            return Map.copyOf(indexed);
        }

        private static Map<String, List<StructureValue>> children(
                List<StructureValue> nodes) {
            Map<String, List<StructureValue>> children = new HashMap<>();
            for (StructureValue node : nodes) {
                if (node.parentNodeId() != null && !node.parentNodeId().isBlank()) {
                    children.computeIfAbsent(node.parentNodeId(), ignored -> new ArrayList<>())
                            .add(node);
                }
            }
            Map<String, List<StructureValue>> result = new HashMap<>();
            children.forEach((parent, values) -> result.put(
                    parent,
                    values.stream()
                            .sorted(Comparator.comparingInt(StructureValue::order)
                                    .thenComparing(StructureValue::nodeId))
                            .toList()));
            return Map.copyOf(result);
        }
    }
}
