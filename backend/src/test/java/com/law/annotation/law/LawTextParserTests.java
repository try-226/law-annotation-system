package com.law.annotation.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.law.annotation.common.exception.ApiException;
import com.law.annotation.law.dto.LawImportPreviewResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LawTextParserTests {

    private final LawTextParser parser = new LawTextParser();

    @Test
    void parsesMetadataStructureAndMultipleArticlesWithoutPersistenceDependencies() {
        LawImportPreviewResponse preview = parser.parse("""
                法律名称：中华人民共和国测试法
                发布机关：全国人民代表大会
                发布日期：2026年8月19日

                第一章 总则
                第一条 第一行正文
                第二行正文
                第二条 第二条正文
                """);

        assertThat(preview.baseInfo().name()).isEqualTo("中华人民共和国测试法");
        assertThat(preview.baseInfo().issuingAuthority()).isEqualTo("全国人民代表大会");
        assertThat(preview.baseInfo().publicationDate()).isEqualTo(LocalDate.of(2026, 8, 19));
        assertThat(preview.baseInfo().validityStatus()).isNull();
        assertThat(preview.structure()).hasSize(1);
        assertThat(preview.structure().getFirst().title()).isEqualTo("第一章 总则");
        assertThat(preview.structure().getFirst().articleRefs())
                .containsExactly("article-1", "article-2");
        assertThat(preview.articles()).extracting(article -> article.number())
                .containsExactly("第一条", "第二条");
        assertThat(preview.articles().getFirst().body()).isEqualTo("第一行正文\n第二行正文");
        assertThat(preview.validationIssues()).isEmpty();
    }

    @Test
    void reportsDuplicateArticleNumberInPreview() {
        LawImportPreviewResponse preview = parser.parse("""
                测试法
                第一条 正文一
                第一条 正文二
                """);

        assertThat(preview.validationIssues())
                .extracting(issue -> issue.code())
                .contains("IMPORT.DUPLICATE_ARTICLE_NUMBER");
    }

    @Test
    void reportsInvalidNumberAndEmptyBodyInPreview() {
        LawImportPreviewResponse preview = parser.parse("""
                测试法
                第一条之一
                第二条 合法正文
                """);

        assertThat(preview.validationIssues())
                .extracting(issue -> issue.code())
                .contains("IMPORT.INVALID_ARTICLE_NUMBER", "IMPORT.INVALID_ARTICLE_BODY");
    }

    @Test
    void leavesUnreliablyRecognizedMetadataEmpty() {
        LawImportPreviewResponse preview = parser.parse("""
                这是一段无法可靠识别的说明
                第一条 正文
                """);

        assertThat(preview.baseInfo().name()).isNull();
        assertThat(preview.baseInfo().issuingAuthority()).isNull();
        assertThat(preview.baseInfo().publicationDate()).isNull();
        assertThat(preview.validationIssues())
                .extracting(issue -> issue.code())
                .contains(
                        "IMPORT.MISSING_NAME",
                        "IMPORT.MISSING_ISSUING_AUTHORITY",
                        "IMPORT.MISSING_PUBLICATION_DATE");
    }

    @Test
    void parsesPartChapterAndSectionHierarchy() {
        LawImportPreviewResponse preview = parser.parse("""
                测试法
                第一编 总编
                第一章 总则
                第一节 范围
                第一条 正文
                """);

        assertThat(preview.structure()).hasSize(3);
        assertThat(preview.structure().get(1).parentNodeId()).isEqualTo("structure-1");
        assertThat(preview.structure().get(2).parentNodeId()).isEqualTo("structure-2");
        assertThat(preview.structure().get(2).articleRefs()).containsExactly("article-1");
    }

    @Test
    void rejectsBlankText() {
        assertThatThrownBy(() -> parser.parse("   \n"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.IMPORT_TEXT_INVALID);
    }

    @Test
    void rejectsTextOverFiveHundredThousandCodePoints() {
        assertThatThrownBy(() -> parser.parse("法".repeat(500_001)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.IMPORT_TEXT_INVALID);
    }

    @Test
    void rejectsClearlyMultipleLaws() {
        assertThatThrownBy(() -> parser.parse("""
                        测试法
                        第一条 正文
                        另一测试法
                        第一条 正文
                        """))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.MULTIPLE_LAWS_DETECTED);
    }

    @Test
    void rejectsTextWithoutArticleCandidates() {
        assertThatThrownBy(() -> parser.parse("测试法\n这里只是说明，没有法条"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.NO_ARTICLES_DETECTED);
    }
}
