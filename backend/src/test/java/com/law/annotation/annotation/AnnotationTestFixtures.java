package com.law.annotation.annotation;

import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.enums.TaskType;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.field.FieldConfigSnapshot;
import com.law.annotation.field.FieldConfigSnapshotItem;
import com.law.annotation.law.LawStructureNodeType;
import com.law.annotation.task.TaskArticleSnapshot;
import com.law.annotation.task.TaskContentVersionSnapshot;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskLawBaseInfoSnapshot;
import com.law.annotation.task.TaskStructureNodeSnapshot;
import com.law.annotation.user.UserDocument;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

final class AnnotationTestFixtures {

    static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");

    private AnnotationTestFixtures() {
    }

    static TaskDocument task(TaskState state) {
        return task(state, fieldSnapshot());
    }

    static TaskDocument task(TaskState state, FieldConfigSnapshot snapshot) {
        return task(state, snapshot, null);
    }

    static TaskDocument task(
            TaskState state,
            FieldConfigSnapshot snapshot,
            String initialSubmissionId) {
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
                new TaskContentVersionSnapshot(
                        "content-1",
                        1,
                        List.of(
                                new TaskArticleSnapshot("article-1", "第一条", "正文一", 0),
                                new TaskArticleSnapshot("article-2", "第二条", "正文二", 1))),
                new TaskLawBaseInfoSnapshot(
                        "测试法",
                        "制定机关",
                        LocalDate.of(2026, 8, 24),
                        ValidityStatus.ACTIVE),
                List.of(
                        new TaskStructureNodeSnapshot(
                                "chapter-1",
                                LawStructureNodeType.CHAPTER,
                                "第一章 总则",
                                null,
                                0,
                                List.of("article-1", "article-2"))),
                snapshot,
                "admin-1",
                initialSubmissionId,
                null,
                null,
                null,
                NOW,
                NOW);
    }

    static FieldConfigSnapshot fieldSnapshot() {
        return new FieldConfigSnapshot(
                List.of(
                        new FieldConfigSnapshotItem("lawCategory", true),
                        new FieldConfigSnapshotItem("overallKeywords", true),
                        new FieldConfigSnapshotItem("summary", false),
                        new FieldConfigSnapshotItem("overallNote", false)),
                List.of(
                        new FieldConfigSnapshotItem("itemType", true),
                        new FieldConfigSnapshotItem("keywords", true),
                        new FieldConfigSnapshotItem("subjects", false),
                        new FieldConfigSnapshotItem("legalLiability", false),
                        new FieldConfigSnapshotItem("annotationNote", false)));
    }

    static UserPrincipal principal(String id, Role role) {
        UserDocument user = new UserDocument(
                role == Role.ADMIN ? "管理员" : "标注员甲",
                id,
                id,
                "$2a$12$hash",
                role,
                true,
                NOW,
                NOW);
        user.setId(id);
        return UserPrincipal.from(user);
    }
}
