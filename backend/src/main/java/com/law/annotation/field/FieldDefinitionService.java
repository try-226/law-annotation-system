package com.law.annotation.field;

import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.field.dto.CreateFieldDefinitionRequest;
import com.law.annotation.field.dto.FieldDefinitionResponse;
import com.law.annotation.field.dto.UpdateFieldDefinitionRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class FieldDefinitionService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_OPTIONS = 100;
    private static final int MAX_OPTION_LENGTH = 100;

    private final FieldDefinitionRepository repository;

    public FieldDefinitionService(FieldDefinitionRepository repository) {
        this.repository = repository;
    }

    public PageResponse<FieldDefinitionResponse> list(int page, int size) {
        if (page < 0) {
            throw validation("page", "页码不能小于0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw validation("size", "每页数量须为1至100");
        }
        Page<FieldDefinitionDocument> result = repository.findAll(PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new PageResponse<>(
                result.getContent().stream().map(FieldDefinitionResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    public FieldDefinitionResponse create(CreateFieldDefinitionRequest request) {
        String name = requireText(request.name(), "name", "系统字段名称", MAX_NAME_LENGTH);
        String displayName = requireText(
                request.displayName(), "displayName", "显示名称", MAX_DISPLAY_NAME_LENGTH);
        String description = optionalText(request.description(), "description", "描述", MAX_DESCRIPTION_LENGTH);
        if (request.fieldType() == null) {
            throw validation("fieldType", "字段类型不能为空");
        }
        List<String> options = normalizeOptions(request.fieldType(), request.options());
        if (repository.existsByName(name)) {
            throw nameConflict();
        }

        Instant now = Instant.now();
        FieldDefinitionDocument document = new FieldDefinitionDocument(
                name,
                displayName,
                description,
                request.fieldType(),
                request.required(),
                options,
                FieldDefinitionStatus.ACTIVE,
                now,
                now);
        try {
            return FieldDefinitionResponse.from(repository.insert(document));
        } catch (DuplicateKeyException exception) {
            throw nameConflict();
        }
    }

    public FieldDefinitionResponse get(String id) {
        return FieldDefinitionResponse.from(requireDocument(id));
    }

    public FieldDefinitionResponse update(String id, UpdateFieldDefinitionRequest request) {
        FieldDefinitionDocument document = requireDocument(id);
        String displayName = requireText(
                request.displayName(), "displayName", "显示名称", MAX_DISPLAY_NAME_LENGTH);
        String description = optionalText(request.description(), "description", "描述", MAX_DESCRIPTION_LENGTH);
        if (request.status() == null) {
            throw validation("status", "字段状态不能为空");
        }
        List<String> options = normalizeOptions(document.getFieldType(), request.options());
        document.update(
                displayName,
                description,
                request.required(),
                options,
                request.status(),
                Instant.now());
        return FieldDefinitionResponse.from(repository.save(document));
    }

    public FieldDefinitionResponse deactivate(String id) {
        FieldDefinitionDocument document = requireDocument(id);
        if (document.getStatus() != FieldDefinitionStatus.INACTIVE) {
            document.deactivate(Instant.now());
            document = repository.save(document);
        }
        return FieldDefinitionResponse.from(document);
    }

    private FieldDefinitionDocument requireDocument(String id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                FieldDefinitionErrorCodes.NOT_FOUND,
                "字段配置不存在"));
    }

    private static List<String> normalizeOptions(FieldType fieldType, List<String> values) {
        List<String> source = values == null ? List.of() : values;
        if (source.size() > MAX_OPTIONS) {
            throw validation("options", "选项数量不能超过100个");
        }
        List<String> normalized = new ArrayList<>(source.size());
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < source.size(); index++) {
            String option = requireText(
                    source.get(index),
                    "options[" + index + "]",
                    "选项",
                    MAX_OPTION_LENGTH);
            if (!seen.add(option)) {
                throw validation("options[" + index + "]", "选项不能重复");
            }
            normalized.add(option);
        }
        boolean selectable = fieldType == FieldType.SELECT || fieldType == FieldType.MULTI_SELECT;
        if (!selectable && !normalized.isEmpty()) {
            throw validation("options", "只有单选或多选字段可以配置选项");
        }
        return List.copyOf(normalized);
    }

    private static String requireText(
            String value,
            String path,
            String label,
            int maxLength) {
        if (value == null) {
            throw validation(path, label + "不能为空");
        }
        String trimmed = value.trim();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length < 1 || length > maxLength || containsControl(trimmed)) {
            throw validation(path, label + "须为1至" + maxLength + "个字符且不得包含控制字符");
        }
        return trimmed;
    }

    private static String optionalText(
            String value,
            String path,
            String label,
            int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length > maxLength || containsDisallowedDescriptionControl(trimmed)) {
            throw validation(path, label + "不能超过" + maxLength + "个字符且不得包含控制字符");
        }
        return trimmed;
    }

    private static boolean containsControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private static boolean containsDisallowedDescriptionControl(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
                && codePoint != '\n'
                && codePoint != '\r'
                && codePoint != '\t');
    }

    private static ApiException nameConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                FieldDefinitionErrorCodes.NAME_ALREADY_EXISTS,
                "系统字段名称已存在");
    }

    private static ApiException validation(String path, String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                FieldDefinitionErrorCodes.VALIDATION_FAILED,
                "字段配置校验失败",
                List.of(new ErrorLocator(path, message)));
    }
}
