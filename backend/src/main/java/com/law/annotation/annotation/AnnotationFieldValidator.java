package com.law.annotation.annotation;

import com.law.annotation.annotation.dto.SaveArticleDraftRequest;
import com.law.annotation.annotation.dto.SaveOverallDraftRequest;
import com.law.annotation.common.enums.ItemType;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.field.FixedAnnotationField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class AnnotationFieldValidator {

    private static final int MAX_KEYWORDS_LENGTH = 200;
    private static final int MAX_KEYWORD_COUNT = 20;
    private static final int MAX_KEYWORD_LENGTH = 30;

    private AnnotationFieldValidator() {
    }

    static ValidationResult<OverallAnnotationFields> validateOverall(
            SaveOverallDraftRequest request,
            String pathPrefix) {
        List<ErrorLocator> errors = new ArrayList<>();
        String lawCategory = normalizeSelect(
                request.lawCategory(),
                FixedAnnotationField.LAW_CATEGORY.allowedValues(),
                path(pathPrefix, "lawCategory"),
                errors);
        String overallKeywords = normalizeKeywords(
                request.overallKeywords(),
                path(pathPrefix, "overallKeywords"),
                errors);
        String summary = normalizeText(
                request.summary(), 2000, path(pathPrefix, "summary"), errors);
        String overallNote = normalizeText(
                request.overallNote(), 1000, path(pathPrefix, "overallNote"), errors);
        return new ValidationResult<>(
                new OverallAnnotationFields(
                        lawCategory, overallKeywords, summary, overallNote),
                errors);
    }

    static ValidationResult<ArticleAnnotationFields> validateArticle(
            SaveArticleDraftRequest request,
            String pathPrefix) {
        List<ErrorLocator> errors = new ArrayList<>();
        ItemType itemType = normalizeItemType(
                request.itemType(), path(pathPrefix, "itemType"), errors);
        String keywords = normalizeKeywords(
                request.keywords(), path(pathPrefix, "keywords"), errors);
        String subjects = normalizeText(
                request.subjects(), 200, path(pathPrefix, "subjects"), errors);
        String legalLiability = normalizeText(
                request.legalLiability(), 1000, path(pathPrefix, "legalLiability"), errors);
        String annotationNote = normalizeText(
                request.annotationNote(), 1000, path(pathPrefix, "annotationNote"), errors);
        return new ValidationResult<>(
                new ArticleAnnotationFields(
                        itemType, keywords, subjects, legalLiability, annotationNote),
                errors);
    }

    static ValidationResult<OverallAnnotationFields> validateStoredOverall(
            OverallAnnotationFields fields,
            String pathPrefix) {
        if (fields == null) {
            return new ValidationResult<>(null, List.of());
        }
        return validateOverall(new SaveOverallDraftRequest(
                fields.lawCategory(),
                fields.overallKeywords(),
                fields.summary(),
                fields.overallNote()), pathPrefix);
    }

    static ValidationResult<ArticleAnnotationFields> validateStoredArticle(
            ArticleAnnotationFields fields,
            String pathPrefix) {
        if (fields == null) {
            return new ValidationResult<>(null, List.of());
        }
        return validateArticle(new SaveArticleDraftRequest(
                fields.itemType() == null ? null : fields.itemType().name(),
                fields.keywords(),
                fields.subjects(),
                fields.legalLiability(),
                fields.annotationNote()), pathPrefix);
    }

    private static String normalizeSelect(
            String value,
            List<String> allowedValues,
            String path,
            List<ErrorLocator> errors) {
        String normalized = trimToNull(value);
        if (normalized != null && !allowedValues.contains(normalized)) {
            errors.add(new ErrorLocator(path, "值不在允许范围内"));
        }
        return normalized;
    }

    private static ItemType normalizeItemType(
            String value,
            String path,
            List<ErrorLocator> errors) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return ItemType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            errors.add(new ErrorLocator(path, "值不在允许范围内"));
            return null;
        }
    }

    private static String normalizeKeywords(
            String value,
            String path,
            List<ErrorLocator> errors) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (codePointLength(normalized) > MAX_KEYWORDS_LENGTH) {
            errors.add(new ErrorLocator(path, "总长度不能超过200个字符"));
        }
        String[] rawTokens = normalized.replace('，', ',').split(",", -1);
        if (rawTokens.length > MAX_KEYWORD_COUNT) {
            errors.add(new ErrorLocator(path, "关键词不能超过20个"));
        }
        List<String> tokens = Arrays.stream(rawTokens)
                .map(String::trim)
                .toList();
        if (tokens.stream().anyMatch(String::isEmpty)) {
            errors.add(new ErrorLocator(path, "关键词不能包含空项"));
        }
        if (tokens.stream().anyMatch(token -> codePointLength(token) > MAX_KEYWORD_LENGTH)) {
            errors.add(new ErrorLocator(path, "单个关键词不能超过30个字符"));
        }
        return String.join(",", tokens);
    }

    private static String normalizeText(
            String value,
            int maxLength,
            String path,
            List<ErrorLocator> errors) {
        String normalized = trimToNull(value);
        if (normalized != null && codePointLength(normalized) > maxLength) {
            errors.add(new ErrorLocator(path, "长度不能超过" + maxLength + "个字符"));
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private static String path(String prefix, String field) {
        return prefix == null || prefix.isBlank() ? field : prefix + "." + field;
    }

    record ValidationResult<T>(T value, List<ErrorLocator> errors) {

        ValidationResult {
            errors = List.copyOf(errors);
        }
    }
}
