package com.law.annotation.export.formatter;

import static org.assertj.core.api.Assertions.assertThat;

import com.law.annotation.common.enums.ItemType;
import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.export.dto.FormalLawExport;
import com.law.annotation.export.dto.PlainLawExport;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvFormatterTests {

    @Test
    void plainCsvNeutralizesFormulaPrefixesWithAndWithoutLeadingWhitespace() {
        PlainLawExport export = new PlainLawExport(
                new PlainLawExport.LawInfo(
                        "=SUM(A1:A2)",
                        "+CMD",
                        "-CMD",
                        LocalDate.of(2026, 8, 27),
                        ValidityStatus.ACTIVE,
                        "@CMD",
                        1),
                List.of(),
                List.of(
                        new PlainLawExport.Article(
                                " =SUM(A1:A2)",
                                " +CMD",
                                " -CMD",
                                0,
                                List.of(" @CMD")),
                        new PlainLawExport.Article(
                                "1+1",
                                "2026-08-27",
                                "abc@example.com",
                                1,
                                List.of("普通正文"))));

        String csv = new String(
                new PlainExportCsvFormatter().format(export),
                StandardCharsets.UTF_8);

        assertThat(csv).contains(
                "'=SUM(A1:A2)",
                "'+CMD",
                "'-CMD",
                "'@CMD",
                "' =SUM(A1:A2)",
                "' +CMD",
                "' -CMD",
                "' @CMD");
        assertThat(csv)
                .contains(",1+1,2026-08-27,abc@example.com,1,普通正文\r\n")
                .doesNotContain("'1+1", "'2026-08-27", "'abc@example.com", "'普通正文");
    }

    @Test
    void formalCsvNeutralizesFormulaPrefixesWithAndWithoutLeadingWhitespace() {
        FormalLawExport export = new FormalLawExport(
                new FormalLawExport.LawInfo(
                        "=SUM(A1:A2)",
                        "+CMD",
                        "-CMD",
                        LocalDate.of(2026, 8, 27),
                        ValidityStatus.ACTIVE),
                new FormalLawExport.SemanticVersion("@CMD", 2),
                new FormalLawExport.AnnotationVersion("annotation-2", 2),
                new FormalLawExport.OverallAnnotation(
                        " =SUM(A1:A2)",
                        " +CMD",
                        " -CMD",
                        " @CMD"),
                List.of(),
                List.of(new FormalLawExport.Article(
                        "1+1",
                        List.of("普通章节"),
                        "2026-08-27",
                        "abc@example.com",
                        0,
                        ItemType.OTHER,
                        "普通关键词",
                        "普通主体",
                        "正常-文本",
                        "普通正文")),
                new FormalLawExport.ApprovalMetadata(
                        "reviewer@example.com",
                        Instant.parse("2026-08-27T00:00:00Z"),
                        "task-1"));

        String csv = new String(
                new FormalExportCsvFormatter().format(export),
                StandardCharsets.UTF_8);

        assertThat(csv).contains(
                "'=SUM(A1:A2)",
                "'+CMD",
                "'-CMD",
                "'@CMD",
                "' =SUM(A1:A2)",
                "' +CMD",
                "' -CMD",
                "' @CMD");
        assertThat(csv).contains(
                ",1+1,普通章节,2026-08-27,abc@example.com,0,",
                "普通关键词",
                "reviewer@example.com")
                .doesNotContain(
                        "'1+1",
                        "'2026-08-27",
                        "'abc@example.com",
                        "'普通正文",
                        "'正常-文本");
    }

    @Test
    void formulaNeutralizationPrecedesStandardCsvEscaping() {
        assertThat(CsvCellEscaper.escape("=SUM(A1,A2)\"\r\n"))
                .isEqualTo("\"'=SUM(A1,A2)\"\"\r\n\"");
    }
}
