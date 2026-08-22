package com.law.annotation.field;

import com.law.annotation.auth.AuthErrorCodes;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.field.dto.FieldConfigItemResponse;
import com.law.annotation.field.dto.FieldConfigResponse;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class FieldConfigService {

    static final String SYSTEM_ACTOR = "SYSTEM";

    private static final List<FixedAnnotationField> FIXED_FIELDS =
            List.copyOf(Arrays.asList(FixedAnnotationField.values()));
    private static final List<String> FIXED_KEYS = FIXED_FIELDS.stream()
            .map(FixedAnnotationField::fieldKey)
            .toList();

    private final FieldConfigRepository fieldConfigRepository;

    public FieldConfigService(FieldConfigRepository fieldConfigRepository) {
        this.fieldConfigRepository = fieldConfigRepository;
    }

    public synchronized void initializeDefaults() {
        Map<String, FieldConfigDocument> existing = documentsByKey();
        Instant now = Instant.now();
        for (FixedAnnotationField field : FIXED_FIELDS) {
            FieldConfigDocument document = existing.get(field.fieldKey());
            if (document == null) {
                insertDefault(field, now);
                continue;
            }
            if (!field.configurable() && !document.isRequired()) {
                document.updateRequired(true, SYSTEM_ACTOR, now);
                fieldConfigRepository.save(document);
            }
        }
    }

    public FieldConfigResponse getCurrentConfig() {
        Map<String, Boolean> requiredByKey = currentRequiredByKey();
        List<FieldConfigItemResponse> fields = FIXED_FIELDS.stream()
                .map(field -> new FieldConfigItemResponse(
                        field.fieldKey(),
                        field.displayName(),
                        field.valueKind(),
                        field.scope(),
                        requiredByKey.get(field.fieldKey()),
                        field.configurable()))
                .toList();
        return new FieldConfigResponse(fields);
    }

    public FieldConfigResponse updateRequired(
            String fieldKey,
            Boolean required,
            String updatedBy,
            Role actorRole) {
        requireAdmin(actorRole);
        String validFieldKey = validateFieldKey(fieldKey);
        if (required == null) {
            throw validation("required", "必填设置不能为空");
        }
        String validUpdatedBy = requireActor(updatedBy);

        FixedAnnotationField field = FixedAnnotationField.findByKey(validFieldKey)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        FieldConfigErrorCodes.INVALID_FIELD_KEY,
                        "字段键无效",
                        List.of(new ErrorLocator("fieldKey", "字段不属于固定标注字段"))));
        if (!field.configurable()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    FieldConfigErrorCodes.CORE_REQUIRED_IMMUTABLE,
                    "核心字段必须保持必填",
                    List.of(new ErrorLocator("fieldKey", "核心字段的必填设置不可修改")));
        }

        initializeDefaults();
        FieldConfigDocument document = fieldConfigRepository.findByFieldKey(validFieldKey)
                .orElseThrow(() -> new IllegalStateException("固定字段配置初始化失败: " + validFieldKey));
        if (document.isRequired() != required) {
            document.updateRequired(required, validUpdatedBy, Instant.now());
            fieldConfigRepository.save(document);
        }
        return getCurrentConfig();
    }

    public FieldConfigSnapshot getCurrentSnapshot() {
        Map<String, Boolean> requiredByKey = currentRequiredByKey();
        List<FieldConfigSnapshotItem> overall = snapshotItems(
                FieldConfigScope.OVERALL, requiredByKey);
        List<FieldConfigSnapshotItem> article = snapshotItems(
                FieldConfigScope.ARTICLE, requiredByKey);
        return new FieldConfigSnapshot(overall, article);
    }

    private List<FieldConfigSnapshotItem> snapshotItems(
            FieldConfigScope scope,
            Map<String, Boolean> requiredByKey) {
        return FIXED_FIELDS.stream()
                .filter(field -> field.scope() == scope)
                .map(field -> new FieldConfigSnapshotItem(
                        field.fieldKey(), requiredByKey.get(field.fieldKey())))
                .toList();
    }

    private Map<String, Boolean> currentRequiredByKey() {
        initializeDefaults();
        Map<String, FieldConfigDocument> documents = documentsByKey();
        Map<String, Boolean> requiredByKey = new LinkedHashMap<>();
        for (FixedAnnotationField field : FIXED_FIELDS) {
            FieldConfigDocument document = documents.get(field.fieldKey());
            boolean required = field.configurable()
                    ? document != null && document.isRequired()
                    : true;
            requiredByKey.put(field.fieldKey(), required);
        }
        return Map.copyOf(requiredByKey);
    }

    private Map<String, FieldConfigDocument> documentsByKey() {
        return fieldConfigRepository.findAllByFieldKeyIn(FIXED_KEYS).stream()
                .collect(Collectors.toMap(
                        FieldConfigDocument::getFieldKey,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));
    }

    private void insertDefault(FixedAnnotationField field, Instant now) {
        try {
            fieldConfigRepository.insert(new FieldConfigDocument(
                    field.fieldKey(),
                    field.defaultRequired(),
                    SYSTEM_ACTOR,
                    now));
        } catch (DuplicateKeyException ignored) {
            // Another initializer completed the same idempotent insert.
        }
    }

    private static void requireAdmin(Role actorRole) {
        if (actorRole != Role.ADMIN) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    AuthErrorCodes.FORBIDDEN,
                    "无权执行此操作");
        }
    }

    private static String validateFieldKey(String fieldKey) {
        if (fieldKey == null || fieldKey.isBlank()) {
            throw validation("fieldKey", "字段键不能为空");
        }
        return fieldKey.trim();
    }

    private static String requireActor(String updatedBy) {
        Objects.requireNonNull(updatedBy, "updatedBy must not be null");
        if (updatedBy.isBlank()) {
            throw new IllegalArgumentException("updatedBy must not be blank");
        }
        return updatedBy;
    }

    private static ApiException validation(String path, String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "COMMON.VALIDATION_FAILED",
                "请求参数校验失败",
                List.of(new ErrorLocator(path, message)));
    }
}
