package com.law.annotation.export.formatter;

import com.law.annotation.export.dto.PlainLawExport;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlainExportCsvFormatter {

    private static final String LINE_ENDING = "\r\n";
    private static final List<String> HEADER = List.of(
            "lawId",
            "lawName",
            "issuingAuthority",
            "publicationDate",
            "validityStatus",
            "contentVersionId",
            "contentVersionSeq",
            "articleId",
            "articleNumber",
            "articleBody",
            "articleOrder",
            "structurePath");

    public byte[] format(PlainLawExport export) {
        StringBuilder csv = new StringBuilder();
        appendRow(csv, HEADER);
        PlainLawExport.LawInfo law = export.law();
        for (PlainLawExport.Article article : export.articles()) {
            appendRow(csv, List.of(
                    law.lawId(),
                    law.name(),
                    law.issuingAuthority(),
                    law.publicationDate().toString(),
                    law.validityStatus().name(),
                    law.currentContentVersionId(),
                    Integer.toString(law.currentContentVersionSeq()),
                    article.articleId(),
                    article.number(),
                    article.body(),
                    Integer.toString(article.order()),
                    String.join(" / ", article.structurePath())));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder csv, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append(CsvCellEscaper.escape(values.get(index)));
        }
        csv.append(LINE_ENDING);
    }
}
