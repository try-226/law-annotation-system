package com.law.annotation.law;

import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.law.dto.LawDetailResponse;
import com.law.annotation.law.dto.LawListItemResponse;
import com.law.annotation.law.dto.RecycleLawListItemResponse;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.task.TaskStateRules;
import com.law.annotation.task.TaskStatusProjection;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LawQueryService {

    private final LawRepository lawRepository;
    private final LawSearchRepository lawSearchRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final TaskRepository taskRepository;
    private final LawDisplayStatusResolver displayStatusResolver;

    public LawQueryService(
            LawRepository lawRepository,
            LawSearchRepository lawSearchRepository,
            ContentVersionRepository contentVersionRepository,
            TaskRepository taskRepository,
            LawDisplayStatusResolver displayStatusResolver) {
        this.lawRepository = lawRepository;
        this.lawSearchRepository = lawSearchRepository;
        this.contentVersionRepository = contentVersionRepository;
        this.taskRepository = taskRepository;
        this.displayStatusResolver = displayStatusResolver;
    }

    public PageResponse<LawListItemResponse> list(String name, int page, int size) {
        return list(name, null, null, page, size);
    }

    public PageResponse<LawListItemResponse> list(
            String name,
            ValidityStatus validityStatus,
            LawDisplayStatus displayStatus,
            int page,
            int size) {
        PageRequest pageable = pageable(page, size);
        String normalizedSearch = normalizeSearch(name);
        Map<String, TaskStatusProjection> activeTasksByLawId = Map.of();
        Set<String> includedLawIds = Set.of();
        Set<String> excludedLawIds = Set.of();
        if (displayStatus != null) {
            List<TaskStatusProjection> activeTasks = taskRepository.findStatusesByTaskStateIn(
                    TaskStateRules.unfinishedStates());
            activeTasksByLawId = indexTasksByLawId(activeTasks);
            excludedLawIds = Set.copyOf(activeTasksByLawId.keySet());
            if (LawSearchRepository.requiresActiveTask(displayStatus)) {
                Set<String> matches = new HashSet<>();
                activeTasks.forEach(task -> {
                    if (displayStatusResolver.resolveActiveTask(task) == displayStatus) {
                        matches.add(task.getLawId());
                    }
                });
                includedLawIds = Set.copyOf(matches);
                if (includedLawIds.isEmpty()) {
                    return emptyPage(page, size);
                }
            }
        }

        Page<LawDocument> laws = lawSearchRepository.search(
                new LawSearchFilter(
                        normalizedSearch,
                        validityStatus,
                        displayStatus,
                        includedLawIds,
                        excludedLawIds),
                pageable);
        if (laws.isEmpty()) {
            return new PageResponse<>(
                    List.of(),
                    laws.getNumber(),
                    laws.getSize(),
                    laws.getTotalElements(),
                    laws.getTotalPages());
        }
        if (displayStatus == null) {
            List<String> lawIds = laws.getContent().stream().map(LawDocument::getId).toList();
            activeTasksByLawId = indexTasksByLawId(
                    taskRepository.findStatusesByLawIdInAndTaskStateIn(
                            lawIds,
                            TaskStateRules.unfinishedStates()));
        }
        Map<String, ContentVersionDocument> versions = contentVersionRepository
                .findByIdIn(laws.getContent().stream()
                        .map(LawDocument::getCurrentContentVersionId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(ContentVersionDocument::getId, Function.identity()));
        Map<String, TaskStatusProjection> resolvedTasks = activeTasksByLawId;
        List<LawListItemResponse> items = laws.getContent().stream()
                .map(law -> LawResponseMapper.toListItem(
                        law,
                        requireCurrentVersion(law, versions),
                        displayStatusResolver.resolve(law, resolvedTasks.get(law.getId()))))
                .toList();
        return new PageResponse<>(
                items,
                laws.getNumber(),
                laws.getSize(),
                laws.getTotalElements(),
                laws.getTotalPages());
    }

    public PageResponse<RecycleLawListItemResponse> listRecycle(
            String name,
            int page,
            int size) {
        PageRequest pageable = pageable(page, size);
        String normalizedSearch = normalizeSearch(name);
        Page<LawDocument> laws = normalizedSearch == null
                ? lawRepository.findByDeletedAtIsNotNull(pageable)
                : lawRepository.findByDeletedAtIsNotNullAndNormalizedNameContaining(
                        normalizedSearch,
                        pageable);
        Map<String, ContentVersionDocument> versions = contentVersionRepository
                .findByIdIn(laws.getContent().stream()
                        .map(LawDocument::getCurrentContentVersionId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(ContentVersionDocument::getId, Function.identity()));
        List<RecycleLawListItemResponse> items = laws.getContent().stream()
                .map(law -> LawResponseMapper.toRecycleListItem(
                        law,
                        requireCurrentVersion(law, versions)))
                .toList();
        return new PageResponse<>(
                items,
                laws.getNumber(),
                laws.getSize(),
                laws.getTotalElements(),
                laws.getTotalPages());
    }

    public LawDetailResponse getDetail(String lawId) {
        LawDocument law = requireVisibleLaw(lawId);
        ContentVersionDocument version = contentVersionRepository
                .findById(law.getCurrentContentVersionId())
                .filter(candidate -> law.getId().equals(candidate.getLawId()))
                .orElseThrow(LawQueryService::versionInconsistent);
        TaskStatusProjection activeTask = taskRepository.findStatusesByLawIdInAndTaskStateIn(
                        List.of(lawId),
                        TaskStateRules.unfinishedStates())
                .stream()
                .findFirst()
                .orElse(null);
        return LawResponseMapper.toDetail(
                law,
                version,
                displayStatusResolver.resolve(law, activeTask));
    }

    LawDocument requireVisibleLaw(String lawId) {
        return lawRepository.findById(lawId)
                .filter(law -> law.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        LawErrorCodes.NOT_FOUND,
                        "法律不存在"));
    }

    LawDocument requireDeletedLaw(String lawId) {
        return lawRepository.findById(lawId)
                .filter(law -> law.getDeletedAt() != null)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        LawErrorCodes.NOT_FOUND,
                        "回收站中不存在该法律"));
    }

    ContentVersionDocument requireCurrentVersion(LawDocument law) {
        return contentVersionRepository.findById(law.getCurrentContentVersionId())
                .filter(candidate -> law.getId().equals(candidate.getLawId()))
                .orElseThrow(LawQueryService::versionInconsistent);
    }

    private static ContentVersionDocument requireCurrentVersion(
            LawDocument law,
            Map<String, ContentVersionDocument> versions) {
        ContentVersionDocument version = versions.get(law.getCurrentContentVersionId());
        if (version == null || !law.getId().equals(version.getLawId())) {
            throw versionInconsistent();
        }
        return version;
    }

    private static Map<String, TaskStatusProjection> indexTasksByLawId(
            List<TaskStatusProjection> tasks) {
        Map<String, TaskStatusProjection> result = new HashMap<>();
        tasks.forEach(task -> result.putIfAbsent(task.getLawId(), task));
        return Map.copyOf(result);
    }

    private static PageResponse<LawListItemResponse> emptyPage(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0, 0);
    }

    private static String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        boolean containsControl = value.codePoints().anyMatch(Character::isISOControl);
        String trimmed = value.trim();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (containsControl || length < 1 || length > 100) {
            throw validation("name", "名称查询须为1至100个字符且不得包含控制字符");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static PageRequest pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw validation("page/size", "page不能小于0，size须为1至100");
        }
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
    }

    private static ApiException validation(String path, String message) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                LawErrorCodes.VALIDATION_FAILED,
                "法律查询参数不合法",
                List.of(new ErrorLocator(path, message)));
    }

    private static ApiException versionInconsistent() {
        return new ApiException(
                HttpStatus.CONFLICT,
                LawErrorCodes.VERSION_INCONSISTENT,
                "法律当前内容版本数据不一致");
    }
}
