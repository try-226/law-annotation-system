package com.law.annotation.law;

import com.law.annotation.common.enums.ValidityStatus;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

public final class LawDomainRules {

    private static final Pattern ARTICLE_NUMBER_PATTERN = Pattern.compile(
            "^第(?:[零〇一二三四五六七八九十百千万两]+|[1-9]\\d*)条$");
    private static final Pattern LEADING_BLANK_LINES = Pattern.compile(
            "\\A(?:(?:\\h)*(?:\\R))+");
    private static final Pattern TRAILING_BLANK_LINES = Pattern.compile(
            "(?:(?:\\R)(?:\\h)*)+\\z");

    private LawDomainRules() {
    }

    public static String validateLawName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("法律名称不能为空");
        }
        boolean containsControl = value.codePoints().anyMatch(Character::isISOControl);
        if (containsControl) {
            throw new IllegalArgumentException("法律名称不得包含换行或控制字符");
        }
        String trimmed = value.trim();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length < 1 || length > 100) {
            throw new IllegalArgumentException("法律名称须为1至100个字符，且不得包含换行或控制字符");
        }
        return trimmed;
    }

    public static String normalizeLawName(String value) {
        return validateLawName(value).toLowerCase(Locale.ROOT);
    }

    public static String validateIssuingAuthority(String value) {
        if (value == null) {
            throw new IllegalArgumentException("制定机关不能为空");
        }
        String trimmed = value.trim();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length < 1 || length > 100) {
            throw new IllegalArgumentException("制定机关须为1至100个字符");
        }
        return trimmed;
    }

    public static LocalDate requirePublicationDate(LocalDate value) {
        if (value == null) {
            throw new IllegalArgumentException("公布日期不能为空");
        }
        return value;
    }

    public static ValidityStatus requireValidityStatus(ValidityStatus value) {
        if (value == null) {
            throw new IllegalArgumentException("效力状态不能为空");
        }
        return value;
    }

    public static String validateArticleNumber(String value) {
        if (value == null) {
            throw new IllegalArgumentException("条号不能为空");
        }
        int length = value.codePointCount(0, value.length());
        if (length < 1 || length > 20 || !ARTICLE_NUMBER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("条号格式不合法");
        }
        return value;
    }

    public static String validateArticleBody(String value) {
        if (value == null) {
            throw new IllegalArgumentException("条文正文不能为空");
        }
        String withoutLeadingBlankLines = LEADING_BLANK_LINES.matcher(value).replaceFirst("");
        String normalized = TRAILING_BLANK_LINES.matcher(withoutLeadingBlankLines).replaceFirst("");
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > 20_000 || normalized.isBlank()) {
            throw new IllegalArgumentException("条文正文须为1至20000个字符");
        }
        return normalized;
    }

    public static String requireIdentifier(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}
