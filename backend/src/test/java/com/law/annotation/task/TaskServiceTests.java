package com.law.annotation.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.field.FieldConfigService;
import com.law.annotation.field.FieldConfigSnapshot;
import com.law.annotation.field.FieldConfigSnapshotItem;
import com.law.annotation.law.ArticleSnapshot;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawRepository;
import com.law.annotation.law.LawStructureNode;
import com.law.annotation.law.LawStructureNodeType;
import com.law.annotation.law.PendingChangeSet;
import com.law.annotation.task.dto.TaskDetailResponse;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class TaskServiceTests {

    private final TaskRepository taskRepository = org.mockito.Mockito.mock(TaskRepository.class);
    private final LawRepository lawRepository = org.mockito.Mockito.mock(LawRepository.class);
    private final ContentVersionRepository contentVersionRepository =
            org.mockito.Mockito.mock(ContentVersionRepository.class);
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final FieldConfigService fieldConfigService = org.mockito.Mockito.mock(FieldConfigService.class);
    private final MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(
                taskRepository,
                lawRepository,
                contentVersionRepository,
                userRepository,
                fieldConfigService,
                mongoTemplate);
    }

    @Test
    void createsOrdinaryWholeLawTaskWithFrozenSnapshots() {
        stubEligibleCreation();
        when(taskRepository.insert(any(TaskDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TaskDetailResponse response = service.createOrdinaryTask(
                "law-1", "annotator-1", "  首次标注  ", "  备注  ", "admin-1");

        assertThat(response.taskType()).isEqualTo(TaskType.ORDINARY);
        assertThat(response.taskState()).isEqualTo(TaskState.PENDING_ANNOTATION);
        assertThat(response.contentVersionId()).isEqualTo("content-1");
        assertThat(response.taskName()).isEqualTo("首次标注");
        assertThat(response.remark()).isEqualTo("备注");
        assertThat(response.lawBaseInfoSnapshot().name()).isEqualTo("测试法");
        assertThat(response.contentVersionSnapshot().articles())
                .extracting(TaskArticleSnapshot::body)
                .containsExactly("正文");
        assertThat(response.structureSnapshot()).hasSize(1);
        assertThat(response.fieldConfigSnapshot().overall())
                .extracting(FieldConfigSnapshotItem::fieldKey)
                .containsExactly("lawCategory");

        ArgumentCaptor<TaskDocument> captor = ArgumentCaptor.forClass(TaskDocument.class);
        verify(taskRepository).insert(captor.capture());
        assertThat(TaskStateRules.UNFINISHED_STATES)
                .contains(captor.getValue().getTaskState());
        assertThat(captor.getValue().getAnnotatorNameSnapshot()).isEqualTo("标注员甲");
    }

    @Test
    void activeTaskBlocksCreationBeforeInsert() {
        LawDocument law = law(null, false, null);
        when(lawRepository.findById("law-1")).thenReturn(Optional.of(law));
        when(taskRepository.existsByLawIdAndTaskStateIn(
                "law-1", TaskStateRules.UNFINISHED_STATES)).thenReturn(true);

        assertCode(
                () -> service.createOrdinaryTask(
                        "law-1", "annotator-1", null, null, "admin-1"),
                TaskErrorCodes.TASK_ALREADY_EXISTS);
        verify(taskRepository, never()).insert(any(TaskDocument.class));
    }

    @Test
    void duplicateKeyDuringConcurrentCreationBecomesTaskBusinessError() {
        stubEligibleCreation();
        when(taskRepository.insert(any(TaskDocument.class)))
                .thenThrow(new DuplicateKeyException("duplicate lawId"));

        assertCode(
                () -> service.createOrdinaryTask(
                        "law-1", "annotator-1", null, null, "admin-1"),
                TaskErrorCodes.TASK_ALREADY_EXISTS);
    }

    @Test
    void blankTaskNamesAreGeneratedFromLawName() {
        stubEligibleCreation();
        when(taskRepository.insert(any(TaskDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(Arrays.asList(null, "", "   ").stream()
                        .map(taskName -> service.createOrdinaryTask(
                                "law-1", "annotator-1", taskName, null, "admin-1")
                                .taskName()))
                .containsOnly("测试法普通标注任务")
                .allSatisfy(taskName -> assertThat(taskName).isNotBlank());
    }

    @Test
    void customTaskNameIsTrimmedAndLimitedToOneHundredCharacters() {
        stubEligibleCreation();
        when(taskRepository.insert(any(TaskDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.createOrdinaryTask(
                        "law-1", "annotator-1", "  自定义任务  ", null, "admin-1")
                        .taskName())
                .isEqualTo("自定义任务");
        assertCode(
                () -> service.createOrdinaryTask(
                        "law-1", "annotator-1", "名".repeat(101), null, "admin-1"),
                "COMMON.VALIDATION_FAILED");
    }

    @Test
    void generatedTaskNameKeepsReadableSuffixWithinOneHundredCharacters() {
        stubEligibleCreation("法".repeat(100));
        when(taskRepository.insert(any(TaskDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String generated = service.createOrdinaryTask(
                        "law-1", "annotator-1", null, null, "admin-1")
                .taskName();

        assertThat(generated).endsWith("普通标注任务");
        assertThat(generated.codePointCount(0, generated.length())).isEqualTo(100);
    }

    @Test
    void adminListKeepsRequestedAnnotatorFilter() {
        UserPrincipal admin = UserPrincipal.from(user("admin-1", Role.ADMIN, true));
        when(mongoTemplate.count(any(Query.class),
                org.mockito.ArgumentMatchers.eq(TaskDocument.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class),
                org.mockito.ArgumentMatchers.eq(TaskDocument.class))).thenReturn(List.of());
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);

        service.list(null, null, null, "annotator-2", null, 0, 10, admin);

        verify(mongoTemplate).count(
                queryCaptor.capture(), org.mockito.ArgumentMatchers.eq(TaskDocument.class));
        assertThat(queryCaptor.getValue().getQueryObject().getString("annotatorId"))
                .isEqualTo("annotator-2");
    }

    @Test
    void annotatorListOverridesRequestedAnnotatorFilterWithCurrentUser() {
        UserPrincipal annotator = UserPrincipal.from(user("annotator-1", Role.ANNOTATOR, true));
        when(mongoTemplate.count(any(Query.class),
                org.mockito.ArgumentMatchers.eq(TaskDocument.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class),
                org.mockito.ArgumentMatchers.eq(TaskDocument.class))).thenReturn(List.of());
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);

        service.list(null, null, null, "annotator-2", null, 0, 10, annotator);

        verify(mongoTemplate).count(
                queryCaptor.capture(), org.mockito.ArgumentMatchers.eq(TaskDocument.class));
        assertThat(queryCaptor.getValue().getQueryObject().getString("annotatorId"))
                .isEqualTo("annotator-1");
    }

    @Test
    void adminCanReadAnyTaskDetail() {
        TaskDocument task = task(TaskState.PENDING_ANNOTATION, null);
        UserPrincipal admin = UserPrincipal.from(user("admin-1", Role.ADMIN, true));
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));

        assertThat(service.getDetail("task-1", admin).taskId()).isEqualTo("task-1");
    }

    @Test
    void annotatorCanReadOwnTaskDetail() {
        TaskDocument task = task(TaskState.PENDING_ANNOTATION, null);
        UserPrincipal annotator = UserPrincipal.from(user("annotator-1", Role.ANNOTATOR, true));
        when(taskRepository.findByTaskIdAndAnnotatorId("task-1", "annotator-1"))
                .thenReturn(Optional.of(task));

        assertThat(service.getDetail("task-1", annotator).annotatorId())
                .isEqualTo("annotator-1");
    }

    @Test
    void annotatorCannotReadAnotherAnnotatorsTaskDetail() {
        UserPrincipal annotator = UserPrincipal.from(user("annotator-2", Role.ANNOTATOR, true));
        when(taskRepository.findByTaskIdAndAnnotatorId("task-1", "annotator-2"))
                .thenReturn(Optional.empty());

        assertCode(
                () -> service.getDetail("task-1", annotator),
                TaskErrorCodes.NOT_FOUND);
        verify(taskRepository, never()).findById("task-1");
    }

    @Test
    void deletedFormalOrPendingRevisionLawIsRejected() {
        when(lawRepository.findById("law-1"))
                .thenReturn(Optional.of(law(Instant.now(), false, null)));
        assertCode(
                () -> service.createOrdinaryTask(
                        "law-1", "annotator-1", null, null, "admin-1"),
                TaskErrorCodes.LAW_DELETED);

        when(lawRepository.findById("law-1"))
                .thenReturn(Optional.of(law(null, false, "annotation-1")));
        assertCode(
                () -> service.createOrdinaryTask(
                        "law-1", "annotator-1", null, null, "admin-1"),
                TaskErrorCodes.FORMAL_ANNOTATION_EXISTS);

        when(lawRepository.findById("law-1"))
                .thenReturn(Optional.of(law(null, true, "annotation-1")));
        assertCode(
                () -> service.createOrdinaryTask(
                        "law-1", "annotator-1", null, null, "admin-1"),
                TaskErrorCodes.FORMAL_ANNOTATION_EXISTS);
    }

    @Test
    void contentVersionMustBelongToLawAndContainArticle() {
        LawDocument law = law(null, false, null);
        when(lawRepository.findById("law-1")).thenReturn(Optional.of(law));
        when(contentVersionRepository.findById("content-1"))
                .thenReturn(Optional.of(contentVersion("other-law", List.of())));
        assertCode(
                () -> service.createOrdinaryTask(
                        "law-1", "annotator-1", null, null, "admin-1"),
                TaskErrorCodes.CONTENT_VERSION_INVALID);

        when(contentVersionRepository.findById("content-1"))
                .thenReturn(Optional.of(contentVersion("law-1", List.of())));
        assertCode(
                () -> service.createOrdinaryTask(
                        "law-1", "annotator-1", null, null, "admin-1"),
                TaskErrorCodes.NO_VALID_ARTICLE);
    }

    @Test
    void annotatorMustExistBeEnabledAndHaveAnnotatorRole() {
        LawDocument law = law(null, false, null);
        when(lawRepository.findById("law-1")).thenReturn(Optional.of(law));
        when(contentVersionRepository.findById("content-1"))
                .thenReturn(Optional.of(contentVersion(
                        "law-1", List.of(ArticleSnapshot.createNew("第一条", "正文", 0)))));

        when(userRepository.findById("annotator-1")).thenReturn(Optional.empty());
        assertCode(
                () -> service.createOrdinaryTask(
                        "law-1", "annotator-1", null, null, "admin-1"),
                TaskErrorCodes.ANNOTATOR_NOT_FOUND);

        when(userRepository.findById("annotator-1"))
                .thenReturn(Optional.of(user("annotator-1", Role.ANNOTATOR, false)));
        assertCode(
                () -> service.createOrdinaryTask(
                        "law-1", "annotator-1", null, null, "admin-1"),
                TaskErrorCodes.ANNOTATOR_DISABLED);

        when(userRepository.findById("annotator-1"))
                .thenReturn(Optional.of(user("annotator-1", Role.ADMIN, true)));
        assertCode(
                () -> service.createOrdinaryTask(
                        "law-1", "annotator-1", null, null, "admin-1"),
                TaskErrorCodes.ANNOTATOR_ROLE_INVALID);
    }

    @Test
    void startUsesConditionalUpdateAndReturnsAnnotatingTask() {
        TaskDocument annotating = task(TaskState.ANNOTATING, null);
        when(mongoTemplate.findAndModify(any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(TaskDocument.class)))
                .thenReturn(annotating);

        TaskDetailResponse response = service.start("task-1", "annotator-1");

        assertThat(response.taskState()).isEqualTo(TaskState.ANNOTATING);
        verify(mongoTemplate).findAndModify(any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(TaskDocument.class));
    }

    @Test
    void repeatedOrIllegalStartIsRejected() {
        when(taskRepository.findById("task-1"))
                .thenReturn(Optional.of(task(TaskState.ANNOTATING, null)));

        assertCode(
                () -> service.start("task-1", "annotator-1"),
                TaskErrorCodes.INVALID_STATE_TRANSITION);
    }

    @Test
    void onlyAssignedAnnotatorCanStart() {
        when(taskRepository.findById("task-1"))
                .thenReturn(Optional.of(task(TaskState.PENDING_ANNOTATION, null)));

        assertCode(
                () -> service.start("task-1", "other-annotator"),
                TaskErrorCodes.NOT_ASSIGNEE);
    }

    @Test
    void pendingAndAnnotatingTasksCanBeCanceledWithTrimmedReason() {
        TaskDocument canceled = task(TaskState.CANCELED, "管理员取消");
        UserPrincipal admin = UserPrincipal.from(user("admin-1", Role.ADMIN, true));
        when(mongoTemplate.findAndModify(any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(TaskDocument.class)))
                .thenReturn(canceled);

        TaskDetailResponse response = service.cancel("task-1", "  管理员取消  ", admin);

        assertThat(response.taskState()).isEqualTo(TaskState.CANCELED);
        assertThat(response.cancelReason()).isEqualTo("管理员取消");
    }

    @Test
    void annotatorCannotCancelTask() {
        UserPrincipal annotator = UserPrincipal.from(user("annotator-1", Role.ANNOTATOR, true));

        assertCode(
                () -> service.cancel("task-1", "取消原因", annotator),
                "AUTH.FORBIDDEN");
        verify(mongoTemplate, never()).findAndModify(any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(TaskDocument.class));
    }

    @Test
    void pendingReviewAndLaterStatesCannotBeCanceled() {
        UserPrincipal admin = UserPrincipal.from(user("admin-1", Role.ADMIN, true));
        when(taskRepository.findById("task-1"))
                .thenReturn(Optional.of(task(TaskState.PENDING_REVIEW, null)));

        assertCode(
                () -> service.cancel("task-1", "不再处理", admin),
                TaskErrorCodes.INVALID_STATE_TRANSITION);
    }

    @Test
    void cancelReasonMustBeOneToFiveHundredCharactersAfterTrim() {
        UserPrincipal admin = UserPrincipal.from(user("admin-1", Role.ADMIN, true));
        assertCode(
                () -> service.cancel("task-1", "   ", admin),
                "COMMON.VALIDATION_FAILED");
        assertCode(
                () -> service.cancel("task-1", "x".repeat(501), admin),
                "COMMON.VALIDATION_FAILED");
        verify(mongoTemplate, never()).findAndModify(any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(TaskDocument.class));
    }

    private void stubEligibleCreation() {
        stubEligibleCreation("测试法");
    }

    private void stubEligibleCreation(String lawName) {
        when(lawRepository.findById("law-1"))
                .thenReturn(Optional.of(law(lawName, null, false, null)));
        when(contentVersionRepository.findById("content-1"))
                .thenReturn(Optional.of(contentVersion(
                        "law-1", List.of(ArticleSnapshot.createNew("第一条", "正文", 0)))));
        when(userRepository.findById("annotator-1"))
                .thenReturn(Optional.of(user("annotator-1", Role.ANNOTATOR, true)));
        when(fieldConfigService.getCurrentSnapshot()).thenReturn(fieldSnapshot());
    }

    private static LawDocument law(
            Instant deletedAt,
            boolean pendingRevision,
            String annotationVersionId) {
        return law("测试法", deletedAt, pendingRevision, annotationVersionId);
    }

    private static LawDocument law(
            String name,
            Instant deletedAt,
            boolean pendingRevision,
            String annotationVersionId) {
        Instant now = Instant.parse("2026-08-23T00:00:00Z");
        return new LawDocument(
                "law-1",
                name,
                name,
                "制定机关",
                LocalDate.of(2026, 8, 23),
                ValidityStatus.ACTIVE,
                List.of(new LawStructureNode(
                        "chapter-1",
                        LawStructureNodeType.CHAPTER,
                        "第一章 总则",
                        null,
                        0,
                        List.of("article-1"))),
                deletedAt,
                "content-1",
                annotationVersionId,
                pendingRevision,
                PendingChangeSet.empty(),
                now,
                now);
    }

    private static ContentVersionDocument contentVersion(
            String lawId,
            List<ArticleSnapshot> articles) {
        return new ContentVersionDocument(
                "content-1",
                lawId,
                1,
                articles,
                "admin-1",
                Instant.parse("2026-08-23T00:00:00Z"));
    }

    private static UserDocument user(String id, Role role, boolean enabled) {
        Instant now = Instant.parse("2026-08-23T00:00:00Z");
        UserDocument user = new UserDocument(
                role == Role.ANNOTATOR ? "标注员甲" : "管理员",
                id,
                id,
                "$2a$12$hash",
                role,
                enabled,
                now,
                now);
        user.setId(id);
        return user;
    }

    private static FieldConfigSnapshot fieldSnapshot() {
        return new FieldConfigSnapshot(
                List.of(new FieldConfigSnapshotItem("lawCategory", true)),
                List.of(new FieldConfigSnapshotItem("itemType", true)));
    }

    private static TaskDocument task(TaskState state, String cancelReason) {
        Instant now = Instant.parse("2026-08-23T00:00:00Z");
        return new TaskDocument(
                "task-1",
                TaskType.ORDINARY,
                state,
                "law-1",
                "annotator-1",
                "标注员甲",
                "测试任务",
                null,
                "content-1",
                new TaskContentVersionSnapshot("content-1", 1, List.of()),
                new TaskLawBaseInfoSnapshot(
                        "测试法", "制定机关", LocalDate.of(2026, 8, 23), ValidityStatus.ACTIVE),
                List.of(),
                fieldSnapshot(),
                "admin-1",
                cancelReason,
                cancelReason == null ? null : "admin-1",
                cancelReason == null ? null : now,
                now,
                now);
    }

    private static void assertCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            String code) {
        assertThatThrownBy(action)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
