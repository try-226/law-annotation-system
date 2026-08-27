package com.law.annotation.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.law.annotation.annotation.ArticleDraftValues;
import com.law.annotation.annotation.OverallDraftValues;
import com.law.annotation.annotation.TaskDraftDocument;
import com.law.annotation.annotation.TaskDraftRepository;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.ItemType;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.law.ArticleSnapshot;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawDomainRules;
import com.law.annotation.law.LawStructureNode;
import com.law.annotation.law.LawStructureNodeType;
import com.law.annotation.law.PendingChangeSet;
import com.law.annotation.revision.RevisionMode;
import com.law.annotation.revision.RevisionScope;
import com.law.annotation.search.dto.SearchHitResponse;
import com.law.annotation.task.TaskArticleSnapshot;
import com.law.annotation.task.TaskContentVersionSnapshot;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskErrorCodes;
import com.law.annotation.task.TaskLawBaseInfoSnapshot;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.TaskStructureNodeSnapshot;
import com.law.annotation.user.UserDocument;
import com.law.annotation.version.AnnotationVersionDocument;
import com.law.annotation.version.AnnotationVersionRepository;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");

    private SearchRepository searchRepository;
    private ContentVersionRepository contentVersionRepository;
    private AnnotationVersionRepository annotationVersionRepository;
    private TaskRepository taskRepository;
    private TaskDraftRepository taskDraftRepository;
    private SearchService service;

    @BeforeEach
    void setUp() {
        searchRepository = org.mockito.Mockito.mock(SearchRepository.class);
        contentVersionRepository = org.mockito.Mockito.mock(ContentVersionRepository.class);
        annotationVersionRepository = org.mockito.Mockito.mock(AnnotationVersionRepository.class);
        taskRepository = org.mockito.Mockito.mock(TaskRepository.class);
        taskDraftRepository = org.mockito.Mockito.mock(TaskDraftRepository.class);
        service = new SearchService(
                searchRepository,
                contentVersionRepository,
                annotationVersionRepository,
                taskRepository,
                taskDraftRepository);
    }

    @Test
    void adminSearchesEveryRequiredCurrentLawAndFormalField() {
        givenCurrentFormalLaw();

        assertSingleSource("当前法律名称", SearchHitSource.LAW_NAME, "law.name");
        assertSingleSource("当前制定机关", SearchHitSource.ISSUING_AUTHORITY,
                "law.issuingAuthority");
        assertSingleSource("第一章", SearchHitSource.STRUCTURE_TITLE, "structure.title");
        assertSingleSource("第一条", SearchHitSource.ARTICLE_NUMBER, "article.number");
        assertSingleSource("受教育的权利", SearchHitSource.ARTICLE_BODY, "article.body");
        assertSingleSource("行政法", SearchHitSource.OVERALL_ANNOTATION,
                "overallAnnotation.lawCategory");
        assertSingleSource("正式摘要", SearchHitSource.OVERALL_ANNOTATION,
                "overallAnnotation.summary");
        assertSingleSource("权利义务类", SearchHitSource.ARTICLE_ANNOTATION,
                "articleAnnotation.itemType");
        assertSingleSource("正式法条备注", SearchHitSource.ARTICLE_ANNOTATION,
                "articleAnnotation.annotationNote");
    }

    @Test
    void adminSearchUsesOnlyCurrentAnnotationAndLiteralSafeQuery() {
        givenCurrentFormalLaw();

        for (String literalQuery : List.of(".", "*", "+", "?", "[", "]", "(", ")", "\\")) {
            PageResponse<SearchHitResponse> literal = service.searchLaws(
                    "  " + literalQuery + "  ", SearchScope.ALL, 0, 10);
            assertThat(literal.items()).singleElement()
                    .satisfies(hit -> {
                        assertThat(hit.hitField()).isEqualTo("article.body");
                        assertThat(hit.snippet().substring(
                                hit.highlightStart(), hit.highlightEnd()))
                                .isEqualTo(literalQuery);
                    });
        }
        assertThat(service.searchLaws("历史秘密", SearchScope.ALL, 0, 10).items()).isEmpty();
        verify(annotationVersionRepository, times(10))
                .findByIdIn(List.of("annotation-current"));
    }

    @Test
    void adminAnnotationSearchIncludesMatchingCurrentAnnotation() {
        givenAdminSearchState(
                law("content-1", "annotation-1", false, "法律名称", "第一章"),
                content("content-1", "C1当前正文"),
                annotation("annotation-1", "content-1", "A1匹配标注", "A1法条标注", 1));

        assertThat(service.searchLaws(
                "A1匹配标注", SearchScope.ANNOTATION, 0, 10).items())
                .singleElement()
                .satisfies(hit -> assertThat(hit.hitField())
                        .isEqualTo("overallAnnotation.summary"));
    }

    @Test
    void adminSearchSkipsOldAnnotationAfterSemanticChangeWithoutBlockingCurrentText() {
        givenAdminSearchState(
                law("content-2", "annotation-1", true, "法律名称", "第一章"),
                content("content-2", "C2最新语义正文"),
                annotation("annotation-1", "content-1", "A1旧正式标注", "A1旧法条标注", 1));

        assertThat(service.searchLaws(
                "C2最新语义正文", SearchScope.LAW_TEXT, 0, 10).items())
                .singleElement()
                .satisfies(hit -> assertThat(hit.hitField()).isEqualTo("article.body"));
        assertThat(service.searchLaws(
                "A1旧正式标注", SearchScope.ANNOTATION, 0, 10).items()).isEmpty();
        assertThat(service.searchLaws(
                "C2最新语义正文", SearchScope.ALL, 0, 10).items())
                .singleElement()
                .satisfies(hit -> assertThat(hit.hitField()).isEqualTo("article.body"));
        assertThat(service.searchLaws(
                "A1旧正式标注", SearchScope.ALL, 0, 10).items()).isEmpty();
    }

    @Test
    void adminAnnotationSearchSurvivesMetadataAndStructureOnlyChanges() {
        givenAdminSearchState(
                law("content-1", "annotation-1", false, "更新后的法律名称", "更新后的第一章"),
                content("content-1", "C1正文未变化"),
                annotation("annotation-1", "content-1", "仍有效正式标注", "仍有效法条标注", 1));

        assertThat(service.searchLaws(
                "仍有效正式标注", SearchScope.ANNOTATION, 0, 10).items())
                .singleElement()
                .satisfies(hit -> assertThat(hit.hitField())
                        .isEqualTo("overallAnnotation.summary"));
    }

    @Test
    void adminAnnotationSearchUsesNewMatchingAnnotationAfterRevisionApproval() {
        givenAdminSearchState(
                law("content-2", "annotation-2", false, "法律名称", "第一章"),
                content("content-2", "C2当前正文"),
                annotation("annotation-2", "content-2", "A2新正式标注", "A2新法条标注", 2));

        assertThat(service.searchLaws(
                "A2新正式标注", SearchScope.ANNOTATION, 0, 10).items())
                .singleElement()
                .satisfies(hit -> assertThat(hit.hitField())
                        .isEqualTo("overallAnnotation.summary"));
        assertThat(service.searchLaws(
                "A1旧正式标注", SearchScope.ANNOTATION, 0, 10).items()).isEmpty();
        verify(annotationVersionRepository, times(2))
                .findByIdIn(List.of("annotation-2"));
    }

    @Test
    void adminSearchScopesPaginationAndHighlightAreStable() {
        givenCurrentFormalLaw();

        assertThat(service.searchLaws("行政", SearchScope.LAW_TEXT, 0, 10).items())
                .allMatch(hit -> hit.hitSource() != SearchHitSource.OVERALL_ANNOTATION
                        && hit.hitSource() != SearchHitSource.ARTICLE_ANNOTATION);
        assertThat(service.searchLaws("正文", SearchScope.ANNOTATION, 0, 10).items()).isEmpty();

        PageResponse<SearchHitResponse> first = service.searchLaws(
                "当前", SearchScope.LAW_TEXT, 0, 1);
        PageResponse<SearchHitResponse> second = service.searchLaws(
                "当前", SearchScope.LAW_TEXT, 1, 1);
        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.items()).singleElement()
                .extracting(SearchHitResponse::hitField).isEqualTo("law.name");
        assertThat(second.items()).singleElement()
                .extracting(SearchHitResponse::hitField).isEqualTo("law.issuingAuthority");
    }

    @Test
    void adminSearchNormalizesCommonCopiedTextWhitespaceBeforeMatching() {
        givenAdminSearchState(
                law("content-1", "annotation-1", false, "法律名称", "第一章"),
                content("content-1", "行政机关 应当 依法 处理"),
                annotation("annotation-1", "content-1", "正式标注", "法条标注", 1));

        List<String> queries = List.of(
                "行政机关 应当 依法 处理",
                "  行政机关 应当 依法 处理  ",
                "行政机关   应当 依法 处理",
                "行政机关\n应当 依法 处理",
                "行政机关\r\n应当 依法 处理",
                "行政机关\r应当 依法 处理",
                "行政机关\t应当 依法 处理",
                " 行政机关\r\n应当  依法\t处理 ",
                "行政机关" + " ".repeat(200) + "应当 依法 处理");
        for (String query : queries) {
            assertThat(service.searchLaws(
                    query, SearchScope.LAW_TEXT, 0, 10).items())
                    .singleElement()
                    .satisfies(hit -> assertThat(hit.snippet().substring(
                            hit.highlightStart(), hit.highlightEnd()))
                            .isEqualTo("行政机关 应当 依法 处理"));
        }
    }

    @Test
    void invalidQueryAndPageUseStableSearchErrors() {
        assertSearchError(
                () -> service.searchLaws("   ", SearchScope.ALL, 0, 10),
                SearchErrorCodes.QUERY_INVALID);
        assertSearchError(
                () -> service.searchLaws("x".repeat(101), SearchScope.ALL, 0, 10),
                SearchErrorCodes.QUERY_INVALID);
        assertSearchError(
                () -> service.searchLaws("行政\u0000机关", SearchScope.ALL, 0, 10),
                SearchErrorCodes.QUERY_INVALID);
        assertSearchError(
                () -> service.searchLaws("有效", SearchScope.ALL, -1, 10),
                SearchErrorCodes.PAGE_INVALID);
        assertSearchError(
                () -> service.searchLaws("有效", SearchScope.ALL, 0, 101),
                SearchErrorCodes.PAGE_INVALID);
        verify(searchRepository, never()).findVisibleLawsMatching(
                any(Pattern.class), any(SearchScope.class));
    }

    @Test
    void threeHundredArticleLawIsSearchedAndPagedWithoutPerArticleQueries() {
        List<ArticleSnapshot> articles = IntStream.range(0, 300)
                .mapToObj(index -> new ArticleSnapshot(
                        "article-" + index,
                        "第" + (index + 1) + "条",
                        "规模测试正文" + index,
                        index))
                .toList();
        LawDocument law = new LawDocument(
                "law-large",
                "较大法律",
                LawDomainRules.normalizeLawName("较大法律"),
                "制定机关",
                LocalDate.of(2026, 8, 19),
                ValidityStatus.ACTIVE,
                List.of(),
                null,
                "content-large",
                null,
                false,
                PendingChangeSet.empty(),
                NOW,
                NOW);
        when(searchRepository.findVisibleLawsMatching(
                any(Pattern.class), any(SearchScope.class))).thenReturn(List.of(law));
        when(contentVersionRepository.findByIdIn(List.of("content-large")))
                .thenReturn(List.of(new ContentVersionDocument(
                        "content-large", "law-large", 1, articles, "admin-1", NOW)));

        PageResponse<SearchHitResponse> result = service.searchLaws(
                "规模测试", SearchScope.LAW_TEXT, 2, 100);

        assertThat(result.items()).hasSize(100);
        assertThat(result.totalElements()).isEqualTo(300);
        assertThat(result.totalPages()).isEqualTo(3);
        verify(contentVersionRepository).findByIdIn(List.of("content-large"));
        verify(annotationVersionRepository, never()).findByIdIn(List.of());
    }

    @Test
    void annotatorSearchUsesBoundSnapshotAndOnlyPersistedDraft() {
        TaskDocument task = task(TaskType.ORDINARY, null);
        TaskDraftDocument draft = draft();
        UserPrincipal owner = principal("annotator-1", Role.ANNOTATOR);
        when(taskRepository.findByTaskIdAndAnnotatorId("task-1", "annotator-1"))
                .thenReturn(Optional.of(task));
        when(taskDraftRepository.findById("task-1")).thenReturn(Optional.of(draft));

        assertTaskHit(owner, "任务绑定旧正文", "article.body");
        assertTaskHit(owner, "已保存整体草稿", "overallAnnotation.summary");
        assertTaskHit(owner, "已保存法条草稿", "articleAnnotation.annotationNote");
        assertThat(service.searchTask(
                "task-1", "未保存浏览器输入", SearchScope.ALL, 0, 10, owner).items())
                .isEmpty();
        verify(searchRepository, never()).findVisibleLawsMatching(
                any(Pattern.class), any(SearchScope.class));
        verify(contentVersionRepository, never()).findById("content-current");
    }

    @Test
    void taskSearchEnforcesOwnerAndDoesNotMergeRevisionBaseAnnotation() {
        UserPrincipal owner = principal("annotator-1", Role.ANNOTATOR);
        UserPrincipal other = principal("annotator-2", Role.ANNOTATOR);
        UserPrincipal admin = principal("admin-1", Role.ADMIN);
        TaskDocument revision = task(
                TaskType.REVISION,
                new RevisionScope(
                        RevisionMode.ANNOTATION_ONLY,
                        false,
                        List.of("article-1"),
                        List.of()));
        when(taskRepository.findByTaskIdAndAnnotatorId("task-1", "annotator-1"))
                .thenReturn(Optional.of(revision));
        when(taskRepository.findByTaskIdAndAnnotatorId("task-1", "annotator-2"))
                .thenReturn(Optional.empty());
        when(taskDraftRepository.findById("task-1")).thenReturn(Optional.empty());

        assertThat(service.searchTask(
                "task-1", "旧正式结果", SearchScope.ANNOTATION, 0, 10, owner).items())
                .isEmpty();
        assertSearchError(
                () -> service.searchTask(
                        "task-1", "正文", SearchScope.ALL, 0, 10, other),
                "TASK.NOT_FOUND");
        assertSearchError(
                () -> service.searchTask(
                        "task-1", "正文", SearchScope.ALL, 0, 10, admin),
                "AUTH.FORBIDDEN");
    }

    @Test
    void taskSearchDoesNotExposeCanceledOrApprovedTaskHistory() {
        UserPrincipal owner = principal("annotator-1", Role.ANNOTATOR);
        for (TaskState state : List.of(TaskState.CANCELED, TaskState.APPROVED)) {
            when(taskRepository.findByTaskIdAndAnnotatorId("task-1", "annotator-1"))
                    .thenReturn(Optional.of(task(TaskType.ORDINARY, null, state)));

            assertSearchError(
                    () -> service.searchTask(
                            "task-1", "旧草稿", SearchScope.ALL, 0, 10, owner),
                    TaskErrorCodes.NOT_FOUND);
        }
        verify(taskDraftRepository, never()).findById("task-1");
    }

    private void assertSingleSource(String q, SearchHitSource source, String field) {
        assertThat(service.searchLaws(q, SearchScope.ALL, 0, 10).items())
                .anySatisfy(hit -> {
                    assertThat(hit.hitSource()).isEqualTo(source);
                    assertThat(hit.hitField()).isEqualTo(field);
                    assertThat(hit.snippet().substring(
                            hit.highlightStart(), hit.highlightEnd()))
                            .isEqualTo(q);
                });
    }

    private void assertTaskHit(UserPrincipal owner, String q, String field) {
        assertThat(service.searchTask(
                "task-1", q, SearchScope.ALL, 0, 10, owner).items())
                .anySatisfy(hit -> assertThat(hit.hitField()).isEqualTo(field));
    }

    private void givenCurrentFormalLaw() {
        LawDocument law = law();
        ContentVersionDocument content = new ContentVersionDocument(
                "content-current",
                "law-1",
                2,
                List.of(new ArticleSnapshot(
                        "article-1",
                        "第一条",
                        "中华人民共和国公民有受教育的权利和义务.正文*+?[]()\\",
                        0)),
                "admin-1",
                NOW);
        AnnotationVersionDocument annotation = annotation(
                "annotation-current",
                "正式摘要",
                "正式法条备注");
        when(searchRepository.findVisibleLawsMatching(
                any(Pattern.class), any(SearchScope.class))).thenReturn(List.of(law));
        when(contentVersionRepository.findByIdIn(List.of("content-current")))
                .thenReturn(List.of(content));
        when(annotationVersionRepository.findByIdIn(List.of("annotation-current")))
                .thenReturn(List.of(annotation));
    }

    private void givenAdminSearchState(
            LawDocument law,
            ContentVersionDocument content,
            AnnotationVersionDocument annotation) {
        when(searchRepository.findVisibleLawsMatching(
                any(Pattern.class), any(SearchScope.class))).thenReturn(List.of(law));
        when(contentVersionRepository.findByIdIn(List.of(law.getCurrentContentVersionId())))
                .thenReturn(List.of(content));
        when(annotationVersionRepository.findByIdIn(
                List.of(law.getCurrentAnnotationVersionId())))
                .thenReturn(List.of(annotation));
    }

    private static LawDocument law() {
        return law(
                "content-current",
                "annotation-current",
                false,
                "当前法律名称",
                "第一章 行政管理");
    }

    private static LawDocument law(
            String contentVersionId,
            String annotationVersionId,
            boolean pendingRevision,
            String name,
            String structureTitle) {
        return new LawDocument(
                "law-1",
                name,
                LawDomainRules.normalizeLawName(name),
                "当前制定机关",
                LocalDate.of(2026, 8, 19),
                ValidityStatus.ACTIVE,
                List.of(new LawStructureNode(
                        "chapter-1",
                        LawStructureNodeType.CHAPTER,
                        structureTitle,
                        null,
                        0,
                        List.of("article-1"))),
                null,
                contentVersionId,
                annotationVersionId,
                pendingRevision,
                pendingRevision
                        ? PendingChangeSet.empty().recordModification("article-1")
                        : PendingChangeSet.empty(),
                NOW,
                NOW);
    }

    private static ContentVersionDocument content(String id, String body) {
        return new ContentVersionDocument(
                id,
                "law-1",
                id.equals("content-1") ? 1 : 2,
                List.of(new ArticleSnapshot("article-1", "第一条", body, 0)),
                "admin-1",
                NOW);
    }

    private static AnnotationVersionDocument annotation(
            String id,
            String summary,
            String articleNote) {
        return annotation(id, "content-current", summary, articleNote, 2);
    }

    private static AnnotationVersionDocument annotation(
            String id,
            String contentVersionId,
            String summary,
            String articleNote,
            int seq) {
        return new AnnotationVersionDocument(
                id,
                "law-1",
                seq,
                contentVersionId,
                new OverallDraftValues("行政法", "许可,行政", summary, "正式整体备注"),
                Map.of("article-1", new ArticleDraftValues(
                        ItemType.RIGHTS_DUTIES,
                        "教育,权利",
                        "公民",
                        "依法承担责任",
                        articleNote)),
                "task-approved",
                "submission-approved",
                "reviewer-1",
                NOW);
    }

    private static TaskDocument task(TaskType type, RevisionScope scope) {
        return task(type, scope, TaskState.ANNOTATING);
    }

    private static TaskDocument task(
            TaskType type,
            RevisionScope scope,
            TaskState state) {
        return new TaskDocument(
                "task-1",
                type,
                state,
                "law-1",
                "annotator-1",
                "标注员一",
                "任务一",
                null,
                "content-bound",
                new TaskContentVersionSnapshot(
                        "content-bound",
                        1,
                        List.of(new TaskArticleSnapshot(
                                "article-1", "第一条", "任务绑定旧正文", 0))),
                new TaskLawBaseInfoSnapshot(
                        "任务法律名称",
                        "任务制定机关",
                        LocalDate.of(2026, 8, 19),
                        ValidityStatus.ACTIVE),
                List.of(new TaskStructureNodeSnapshot(
                        "chapter-1",
                        LawStructureNodeType.CHAPTER,
                        "任务第一章",
                        null,
                        0,
                        List.of("article-1"))),
                null,
                type == TaskType.REVISION ? "annotation-base" : null,
                scope,
                "admin-1",
                null,
                null,
                null,
                null,
                NOW,
                NOW);
    }

    private static TaskDraftDocument draft() {
        Map<String, ArticleDraftValues> articleDrafts = new LinkedHashMap<>();
        articleDrafts.put("article-1", new ArticleDraftValues(
                ItemType.PROCEDURE,
                "草稿关键词",
                "草稿主体",
                "草稿责任",
                "已保存法条草稿"));
        return new TaskDraftDocument(
                "task-1",
                new OverallDraftValues(
                        "草稿类别", "草稿整体关键词", "已保存整体草稿", "草稿整体备注"),
                articleDrafts,
                3,
                "annotator-1",
                NOW,
                NOW);
    }

    private static UserPrincipal principal(String id, Role role) {
        UserDocument user = new UserDocument(
                "测试用户", id, id, "$2a$12$hash", role, true, NOW, NOW);
        user.setId(id);
        return UserPrincipal.from(user);
    }

    private static void assertSearchError(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
