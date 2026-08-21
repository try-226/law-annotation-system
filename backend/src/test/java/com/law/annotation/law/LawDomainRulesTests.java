package com.law.annotation.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.law.annotation.version.ContentVersionDocument;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LawDomainRulesTests {

    @Test
    void normalizesLawNameUsingOnlyTrimAndRootLowercase() {
        assertThat(LawDomainRules.validateLawName(" Test  Law ")).isEqualTo("Test  Law");
        assertThat(LawDomainRules.normalizeLawName(" Test  Law ")).isEqualTo("test  law");
        assertThat(LawDomainRules.normalizeLawName(" 测试 法 ")).isEqualTo("测试 法");
        assertThat(LawDomainRules.normalizeLawName(" 测试法 ")).isEqualTo("测试法");
    }

    @Test
    void validatesLawNameAndArticleNumberContracts() {
        assertThatThrownBy(() -> LawDomainRules.validateLawName("法律\n名称"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LawDomainRules.validateLawName("法律名称\n"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LawDomainRules.validateLawName("\t法律名称"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LawDomainRules.validateArticleNumber("第 102 条"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LawDomainRules.validateArticleNumber("第一百零二条之一"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(LawDomainRules.validateArticleNumber("第一百零二条"))
                .isEqualTo("第一百零二条");
        assertThat(LawDomainRules.validateArticleNumber("第102条")).isEqualTo("第102条");
    }

    @Test
    void removesOnlySurroundingBlankLinesFromArticleBody() {
        String body = LawDomainRules.validateArticleBody("\n  第一款  \n\n  第二款  \n");

        assertThat(body).isEqualTo("  第一款  \n\n  第二款  ");
    }

    @Test
    void rejectsDuplicateNumberInsideOneSemanticSnapshot() {
        ArticleSnapshot first = ArticleSnapshot.createNew("第一条", "正文一", 0);
        ArticleSnapshot duplicate = ArticleSnapshot.createNew("第一条", "正文二", 1);

        assertThatThrownBy(() -> new ContentVersionDocument(
                        "c1", "law-1", 1, List.of(first, duplicate), "user-1", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("条号不能重复");
    }

    @Test
    void pendingChangeCategoriesAreDisjoint() {
        assertThatThrownBy(() -> new PendingChangeSet(
                        Set.of("a1"), Set.of("a1"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pendingRevisionRequiresAFormalAnnotationVersion() {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");

        assertThatThrownBy(() -> new LawDocument(
                        "law-1",
                        "法律名称",
                        "法律名称",
                        "制定机关",
                        java.time.LocalDate.of(2026, 8, 19),
                        com.law.annotation.common.enums.ValidityStatus.ACTIVE,
                        List.of(),
                        null,
                        "c2",
                        null,
                        true,
                        new PendingChangeSet(Set.of(), Set.of("a1"), Set.of()),
                        now,
                        now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("正式标注版本");
    }
}
