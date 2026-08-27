package com.law.annotation.export.formatter;

final class CsvCellEscaper {

    private CsvCellEscaper() {
    }

    static String escape(String value) {
        String text = neutralizeFormula(value == null ? "" : value);
        if (text.indexOf(',') >= 0
                || text.indexOf('"') >= 0
                || text.indexOf('\r') >= 0
                || text.indexOf('\n') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    private static String neutralizeFormula(String value) {
        int firstNonWhitespace = 0;
        while (firstNonWhitespace < value.length()
                && (Character.isWhitespace(value.charAt(firstNonWhitespace))
                        || Character.isSpaceChar(value.charAt(firstNonWhitespace)))) {
            firstNonWhitespace++;
        }
        if (firstNonWhitespace < value.length()
                && isFormulaPrefix(value.charAt(firstNonWhitespace))) {
            return "'" + value;
        }
        return value;
    }

    private static boolean isFormulaPrefix(char value) {
        return value == '=' || value == '+' || value == '-' || value == '@';
    }
}
