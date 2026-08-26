package com.law.annotation.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.law.annotation.annotation.ArticleDraftValues;
import com.law.annotation.annotation.OverallDraftValues;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.ItemType;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.law.ArticleSnapshot;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawRepository;
import com.law.annotation.law.PendingChangeSet;
import com.law.annotation.revision.dto.CreateRevisionTaskRequest;
import com.law.annotation.task.TaskService;
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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RevisionServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");

    private LawRepository lawRepository;
    private ContentVersionRepository contentVersionRepository;
    private AnnotationVersionRepository annotationVersionRepository;
    private TaskService taskService;
    private RevisionService service;

    @BeforeEach
    void setUp() {
        lawRepository = mock(LawRepository.class);
        contentVersionRepository = mock(ContentVersionRepository.class);
        annotationVersionRepository = mock(AnnotationVersionRepository.class);
        taskService = mock(TaskService.class);
        service = new RevisionService(
                lawRepository,
                contentVersionRepository,
                annotationVersionRepository,
                taskService);
    }

    @Test
    void contentChangeScopeIsServerOwnedAndExcludesDeletedArticles() {
        stubContentChangeLaw();
        CreateRevisionTaskRequest request = request(false, List.of());

        service.create(request, principal("admin-1", Role.ADMIN));

        ArgumentCaptor<RevisionScope> scope = ArgumentCaptor.forClass(RevisionScope.class);
        verify(taskService).createRevisionTask(
                anyString(), anyString(), any(), any(), anyString(),
                anyString(), anyString(), scope.capture());
        assertThat(scope.getValue().mode()).isEqualTo(RevisionMode.CONTENT_CHANGE);
        assertThat(scope.getValue().articleIds())
                .containsExactly("article-1", "article-added")
                .doesNotContain("article-deleted");
        assertThat(scope.getValue().mandatoryArticleIds())
                .containsExactlyElementsOf(scope.getValue().articleIds());
    }

    @Test
    void contentChangeRejectsDeletedAndUnchangedRequestedArticles() {
        stubContentChangeLaw();

        assertCode(
                () -> service.create(
                        request(false, List.of("article-deleted")),
                        principal("admin-1", Role.ADMIN)),
                RevisionErrorCodes.DELETED_ARTICLE_REQUESTED);
        assertCode(
                () -> service.create(
                        request(false, List.of("article-unchanged")),
                        principal("admin-1", Role.ADMIN)),
                RevisionErrorCodes.CONTENT_CHANGE_SCOPE_INVALID);
    }

    @Test
    void annotationOnlyRequiresNonEmptyValidScope() {
        stubAnnotationOnlyLaw();

        assertCode(
                () -> service.create(
                        request(false, List.of()),
                        principal("admin-1", Role.ADMIN)),
                RevisionErrorCodes.SCOPE_EMPTY);

        service.create(
                request(false, List.of("article-1")),
                principal("admin-1", Role.ADMIN));
        ArgumentCaptor<RevisionScope> scope = ArgumentCaptor.forClass(RevisionScope.class);
        verify(taskService).createRevisionTask(
                anyString(), anyString(), any(), any(), anyString(),
                anyString(), anyString(), scope.capture());
        assertThat(scope.getValue().mode()).isEqualTo(RevisionMode.ANNOTATION_ONLY);
        assertThat(scope.getValue().articleIds()).containsExactly("article-1");
        assertThat(scope.getValue().mandatoryArticleIds()).isEmpty();
    }

    @Test
    void inconsistentPendingStateAndInvalidBaseAreRejected() {
        LawDocument inconsistent = law(true, PendingChangeSet.empty(), "content-2");
        stubDocuments(inconsistent, baseContent(), latestContent(), baseAnnotation());
        assertCode(
                () -> service.create(
                        request(false, List.of()),
                        principal("admin-1", Role.ADMIN)),
                RevisionErrorCodes.CONTENT_CHANGE_SCOPE_INVALID);

        AnnotationVersionDocument invalid = new AnnotationVersionDocument(
                "annotation-1", "other-law", 1, "content-1",
                overall(), baseResults(), "old-task", "old-submission", "admin-1", NOW);
        when(annotationVersionRepository.findById("annotation-1"))
                .thenReturn(Optional.of(invalid));
        assertCode(
                () -> service.create(
                        request(true, List.of()),
                        principal("admin-1", Role.ADMIN)),
                RevisionErrorCodes.BASE_ANNOTATION_INVALID);
    }

    @Test
    void onlyAdminCanCreateRevisionTask() {
        assertCode(
                () -> service.create(
                        request(true, List.of()),
                        principal("annotator-1", Role.ANNOTATOR)),
                "AUTH.FORBIDDEN");
    }

    @Test
    void nullArticleIdentifierReturnsStableValidationError() {
        stubAnnotationOnlyLaw();
        assertCode(
                () -> service.create(
                        request(false, java.util.Arrays.asList((String) null)),
                        principal("admin-1", Role.ADMIN)),
                "COMMON.VALIDATION_FAILED");
    }

    private void stubContentChangeLaw() {
        PendingChangeSet changes = new PendingChangeSet(
                Set.of("article-added"),
                Set.of("article-1"),
                Set.of("article-deleted"));
        stubDocuments(law(true, changes, "content-2"),
                baseContent(), latestContent(), baseAnnotation());
    }

    private void stubAnnotationOnlyLaw() {
        ContentVersionDocument current = new ContentVersionDocument(
                "content-1", "law-1", 1,
                List.of(
                        new ArticleSnapshot("article-1", "第一条", "旧正文", 0),
                        new ArticleSnapshot("article-deleted", "第二条", "待删除", 1),
                        new ArticleSnapshot("article-unchanged", "第三条", "未变化", 2)),
                "admin-1", NOW);
        stubDocuments(law(false, PendingChangeSet.empty(), "content-1"),
                current, current, baseAnnotation());
    }

    private void stubDocuments(
            LawDocument law,
            ContentVersionDocument base,
            ContentVersionDocument latest,
            AnnotationVersionDocument annotation) {
        when(lawRepository.findById("law-1")).thenReturn(Optional.of(law));
        when(annotationVersionRepository.findById("annotation-1"))
                .thenReturn(Optional.of(annotation));
        when(contentVersionRepository.findById("content-1"))
                .thenReturn(Optional.of(base));
        when(contentVersionRepository.findById(latest.getId()))
                .thenReturn(Optional.of(latest));
    }

    private static LawDocument law(
            boolean pendingRevision,
            PendingChangeSet pendingChangeSet,
            String currentContentVersionId) {
        return new LawDocument(
                "law-1", "测试法", "测试法", "制定机关",
                LocalDate.of(2026, 8, 26), ValidityStatus.ACTIVE, List.of(), null,
                currentContentVersionId, "annotation-1", pendingRevision,
                pendingChangeSet, NOW, NOW);
    }

    private static ContentVersionDocument baseContent() {
        return new ContentVersionDocument(
                "content-1", "law-1", 1,
                List.of(
                        new ArticleSnapshot("article-1", "第一条", "旧正文", 0),
                        new ArticleSnapshot("article-deleted", "第二条", "待删除", 1),
                        new ArticleSnapshot("article-unchanged", "第三条", "未变化", 2)),
                "admin-1", NOW);
    }

    private static ContentVersionDocument latestContent() {
        return new ContentVersionDocument(
                "content-2", "law-1", 2,
                List.of(
                        new ArticleSnapshot("article-1", "第一条", "新正文", 0),
                        new ArticleSnapshot("article-added", "第二条", "新增", 1),
                        new ArticleSnapshot("article-unchanged", "第三条", "未变化", 2)),
                "admin-1", NOW.plusSeconds(1));
    }

    private static AnnotationVersionDocument baseAnnotation() {
        return new AnnotationVersionDocument(
                "annotation-1", "law-1", 1, "content-1",
                overall(), baseResults(), "old-task", "old-submission", "admin-1", NOW);
    }

    private static Map<String, ArticleDraftValues> baseResults() {
        Map<String, ArticleDraftValues> values = new LinkedHashMap<>();
        values.put("article-1", article("旧一"));
        values.put("article-deleted", article("旧二"));
        values.put("article-unchanged", article("旧三"));
        return values;
    }

    private static OverallDraftValues overall() {
        return new OverallDraftValues("民事", "基础", null, null);
    }

    private static ArticleDraftValues article(String keyword) {
        return new ArticleDraftValues(ItemType.DEFINITION, keyword, null, null, null);
    }

    private static CreateRevisionTaskRequest request(boolean overall, List<String> articleIds) {
        CreateRevisionTaskRequest request = new CreateRevisionTaskRequest();
        request.setLawId("law-1");
        request.setAnnotatorId("annotator-1");
        request.setOverall(overall);
        request.setArticleIds(articleIds);
        return request;
    }

    private static UserPrincipal principal(String id, Role role) {
        UserDocument user = new UserDocument(
                id, id, id, "$2a$12$hash", role, true, NOW, NOW);
        user.setId(id);
        return UserPrincipal.from(user);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
