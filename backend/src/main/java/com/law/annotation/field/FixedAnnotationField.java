package com.law.annotation.field;

import com.law.annotation.common.enums.ItemType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum FixedAnnotationField {
    LAW_CATEGORY(
            "lawCategory",
            "法律类别",
            FieldValueKind.SELECT,
            FieldConfigScope.OVERALL,
            true,
            false,
            List.of("民事", "刑事", "行政", "商事经济", "劳动社保", "其他")),
    OVERALL_KEYWORDS(
            "overallKeywords",
            "整体关键词",
            FieldValueKind.TEXT,
            FieldConfigScope.OVERALL,
            true,
            false,
            List.of()),
    SUMMARY(
            "summary",
            "摘要",
            FieldValueKind.TEXTAREA,
            FieldConfigScope.OVERALL,
            false,
            true,
            List.of()),
    OVERALL_NOTE(
            "overallNote",
            "备注",
            FieldValueKind.TEXTAREA,
            FieldConfigScope.OVERALL,
            false,
            true,
            List.of()),
    ITEM_TYPE(
            "itemType",
            "条目类型",
            FieldValueKind.SELECT,
            FieldConfigScope.ARTICLE,
            true,
            false,
            Arrays.stream(ItemType.values()).map(Enum::name).toList()),
    KEYWORDS(
            "keywords",
            "关键词",
            FieldValueKind.TEXT,
            FieldConfigScope.ARTICLE,
            true,
            false,
            List.of()),
    SUBJECTS(
            "subjects",
            "涉及主体",
            FieldValueKind.TEXT,
            FieldConfigScope.ARTICLE,
            false,
            true,
            List.of()),
    LEGAL_LIABILITY(
            "legalLiability",
            "法律责任",
            FieldValueKind.TEXTAREA,
            FieldConfigScope.ARTICLE,
            false,
            true,
            List.of()),
    ANNOTATION_NOTE(
            "annotationNote",
            "标注备注",
            FieldValueKind.TEXTAREA,
            FieldConfigScope.ARTICLE,
            false,
            true,
            List.of());

    private final String fieldKey;
    private final String displayName;
    private final FieldValueKind valueKind;
    private final FieldConfigScope scope;
    private final boolean defaultRequired;
    private final boolean configurable;
    private final List<String> allowedValues;

    FixedAnnotationField(
            String fieldKey,
            String displayName,
            FieldValueKind valueKind,
            FieldConfigScope scope,
            boolean defaultRequired,
            boolean configurable,
            List<String> allowedValues) {
        this.fieldKey = fieldKey;
        this.displayName = displayName;
        this.valueKind = valueKind;
        this.scope = scope;
        this.defaultRequired = defaultRequired;
        this.configurable = configurable;
        this.allowedValues = List.copyOf(allowedValues);
    }

    public String fieldKey() {
        return fieldKey;
    }

    public String displayName() {
        return displayName;
    }

    public FieldValueKind valueKind() {
        return valueKind;
    }

    public FieldConfigScope scope() {
        return scope;
    }

    public boolean defaultRequired() {
        return defaultRequired;
    }

    public boolean configurable() {
        return configurable;
    }

    public List<String> allowedValues() {
        return allowedValues;
    }

    public static Optional<FixedAnnotationField> findByKey(String fieldKey) {
        return Arrays.stream(values())
                .filter(field -> field.fieldKey.equals(fieldKey))
                .findFirst();
    }
}
