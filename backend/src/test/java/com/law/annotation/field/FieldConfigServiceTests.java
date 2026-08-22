package com.law.annotation.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.law.annotation.auth.AuthErrorCodes;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.field.dto.FieldConfigItemResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

class FieldConfigServiceTests {

    private final Map<String, FieldConfigDocument> documents = new LinkedHashMap<>();
    private FieldConfigRepository repository;
    private FieldConfigService service;

    @BeforeEach
    void setUp() {
        documents.clear();
        repository = Mockito.mock(FieldConfigRepository.class);
        when(repository.findAllByFieldKeyIn(any())).thenAnswer(ignored -> new ArrayList<>(documents.values()));
        when(repository.findByFieldKey(any())).thenAnswer(invocation ->
                Optional.ofNullable(documents.get(invocation.getArgument(0, String.class))));
        when(repository.insert(any(FieldConfigDocument.class))).thenAnswer(invocation -> {
            FieldConfigDocument document = invocation.getArgument(0, FieldConfigDocument.class);
            documents.put(document.getFieldKey(), document);
            return document;
        });
        when(repository.save(any(FieldConfigDocument.class))).thenAnswer(invocation -> {
            FieldConfigDocument document = invocation.getArgument(0, FieldConfigDocument.class);
            documents.put(document.getFieldKey(), document);
            return document;
        });
        service = new FieldConfigService(repository);
    }

    @Test
    void initializesNineFixedFieldsWithV15DefaultsAndOrder() {
        List<FieldConfigItemResponse> fields = service.getCurrentConfig().fields();

        assertThat(documents).hasSize(9);
        assertThat(fields)
                .extracting(FieldConfigItemResponse::fieldKey)
                .containsExactly(
                        "lawCategory",
                        "overallKeywords",
                        "summary",
                        "overallNote",
                        "itemType",
                        "keywords",
                        "subjects",
                        "legalLiability",
                        "annotationNote");
        assertThat(required("lawCategory")).isTrue();
        assertThat(required("overallKeywords")).isTrue();
        assertThat(required("itemType")).isTrue();
        assertThat(required("keywords")).isTrue();
        assertThat(required("summary")).isFalse();
        assertThat(required("overallNote")).isFalse();
        assertThat(required("subjects")).isFalse();
        assertThat(required("legalLiability")).isFalse();
        assertThat(required("annotationNote")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"lawCategory", "overallKeywords", "itemType", "keywords"})
    void rejectsChangesToCoreRequiredFields(String fieldKey) {
        assertThatThrownBy(() -> service.updateRequired(fieldKey, false, "admin", Role.ADMIN))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(FieldConfigErrorCodes.CORE_REQUIRED_IMMUTABLE);
    }

    @Test
    void updatesSummaryAndSubjectsRequiredFlags() {
        service.getCurrentConfig();

        service.updateRequired("summary", true, "admin", Role.ADMIN);
        service.updateRequired("subjects", true, "admin", Role.ADMIN);

        assertThat(required("summary")).isTrue();
        assertThat(required("subjects")).isTrue();
        assertThat(documents.get("summary").getUpdatedBy()).isEqualTo("admin");
        assertThat(documents.get("subjects").getUpdatedBy()).isEqualTo("admin");
    }

    @Test
    void rejectsUnknownFieldKey() {
        assertThatThrownBy(() -> service.updateRequired("customField", true, "admin", Role.ADMIN))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(FieldConfigErrorCodes.INVALID_FIELD_KEY);
    }

    @Test
    void serviceRejectsAnnotatorBeforeAccessingPersistence() {
        assertThatThrownBy(() -> service.updateRequired("summary", true, "annotator", Role.ANNOTATOR))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCodes.FORBIDDEN);
        verifyNoInteractions(repository);
    }

    @Test
    void oldSnapshotRemainsImmutableAfterConfigurationChanges() {
        FieldConfigSnapshot oldSnapshot = service.getCurrentSnapshot();

        service.updateRequired("summary", true, "admin", Role.ADMIN);
        FieldConfigSnapshot newSnapshot = service.getCurrentSnapshot();

        assertThat(snapshotRequired(oldSnapshot, "summary")).isFalse();
        assertThat(snapshotRequired(newSnapshot, "summary")).isTrue();
        assertThat(oldSnapshot.overall())
                .extracting(FieldConfigSnapshotItem::fieldKey)
                .containsExactly("lawCategory", "overallKeywords", "summary", "overallNote");
        assertThat(oldSnapshot.article())
                .extracting(FieldConfigSnapshotItem::fieldKey)
                .containsExactly("itemType", "keywords", "subjects", "legalLiability", "annotationNote");
        assertThatThrownBy(() -> oldSnapshot.overall().add(
                new FieldConfigSnapshotItem("illegal", true)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void fixedSelectValuesMatchV15Contract() {
        assertThat(FixedAnnotationField.LAW_CATEGORY.allowedValues())
                .containsExactly("民事", "刑事", "行政", "商事经济", "劳动社保", "其他");
        assertThat(FixedAnnotationField.ITEM_TYPE.allowedValues())
                .containsExactly(
                        "DEFINITION",
                        "RIGHTS_DUTIES",
                        "AUTHORITY_DUTY",
                        "PROHIBITION_RESTRICTION",
                        "PROCEDURE",
                        "LIABILITY",
                        "OTHER");
    }

    private boolean required(String fieldKey) {
        return documents.get(fieldKey).isRequired();
    }

    private static boolean snapshotRequired(FieldConfigSnapshot snapshot, String fieldKey) {
        return snapshot.overall().stream()
                .filter(item -> item.fieldKey().equals(fieldKey))
                .findFirst()
                .orElseThrow()
                .required();
    }
}
