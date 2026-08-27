package com.law.annotation.export.formatter;

import com.law.annotation.export.dto.FormalLawExport;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FormalExportCsvFormatter {

    private static final String LINE_ENDING = "\r\n";
    private static final List<String> HEADER = List.of(
            "lawId",
            "lawName",
            "issuingAuthority",
            "publicationDate",
            "validityStatus",
            "contentVersionId",
            "contentVersionSeq",
            "annotationVersionId",
            "annotationVersionSeq",
            "articleId",
            "structurePath",
            "articleNumber",
            "articleBody",
            "articleOrder",
            "lawCategory",
            "overallKeywords",
            "summary",
            "overallNote",
            "itemType",
            "keywords",
            "subjects",
            "legalLiability",
            "annotationNote",
            "approvedBy",
            "approvedAt",
            "sourceTaskId");

    public byte[] format(FormalLawExport export) {
        StringBuilder csv = new StringBuilder();
        appendRow(csv, HEADER);
        for (FormalLawExport.Article article : export.articles()) {
            appendRow(csv, Arrays.asList(
                    export.law().lawId(),
                    export.law().name(),
                    export.law().issuingAuthority(),
                    export.law().publicationDate().toString(),
                    export.law().validityStatus().name(),
                    export.semanticVersion().contentVersionId(),
                    Integer.toString(export.semanticVersion().contentVersionSeq()),
                    export.annotationVersion().annotationVersionId(),
                    Integer.toString(export.annotationVersion().annotationVersionSeq()),
                    article.articleId(),
                    String.join(" / ", article.structurePath()),
                    article.number(),
                    article.body(),
                    Integer.toString(article.order()),
                    export.overallAnnotation().lawCategory(),
                    export.overallAnnotation().overallKeywords(),
                    export.overallAnnotation().summary(),
                    export.overallAnnotation().overallNote(),
                    article.itemType() == null ? null : article.itemType().name(),
                    article.keywords(),
                    article.subjects(),
                    article.legalLiability(),
                    article.annotationNote(),
                    export.approvalMetadata().approvedBy(),
                    export.approvalMetadata().approvedAt() == null
                            ? null
                            : export.approvalMetadata().approvedAt().toString(),
                    export.approvalMetadata().sourceTaskId()));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder csv, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append(escape(values.get(index)));
        }
        csv.append(LINE_ENDING);
    }

    private static String escape(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf(',') >= 0
                || text.indexOf('"') >= 0
                || text.indexOf('\r') >= 0
                || text.indexOf('\n') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
