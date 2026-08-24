package com.law.annotation.law;

import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.enums.TaskState;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.law.dto.LawDetailResponse;
import com.law.annotation.law.dto.LawDetailViewResponse;
import com.law.annotation.law.dto.LawListItemResponse;
import com.law.annotation.law.dto.RecycleLawListItemResponse;
import com.law.annotation.task.TaskDocument;
import com.law.annotation.task.TaskRepository;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LawQueryService {

    private static final List<TaskState> UNFINISHED_TASK_STATES = List.of(
            TaskState.PENDING_ANNOTATION,
            TaskState.ANNOTATING,
            TaskState.PENDING_REVIEW,
            TaskState.PARTIALLY_REJECTED,
            TaskState.PENDING_REREVIEW);

    private final LawRepository lawRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final LawAuditRepository lawAuditRepository;
    private final TaskRepository taskRepository;

    public LawQueryService(
            LawRepository lawRepository,
            ContentVersionRepository contentVersionRepository,
            LawAuditRepository lawAuditRepository,
            TaskRepository taskRepository) {
        this.lawRepository = lawRepository;
        this.contentVersionRepository = contentVersionRepository;
        this.lawAuditRepository = lawAuditRepository;
        this.taskRepository = taskRepository;
    }

    public PageResponse<LawListItemResponse> list(String name, int page, int size) {
        PageRequest pageable = pageable(page, size);
        String normalizedSearch = normalizeSearch(name);
        Page<LawDocument> laws = normalizedSearch == null
                ? lawRepository.findByDeletedAtIsNull(pageable)
                : lawRepository.findByDeletedAtIsNullAndNormalizedNameContaining(
                        normalizedSearch,
                        pageable);
        Map<String, ContentVersionDocument> versions = contentVersionRepository
                .findByIdIn(laws.getContent().stream()
                        .map(LawDocument::getCurrentContentVersionId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(ContentVersionDocument::getId, Function.identity()));
        List<LawListItemResponse> items = laws.getContent().stream()
                .map(law -> LawResponseMapper.toListItem(law, requireCurrentVersion(law, versions)))
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
        ContentVersionDocument version = requireCurrentVersion(law);
        return LawResponseMapper.toDetail(law, version);
    }

    public LawDetailViewResponse getViewDetail(String lawId) {
        LawDocument law = requireVisibleLaw(lawId);
        ContentVersionDocument version = requireCurrentVersion(law);
        TaskDocument currentTask = taskRepository
                .findFirstByLawIdAndTaskStateInOrderByUpdatedAtDesc(
                        lawId,
                        UNFINISHED_TASK_STATES)
                .orElse(null);
        boolean hasHistory = version.getSeq() > 1
                || law.getCurrentAnnotationVersionId() != null
                || lawAuditRepository.existsByLawId(lawId)
                || taskRepository.existsByLawId(lawId);
        return LawResponseMapper.toViewDetail(law, version, currentTask, hasHistory);
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
