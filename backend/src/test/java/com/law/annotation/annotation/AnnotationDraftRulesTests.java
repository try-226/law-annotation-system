package com.law.annotation.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.law.annotation.annotation.dto.AnnotationProgressResponse;
import com.law.annotation.annotation.dto.SaveArticleDraftRequest;
import com.law.annotation.annotation.dto.SaveOverallDraftRequest;
import com.law.annotation.common.enums.ItemType;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.field.FieldConfigSnapshot;
import com.law.annotation.field.FieldConfigSnapshotItem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnnotationDraftRulesTests {

    @Test
    void normalizesAndValidatesFixedFieldValues() {
        SaveOverallDraftRequest overallRequest = new SaveOverallDraftRequest();
        overallRequest.setLawCategory(" 民事 ");
        overallRequest.setOverallKeywords(" 合同， 权利 ");
        overallRequest.setSummary("  摘要  ");
        OverallDraftValues overall = AnnotationDraftRules.normalizeOverall(overallRequest);
        assertThat(overall.lawCategory()).isEqualTo("民事");
        assertThat(overall.overallKeywords()).isEqualTo("合同,权利");
        assertThat(overall.summary()).isEqualTo("摘要");

        SaveArticleDraftRequest articleRequest = new SaveArticleDraftRequest();
        articleRequest.setItemType("RIGHTS_DUTIES");
        articleRequest.setKeywords("权利, 义务");
        ArticleDraftValues article = AnnotationDraftRules.normalizeArticle(articleRequest);
        assertThat(article.itemType()).isEqualTo(ItemType.RIGHTS_DUTIES);
        assertThat(article.keywords()).isEqualTo("权利,义务");
    }

    @Test
    void invalidSelectAndKeywordShapesAreRejected() {
        SaveOverallDraftRequest invalidSelect = new SaveOverallDraftRequest();
        invalidSelect.setLawCategory("未知类别");
        assertThatThrownBy(() -> AnnotationDraftRules.normalizeOverall(invalidSelect))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("COMMON.VALIDATION_FAILED");

        SaveArticleDraftRequest invalidKeywords = new SaveArticleDraftRequest();
        invalidKeywords.setKeywords("关键词一,,关键词二");
        assertThatThrownBy(() -> AnnotationDraftRules.normalizeArticle(invalidKeywords))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("COMMON.VALIDATION_FAILED");
    }

    @Test
    void progressUsesOnlySavedTaskSnapshotArticlesAndRequiredFlags() {
        var task = AnnotationTestFixtures.task(TaskState.ANNOTATING);
        TaskDraftDocument draft = new TaskDraftDocument(
                "task-1",
                new OverallDraftValues("民事", "合同", null, null),
                Map.of(
                        "article-1",
                        new ArticleDraftValues(ItemType.DEFINITION, "定义", null, null, null),
                        "outside-snapshot",
                        new ArticleDraftValues(ItemType.OTHER, "不应统计", null, null, null)),
                2,
                "annotator-1",
                AnnotationTestFixtures.NOW,
                AnnotationTestFixtures.NOW);

        AnnotationProgressResponse progress = AnnotationDraftRules.progress(task, draft);

        assertThat(progress.totalArticles()).isEqualTo(2);
        assertThat(progress.filledArticles()).isEqualTo(1);
        assertThat(progress.overallCompleted()).isTrue();
    }

    @Test
    void completionUsesFrozenFieldConfigSnapshotInsteadOfAnyCurrentConfig() {
        TaskDraftDocument draft = new TaskDraftDocument(
                "task-1",
                new OverallDraftValues("民事", "合同", null, null),
                Map.of(
                        "article-1",
                        new ArticleDraftValues(ItemType.DEFINITION, "定义", null, null, null),
                        "article-2",
                        new ArticleDraftValues(ItemType.OTHER, "其他", null, null, null)),
                3,
                "annotator-1",
                AnnotationTestFixtures.NOW,
                AnnotationTestFixtures.NOW);
        var originalTask = AnnotationTestFixtures.task(TaskState.ANNOTATING);
        FieldConfigSnapshot stricterSnapshot = new FieldConfigSnapshot(
                List.of(
                        new FieldConfigSnapshotItem("lawCategory", true),
                        new FieldConfigSnapshotItem("overallKeywords", true),
                        new FieldConfigSnapshotItem("summary", true),
                        new FieldConfigSnapshotItem("overallNote", false)),
                originalTask.getFieldConfigSnapshot().article());
        var laterTask = AnnotationTestFixtures.task(TaskState.ANNOTATING, stricterSnapshot);

        assertThat(AnnotationDraftRules.progress(originalTask, draft).overallCompleted()).isTrue();
        assertThat(AnnotationDraftRules.progress(laterTask, draft).overallCompleted()).isFalse();
    }

    @Test
    void submitMissingLocatorsAreCompleteAndSnapshotAddressable() {
        var task = AnnotationTestFixtures.task(TaskState.ANNOTATING);
        Map<String, ArticleDraftValues> articleDrafts = new LinkedHashMap<>();
        articleDrafts.put(
                "article-1",
                new ArticleDraftValues(ItemType.DEFINITION, null, null, null, null));
        TaskDraftDocument draft = new TaskDraftDocument(
                "task-1",
                new OverallDraftValues("民事", null, null, null),
                articleDrafts,
                1,
                "annotator-1",
                AnnotationTestFixtures.NOW,
                AnnotationTestFixtures.NOW);

        List<ErrorLocator> locators = AnnotationDraftRules.missingRequired(task, draft);

        assertThat(locators).extracting(ErrorLocator::path).containsExactly(
                "overall.overallKeywords",
                "articles.article-1.keywords",
                "articles.article-2.itemType",
                "articles.article-2.keywords");
        assertThat(locators.get(1).message()).contains("第一章 总则", "第一条", "关键词未填写");
    }
}
