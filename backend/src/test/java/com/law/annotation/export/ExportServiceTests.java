package com.law.annotation.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.law.annotation.annotation.ArticleDraftValues;
import com.law.annotation.annotation.OverallDraftValues;
import com.law.annotation.common.enums.ItemType;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.export.dto.LawExportRequest;
import com.law.annotation.export.formatter.FormalExportCsvFormatter;
import com.law.annotation.export.formatter.FormalExportJsonFormatter;
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
import com.law.annotation.version.AnnotationVersionDocument;
import com.law.annotation.version.AnnotationVersionRepository;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private AnnotationVersionRepository annotationVersionRepository;
    private ObjectMapper objectMapper;
    private ExportService service;

    @BeforeEach
    void setUp() {
        lawRepository = org.mockito.Mockito.mock(LawRepository.class);
        contentVersionRepository = org.mockito.Mockito.mock(ContentVersionRepository.class);
        annotationVersionRepository = org.mockito.Mockito.mock(AnnotationVersionRepository.class);
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        service = new ExportService(
                lawRepository,
                contentVersionRepository,
                annotationVersionRepository,
                new PlainExportCsvFormatter(),
                new PlainExportJsonFormatter(objectMapper),
                new FormalExportCsvFormatter(),
                new FormalExportJsonFormatter(objectMapper));
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
        assertThat(articleIdsInStructure(json.path("structure")))
                .containsExactlyInAnyOrder("article-1", "article-2", "article-3");
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
    void selectedJsonFiltersStructureToRelatedNodesAndKeepsPaths() throws Exception {
        LawDocument law = law("content-2", false, false, null);
        givenCurrentLaw(law, version("content-2", 2, articles()));

        ExportedFile file = service.export(
                "law-1",
                request(
                        LawExportRequest.Scope.SELECTED,
                        List.of("article-3", "article-1"),
                        LawExportRequest.Format.JSON));

        JsonNode json = objectMapper.readTree(file.content());
        JsonNode exportedArticles = json.path("articles");
        assertThat(exportedArticles).extracting(node -> node.path("articleId").asText())
                .containsExactly("article-1", "article-3")
                .doesNotContain("article-2");
        assertThat(json.path("structure")).extracting(node -> node.path("nodeId").asText())
                .containsExactly("chapter-1", "section-1")
                .doesNotContain("chapter-2");
        assertThat(articleIdsInStructure(json.path("structure")))
                .containsExactlyInAnyOrder("article-1", "article-3")
                .doesNotContain("article-2");
        assertThat(json.path("structure").get(1).path("parentNodeId").asText())
                .isEqualTo("chapter-1");
        assertThat(exportedArticles.get(1).path("structurePath"))
                .extracting(JsonNode::asText)
                .containsExactly("当前第一章", "当前第一节");
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
    void formalWholeJsonPairsCurrentCAndAAndIncludesApprovalMetadata() throws Exception {
        LawDocument law = law("content-2", false, false, "annotation-2");
        ContentVersionDocument content = version("content-2", 2, articles());
        AnnotationVersionDocument annotation = annotation(
                "annotation-2", "law-1", "content-2", articleResults());
        givenCurrentFormalLaw(law, content, annotation);

        JsonNode json = objectMapper.readTree(service.export(
                        "law-1",
                        formalRequest(
                                LawExportRequest.Scope.WHOLE,
                                List.of(),
                                LawExportRequest.Format.JSON))
                .content());

        assertThat(json.path("semanticVersion").path("contentVersionId").asText())
                .isEqualTo("content-2");
        assertThat(json.path("annotationVersion").path("annotationVersionId").asText())
                .isEqualTo("annotation-2");
        assertThat(json.path("overallAnnotation").path("lawCategory").asText())
                .isEqualTo("行政法");
        assertThat(json.path("articles").get(0).path("itemType").asText())
                .isEqualTo("RIGHTS_DUTIES");
        assertThat(json.path("approvalMetadata").path("approvedBy").asText())
                .isEqualTo("reviewer-1");
        assertThat(json.path("approvalMetadata").path("sourceTaskId").asText())
                .isEqualTo("task-1");
    }

    @Test
    void formalSelectedJsonKeepsContentOrderAndOnlyRelatedStructure() throws Exception {
        LawDocument law = law("content-2", false, false, "annotation-2");
        givenCurrentFormalLaw(
                law,
                version("content-2", 2, articles()),
                annotation("annotation-2", "law-1", "content-2", articleResults()));

        JsonNode json = objectMapper.readTree(service.export(
                        "law-1",
                        formalRequest(
                                LawExportRequest.Scope.SELECTED,
                                List.of("article-3", "article-1"),
                                LawExportRequest.Format.JSON))
                .content());

        assertThat(json.path("articles")).extracting(node -> node.path("articleId").asText())
                .containsExactly("article-1", "article-3");
        assertThat(json.path("structure")).extracting(node -> node.path("nodeId").asText())
                .containsExactly("chapter-1", "section-1");
        assertThat(articleIdsInStructure(json.path("structure")))
                .containsExactlyInAnyOrder("article-1", "article-3");
    }

    @Test
    void formalWholeAndSelectedCsvUseDedicatedColumnsAndEscapeValues() {
        List<ArticleSnapshot> escapedArticles = List.of(
                new ArticleSnapshot(
                        "article-1", "第一条", "第一行,含\"引号\"\n第二行", 0),
                new ArticleSnapshot("article-2", "第二条", "第二条正文", 1),
                new ArticleSnapshot("article-3", "第三条", "第三条正文", 2));
        Map<String, ArticleDraftValues> results = new LinkedHashMap<>(articleResults());
        results.put("article-1", new ArticleDraftValues(
                ItemType.RIGHTS_DUTIES,
                "权利,义务",
                "公民",
                "责任\"说明\"",
                "第一行\n第二行"));
        LawDocument law = law("content-2", false, false, "annotation-2");
        givenCurrentFormalLaw(
                law,
                version("content-2", 2, escapedArticles),
                annotation("annotation-2", "law-1", "content-2", results));

        ExportedFile whole = service.export(
                "law-1",
                formalRequest(
                        LawExportRequest.Scope.WHOLE,
                        List.of(),
                        LawExportRequest.Format.CSV));
        String wholeCsv = new String(whole.content(), StandardCharsets.UTF_8);
        assertThat(whole.filename()).isEqualTo("law-law-1-formal.csv");
        assertThat(wholeCsv).startsWith(
                "lawId,lawName,issuingAuthority,publicationDate,validityStatus,"
                        + "contentVersionId,contentVersionSeq,annotationVersionId,");
        assertThat(wholeCsv).contains(
                "\"第一行,含\"\"引号\"\"\n第二行\"",
                "\"权利,义务\"",
                "\"责任\"\"说明\"\"\"",
                "\"第一行\n第二行\"");

        String selectedCsv = new String(service.export(
                        "law-1",
                        formalRequest(
                                LawExportRequest.Scope.SELECTED,
                                List.of("article-3", "article-1"),
                                LawExportRequest.Format.CSV))
                .content(), StandardCharsets.UTF_8);
        assertThat(selectedCsv.indexOf("article-1")).isLessThan(selectedCsv.indexOf("article-3"));
        assertThat(selectedCsv).doesNotContain("article-2");
    }

    @Test
    void formalExportRejectsMissingCrossLawMismatchedAndIncompleteAnnotations() {
        ContentVersionDocument c2 = version("content-2", 2, articles());

        LawDocument unannotated = law("content-2", false, false, null);
        givenCurrentLaw(unannotated, c2);
        assertExportError(
                () -> service.export(
                        "law-1",
                        formalRequest(
                                LawExportRequest.Scope.WHOLE,
                                List.of(),
                                LawExportRequest.Format.JSON)),
                ExportErrorCodes.FORMAL_UNAVAILABLE);

        LawDocument law = law("content-2", false, false, "annotation-2");
        givenCurrentLaw(law, c2);
        when(annotationVersionRepository.findById("annotation-2")).thenReturn(Optional.empty());
        assertExportError(
                () -> service.export(
                        "law-1",
                        formalRequest(
                                LawExportRequest.Scope.WHOLE,
                                List.of(),
                                LawExportRequest.Format.JSON)),
                ExportErrorCodes.ANNOTATION_INCONSISTENT);

        when(annotationVersionRepository.findById("annotation-2"))
                .thenReturn(Optional.of(annotation(
                        "annotation-2", "other-law", "content-2", articleResults())));
        assertExportError(
                () -> service.export(
                        "law-1",
                        formalRequest(
                                LawExportRequest.Scope.WHOLE,
                                List.of(),
                                LawExportRequest.Format.JSON)),
                ExportErrorCodes.ANNOTATION_INCONSISTENT);

        when(annotationVersionRepository.findById("annotation-2"))
                .thenReturn(Optional.of(annotation(
                        "annotation-2", "law-1", "content-1", articleResults())));
        assertExportError(
                () -> service.export(
                        "law-1",
                        formalRequest(
                                LawExportRequest.Scope.WHOLE,
                                List.of(),
                                LawExportRequest.Format.JSON)),
                ExportErrorCodes.VERSION_MISMATCH);

        Map<String, ArticleDraftValues> incomplete = new LinkedHashMap<>(articleResults());
        incomplete.remove("article-2");
        when(annotationVersionRepository.findById("annotation-2"))
                .thenReturn(Optional.of(annotation(
                        "annotation-2", "law-1", "content-2", incomplete)));
        assertExportError(
                () -> service.export(
                        "law-1",
                        formalRequest(
                                LawExportRequest.Scope.SELECTED,
                                List.of("article-1"),
                                LawExportRequest.Format.JSON)),
                ExportErrorCodes.ANNOTATION_INCONSISTENT);
    }

    @Test
    void formalPairingUsesFactsInsteadOfPendingRevisionFlag() throws Exception {
        LawDocument semanticPending = law("content-2", true, false, "annotation-1");
        givenCurrentFormalLaw(
                semanticPending,
                version("content-2", 2, articles()),
                annotation("annotation-1", "law-1", "content-1", articleResults()));
        assertExportError(
                () -> service.export(
                        "law-1",
                        formalRequest(
                                LawExportRequest.Scope.WHOLE,
                                List.of(),
                                LawExportRequest.Format.JSON)),
                ExportErrorCodes.VERSION_MISMATCH);

        LawDocument annotationOnlyRevision = law(
                "content-1", true, false, "annotation-1");
        givenCurrentFormalLaw(
                annotationOnlyRevision,
                version("content-1", 1, articles()),
                annotation("annotation-1", "law-1", "content-1", articleResults()));
        JsonNode annotationOnlyJson = objectMapper.readTree(service.export(
                "law-1",
                formalRequest(
                        LawExportRequest.Scope.WHOLE,
                        List.of(),
                        LawExportRequest.Format.JSON)).content());
        assertThat(annotationOnlyJson.path("law").path("name").asText())
                .isEqualTo("当前法律名称");
        assertThat(annotationOnlyJson.path("structure").get(0).path("title").asText())
                .isEqualTo("当前第一章");
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

    private void givenCurrentFormalLaw(
            LawDocument law,
            ContentVersionDocument version,
            AnnotationVersionDocument annotation) {
        givenCurrentLaw(law, version);
        when(annotationVersionRepository.findById(law.getCurrentAnnotationVersionId()))
                .thenReturn(Optional.of(annotation));
    }

    private static LawExportRequest request(
            LawExportRequest.Scope scope,
            List<String> articleIds,
            LawExportRequest.Format format) {
        return new LawExportRequest(scope, articleIds, LawExportRequest.Type.PLAIN, format);
    }

    private static LawExportRequest formalRequest(
            LawExportRequest.Scope scope,
            List<String> articleIds,
            LawExportRequest.Format format) {
        return new LawExportRequest(scope, articleIds, LawExportRequest.Type.FORMAL, format);
    }

    private static List<ArticleSnapshot> articles() {
        return List.of(
                new ArticleSnapshot("article-1", "第一条", "第一条正文", 0),
                new ArticleSnapshot("article-2", "第二条", "第二条正文", 1),
                new ArticleSnapshot("article-3", "第三条", "第三条正文", 2));
    }

    private static List<String> articleIdsInStructure(JsonNode structure) {
        List<String> articleIds = new ArrayList<>();
        structure.forEach(node -> node.path("articleIds")
                .forEach(articleId -> articleIds.add(articleId.asText())));
        return articleIds;
    }

    private static ContentVersionDocument version(
            String id,
            int seq,
            List<ArticleSnapshot> articles) {
        return new ContentVersionDocument(id, "law-1", seq, articles, "admin-1", NOW);
    }

    private static AnnotationVersionDocument annotation(
            String id,
            String lawId,
            String contentVersionId,
            Map<String, ArticleDraftValues> results) {
        return new AnnotationVersionDocument(
                id,
                lawId,
                2,
                contentVersionId,
                new OverallDraftValues("行政法", "行政,许可", "正式摘要", "正式备注"),
                results,
                "task-1",
                "submission-1",
                "reviewer-1",
                NOW.plusSeconds(30));
    }

    private static Map<String, ArticleDraftValues> articleResults() {
        Map<String, ArticleDraftValues> results = new LinkedHashMap<>();
        results.put("article-1", articleResult("一"));
        results.put("article-2", articleResult("二"));
        results.put("article-3", articleResult("三"));
        return Map.copyOf(results);
    }

    private static ArticleDraftValues articleResult(String suffix) {
        return new ArticleDraftValues(
                ItemType.RIGHTS_DUTIES,
                "关键词" + suffix,
                "主体" + suffix,
                "责任" + suffix,
                "备注" + suffix);
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
                        List.of("article-1")),
                new LawStructureNode(
                        "section-1",
                        LawStructureNodeType.SECTION,
                        "当前第一节",
                        "chapter-1",
                        1,
                        List.of("article-3")),
                new LawStructureNode(
                        "chapter-2",
                        LawStructureNodeType.CHAPTER,
                        "当前第二章",
                        null,
                        2,
                        List.of("article-2")));
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

    private static void assertExportError(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
