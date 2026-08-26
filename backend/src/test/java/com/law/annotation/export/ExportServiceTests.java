package com.law.annotation.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.export.dto.LawExportRequest;
import com.law.annotation.export.formatter.PlainExportCsvFormatter;
import com.law.annotation.export.formatter.PlainExportJsonFormatter;
import com.law.annotation.law.ArticleSnapshot;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawDomainRules;
import com.law.annotation.law.LawErrorCodes;
import com.law.annotation.law.LawRepository;
import com.law.annotation.law.LawStructureNode;
import com.law.annotation.law.LawStructureNodeType;
import com.law.annotation.law.PendingChangeSet;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExportServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");

    private LawRepository lawRepository;
    private ContentVersionRepository contentVersionRepository;
    private ObjectMapper objectMapper;
    private ExportService service;

    @BeforeEach
    void setUp() {
        lawRepository = org.mockito.Mockito.mock(LawRepository.class);
        contentVersionRepository = org.mockito.Mockito.mock(ContentVersionRepository.class);
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        service = new ExportService(
                lawRepository,
                contentVersionRepository,
                new PlainExportCsvFormatter(),
                new PlainExportJsonFormatter(objectMapper));
    }

    @Test
    void wholeJsonUsesCurrentBaseStructureAndLatestSemanticContentInFormalOrder() throws Exception {
        List<ArticleSnapshot> articles = articles();
        LawDocument law = law("content-2", false, false, null);
        ContentVersionDocument c2 = version(
                "content-2", 2, List.of(articles.get(2), articles.get(0), articles.get(1)));
        givenCurrentLaw(law, c2);

        ExportedFile file = service.export(
                "law-1",
                request(LawExportRequest.Scope.WHOLE, List.of(), LawExportRequest.Format.JSON));

        JsonNode json = objectMapper.readTree(file.content());
        assertThat(json.path("law").path("name").asText()).isEqualTo("当前法律名称");
        assertThat(json.path("law").path("issuingAuthority").asText()).isEqualTo("当前制定机关");
        assertThat(json.path("law").path("currentContentVersionId").asText())
                .isEqualTo("content-2");
        assertThat(json.path("structure").get(1).path("title").asText()).isEqualTo("当前第一节");
        assertThat(json.path("articles")).extracting(node -> node.path("articleId").asText())
                .containsExactly("article-1", "article-2", "article-3");
        assertThat(json.path("articles").get(2).path("structurePath"))
                .extracting(JsonNode::asText)
                .containsExactly("当前第一章", "当前第一节");
        assertThat(new String(file.content(), StandardCharsets.UTF_8))
                .contains("当前法律名称", "第一条正文")
                .doesNotContain("currentAnnotationVersionId", "overallResult", "perArticleResults");
        assertThat(file.contentType().toString()).isEqualTo("application/json;charset=UTF-8");
        verify(contentVersionRepository).findById("content-2");
    }

    @Test
    void selectedJsonUsesLatestContentOrderInsteadOfRequestOrder() throws Exception {
        LawDocument law = law("content-2", false, false, null);
        givenCurrentLaw(law, version("content-2", 2, articles()));

        ExportedFile file = service.export(
                "law-1",
                request(
                        LawExportRequest.Scope.SELECTED,
                        List.of("article-3", "article-1"),
                        LawExportRequest.Format.JSON));

        JsonNode articles = objectMapper.readTree(file.content()).path("articles");
        assertThat(articles).extracting(node -> node.path("articleId").asText())
                .containsExactly("article-1", "article-3")
                .doesNotContain("article-2");
    }

    @Test
    void wholeCsvIsUtf8AndEscapesCommaQuoteAndMultilineBody() {
        ArticleSnapshot escaped = new ArticleSnapshot(
                "article-1", "第一条", "第一行,包含\"引号\"\n第二行", 0);
        LawDocument law = law("content-2", false, false, null);
        givenCurrentLaw(law, version("content-2", 2, List.of(
                escaped,
                new ArticleSnapshot("article-2", "第二条", "第二条正文", 1),
                new ArticleSnapshot("article-3", "第三条", "第三条正文", 2))));

        ExportedFile file = service.export(
                "law-1",
                request(LawExportRequest.Scope.WHOLE, List.of(), LawExportRequest.Format.CSV));

        String csv = new String(file.content(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith(
                "lawId,lawName,issuingAuthority,publicationDate,validityStatus,"
                        + "contentVersionId,contentVersionSeq,articleId,articleNumber,"
                        + "articleBody,articleOrder,structurePath\r\n");
        assertThat(csv).contains("\"第一行,包含\"\"引号\"\"\n第二行\"");
        assertThat(csv).endsWith("\r\n");
        assertThat(file.contentType().toString()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(file.filename()).isEqualTo("law-law-1-plain.csv");
    }

    @Test
    void selectedCsvExportsOnlySelectedRowsInContentOrder() {
        LawDocument law = law("content-2", false, false, null);
        givenCurrentLaw(law, version("content-2", 2, articles()));

        String csv = new String(service.export(
                        "law-1",
                        request(
                                LawExportRequest.Scope.SELECTED,
                                List.of("article-3", "article-1"),
                                LawExportRequest.Format.CSV))
                .content(), StandardCharsets.UTF_8);

        assertThat(csv.indexOf("article-1")).isLessThan(csv.indexOf("article-3"));
        assertThat(csv).doesNotContain("article-2");
    }

    @Test
    void pendingRevisionWithOldFormalAnnotationStillExportsLatestPlainContent() throws Exception {
        LawDocument law = law("content-2", true, false, "annotation-1");
        givenCurrentLaw(law, version(
                "content-2", 2,
                List.of(
                        new ArticleSnapshot("article-1", "第一条", "C2新正文", 0),
                        new ArticleSnapshot("article-2", "第二条", "第二条正文", 1),
                        new ArticleSnapshot("article-3", "第三条", "第三条正文", 2))));

        ExportedFile file = service.export(
                "law-1",
                request(LawExportRequest.Scope.WHOLE, List.of(), LawExportRequest.Format.JSON));

        assertThat(objectMapper.readTree(file.content())
                        .path("articles").get(0).path("body").asText())
                .isEqualTo("C2新正文");
    }

    @Test
    void unannotatedLawCanExportPlainContent() {
        LawDocument law = law("content-1", false, false, null);
        givenCurrentLaw(law, version("content-1", 1, articles()));

        assertThat(service.export(
                        "law-1",
                        request(LawExportRequest.Scope.WHOLE, List.of(), LawExportRequest.Format.JSON))
                .content()).isNotEmpty();
    }

    @Test
    void missingAndSoftDeletedLawsUseTheNormalLawNotFoundContract() {
        when(lawRepository.findById("missing-law")).thenReturn(Optional.empty());
        LawDocument deleted = law("content-1", false, true, null);
        when(lawRepository.findById("law-1")).thenReturn(Optional.of(deleted));

        assertLawError(
                () -> service.export(
                        "missing-law",
                        request(LawExportRequest.Scope.WHOLE, List.of(), LawExportRequest.Format.JSON)),
                LawErrorCodes.NOT_FOUND);
        assertLawError(
                () -> service.export(
                        "law-1",
                        request(LawExportRequest.Scope.WHOLE, List.of(), LawExportRequest.Format.JSON)),
                LawErrorCodes.NOT_FOUND);
        verify(contentVersionRepository, never()).findById("content-1");
    }

    @Test
    void missingOrCrossLawCurrentContentVersionIsAConflict() {
        LawDocument law = law("content-2", false, false, null);
        when(lawRepository.findById("law-1")).thenReturn(Optional.of(law));
        when(contentVersionRepository.findById("content-2")).thenReturn(Optional.empty());

        assertLawError(
                () -> service.export(
                        "law-1",
                        request(LawExportRequest.Scope.WHOLE, List.of(), LawExportRequest.Format.JSON)),
                LawErrorCodes.VERSION_INCONSISTENT);

        when(contentVersionRepository.findById("content-2"))
                .thenReturn(Optional.of(new ContentVersionDocument(
                        "content-2", "other-law", 2, articles(), "admin-1", NOW)));
        assertLawError(
                () -> service.export(
                        "law-1",
                        request(LawExportRequest.Scope.WHOLE, List.of(), LawExportRequest.Format.JSON)),
                LawErrorCodes.VERSION_INCONSISTENT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSelections")
    void invalidSelectedArticleIdsAreRejected(String ignoredCaseName, List<String> articleIds) {
        LawDocument law = law("content-2", false, false, null);
        givenCurrentLaw(law, version("content-2", 2, articles()));

        assertThatThrownBy(() -> service.export(
                        "law-1",
                        request(
                                LawExportRequest.Scope.SELECTED,
                                articleIds,
                                LawExportRequest.Format.JSON)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ExportErrorCodes.SELECTION_INVALID);
    }

    @Test
    void wholeScopeRejectsUnexpectedArticleIds() {
        LawDocument law = law("content-2", false, false, null);
        givenCurrentLaw(law, version("content-2", 2, articles()));

        assertThatThrownBy(() -> service.export(
                        "law-1",
                        request(
                                LawExportRequest.Scope.WHOLE,
                                List.of("article-1"),
                                LawExportRequest.Format.JSON)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ExportErrorCodes.SELECTION_INVALID);
    }

    @Test
    void formalExportIsExplicitlyUnsupportedWithoutReadingLawData() {
        LawExportRequest request = new LawExportRequest(
                LawExportRequest.Scope.WHOLE,
                List.of(),
                LawExportRequest.Type.FORMAL,
                LawExportRequest.Format.JSON);

        assertThatThrownBy(() -> service.export("law-1", request))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ExportErrorCodes.TYPE_UNSUPPORTED);
        verify(lawRepository, never()).findById("law-1");
    }

    private static Stream<Arguments> invalidSelections() {
        return Stream.of(
                Arguments.of("empty selection", List.of()),
                Arguments.of("null article id", Collections.singletonList(null)),
                Arguments.of("blank article id", List.of(" ")),
                Arguments.of("duplicate article id", List.of("article-1", "article-1")),
                Arguments.of("old or deleted article id", List.of("article-old")),
                Arguments.of("cross-law article id", List.of("other-law-article")));
    }

    private void givenCurrentLaw(LawDocument law, ContentVersionDocument version) {
        when(lawRepository.findById("law-1")).thenReturn(Optional.of(law));
        when(contentVersionRepository.findById(law.getCurrentContentVersionId()))
                .thenReturn(Optional.of(version));
    }

    private static LawExportRequest request(
            LawExportRequest.Scope scope,
            List<String> articleIds,
            LawExportRequest.Format format) {
        return new LawExportRequest(scope, articleIds, LawExportRequest.Type.PLAIN, format);
    }

    private static List<ArticleSnapshot> articles() {
        return List.of(
                new ArticleSnapshot("article-1", "第一条", "第一条正文", 0),
                new ArticleSnapshot("article-2", "第二条", "第二条正文", 1),
                new ArticleSnapshot("article-3", "第三条", "第三条正文", 2));
    }

    private static ContentVersionDocument version(
            String id,
            int seq,
            List<ArticleSnapshot> articles) {
        return new ContentVersionDocument(id, "law-1", seq, articles, "admin-1", NOW);
    }

    private static LawDocument law(
            String contentVersionId,
            boolean pendingRevision,
            boolean deleted,
            String annotationVersionId) {
        List<LawStructureNode> structure = List.of(
                new LawStructureNode(
                        "chapter-1",
                        LawStructureNodeType.CHAPTER,
                        "当前第一章",
                        null,
                        0,
                        List.of("article-1", "article-2")),
                new LawStructureNode(
                        "section-1",
                        LawStructureNodeType.SECTION,
                        "当前第一节",
                        "chapter-1",
                        1,
                        List.of("article-3")));
        LawDocument law = new LawDocument(
                "law-1",
                "当前法律名称",
                LawDomainRules.normalizeLawName("当前法律名称"),
                "当前制定机关",
                LocalDate.of(2026, 8, 19),
                ValidityStatus.ACTIVE,
                structure,
                null,
                contentVersionId,
                annotationVersionId,
                pendingRevision,
                pendingRevision
                        ? PendingChangeSet.empty().recordModification("article-1")
                        : PendingChangeSet.empty(),
                NOW,
                NOW);
        if (deleted) {
            law.markDeleted(NOW.plusSeconds(60));
        }
        return law;
    }

    private static void assertLawError(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
