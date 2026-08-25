package com.law.annotation.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.law.dto.LawBaseInfoInput;
import com.law.annotation.law.dto.LawImportArticleInput;
import com.law.annotation.law.dto.LawImportConfirmRequest;
import com.law.annotation.law.dto.LawStructureInput;
import com.law.annotation.version.ContentVersionDocument;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LawImportServiceTests {

    @Test
    void confirmMapsPreviewReferencesAndDelegatesCreationToPr04Service() {
        LawCreationService creationService = org.mockito.Mockito.mock(LawCreationService.class);
        LawTextParser parser = org.mockito.Mockito.mock(LawTextParser.class);
        LawImportService service = new LawImportService(parser, creationService);
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        LawDocument law = LawDocument.createInitial(
                "law-1",
                "测试法",
                "制定机关",
                LocalDate.of(2026, 8, 19),
                ValidityStatus.ACTIVE,
                List.of(),
                "c1",
                now);
        ContentVersionDocument c1 = new ContentVersionDocument(
                "c1",
                "law-1",
                1,
                List.of(new ArticleSnapshot("article-stable", "第一条", "正文", 0)),
                "admin-1",
                now);
        when(creationService.createInitialLaw(
                        eq("测试法"),
                        eq("制定机关"),
                        eq(LocalDate.of(2026, 8, 19)),
                        eq(ValidityStatus.ACTIVE),
                        any(),
                        any(),
                        eq("admin-1")))
                .thenReturn(new InitialLawCreation(law, c1));

        LawImportConfirmRequest request = new LawImportConfirmRequest(
                new LawBaseInfoInput(
                        "测试法",
                        "制定机关",
                        LocalDate.of(2026, 8, 19),
                        ValidityStatus.ACTIVE),
                List.of(new LawStructureInput(
                        "chapter-1",
                        LawStructureNodeType.CHAPTER,
                        "第一章 总则",
                        null,
                        0,
                        List.of("preview-article-1"))),
                List.of(new LawImportArticleInput(
                        "preview-article-1", "第一条", "正文", 0)));

        assertThat(service.confirm(request, "admin-1").currentContentVersionId()).isEqualTo("c1");
        verifyNoInteractions(parser);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LawStructureNode>> structureCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NewArticleDraft>> articleCaptor = ArgumentCaptor.forClass(List.class);
        verify(creationService).createInitialLaw(
                eq("测试法"),
                eq("制定机关"),
                eq(LocalDate.of(2026, 8, 19)),
                eq(ValidityStatus.ACTIVE),
                structureCaptor.capture(),
                articleCaptor.capture(),
                eq("admin-1"));
        assertThat(structureCaptor.getValue().getFirst().getArticleIds())
                .containsExactly(articleCaptor.getValue().getFirst().articleId());
    }

    @Test
    void confirmRejectsZeroArticlesBeforeCreationService() {
        LawCreationService creationService = org.mockito.Mockito.mock(LawCreationService.class);
        LawImportService service = new LawImportService(new LawTextParser(), creationService);
        LawImportConfirmRequest request = new LawImportConfirmRequest(
                baseInfo(),
                List.of(),
                List.of());

        assertThatThrownBy(() -> service.confirm(request, "admin-1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.VALIDATION_FAILED);
        verify(creationService, never()).createInitialLaw(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void confirmRejectsDuplicateClientKeysBeforeCreationService() {
        LawCreationService creationService = org.mockito.Mockito.mock(LawCreationService.class);
        LawImportService service = new LawImportService(new LawTextParser(), creationService);
        LawImportConfirmRequest request = new LawImportConfirmRequest(
                baseInfo(),
                List.of(),
                List.of(
                        new LawImportArticleInput("same", "第一条", "正文", 0),
                        new LawImportArticleInput("same", "第二条", "正文", 1)));

        assertThatThrownBy(() -> service.confirm(request, "admin-1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.VALIDATION_FAILED);
        verify(creationService, never()).createInitialLaw(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void confirmDoesNotTrustPreviewAndMapsDomainValidationFailure() {
        LawCreationService creationService = org.mockito.Mockito.mock(LawCreationService.class);
        when(creationService.createInitialLaw(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("条号格式不合法"));
        LawImportService service = new LawImportService(new LawTextParser(), creationService);
        LawImportConfirmRequest request = new LawImportConfirmRequest(
                baseInfo(),
                List.of(),
                List.of(new LawImportArticleInput("one", "第一条之一", "正文", 0)));

        assertThatThrownBy(() -> service.confirm(request, "admin-1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("校验失败")
                .extracting("code")
                .isEqualTo(LawErrorCodes.VALIDATION_FAILED);
    }

    private static LawBaseInfoInput baseInfo() {
        return new LawBaseInfoInput(
                "测试法",
                "制定机关",
                LocalDate.of(2026, 8, 19),
                ValidityStatus.ACTIVE);
    }
}
