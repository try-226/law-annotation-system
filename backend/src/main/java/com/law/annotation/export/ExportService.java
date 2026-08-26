package com.law.annotation.export;

import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.export.dto.LawExportRequest;
import com.law.annotation.export.dto.PlainLawExport;
import com.law.annotation.export.formatter.PlainExportCsvFormatter;
import com.law.annotation.export.formatter.PlainExportJsonFormatter;
import com.law.annotation.law.ArticleSnapshot;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawErrorCodes;
import com.law.annotation.law.LawRepository;
import com.law.annotation.law.LawStructureNode;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
public class ExportService {

    private static final MediaType CSV_MEDIA_TYPE = MediaType.parseMediaType(
            "text/csv;charset=UTF-8");
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parseMediaType(
            "application/json;charset=UTF-8");

    private final LawRepository lawRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final PlainExportCsvFormatter csvFormatter;
    private final PlainExportJsonFormatter jsonFormatter;

    public ExportService(
            LawRepository lawRepository,
            ContentVersionRepository contentVersionRepository,
            PlainExportCsvFormatter csvFormatter,
            PlainExportJsonFormatter jsonFormatter) {
        this.lawRepository = lawRepository;
        this.contentVersionRepository = contentVersionRepository;
        this.csvFormatter = csvFormatter;
        this.jsonFormatter = jsonFormatter;
    }

    public ExportedFile export(String lawId, LawExportRequest request) {
        if (request.type() != LawExportRequest.Type.PLAIN) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ExportErrorCodes.TYPE_UNSUPPORTED,
                    "PR15仅支持纯法律正文导出");
        }

        LawDocument law = requireVisibleLaw(lawId);
        ContentVersionDocument version = requireCurrentVersion(law);
        List<ArticleSnapshot> selectedArticles = selectArticles(
                version.getSemanticArticlesSnapshot(), request);
        PlainLawExport export = buildExport(law, version, selectedArticles);
        String extension = request.format().name().toLowerCase(Locale.ROOT);
        String filename = "law-" + safeFilenamePart(law.getId()) + "-plain." + extension;

        return switch (request.format()) {
            case CSV -> new ExportedFile(csvFormatter.format(export), CSV_MEDIA_TYPE, filename);
            case JSON -> new ExportedFile(
                    jsonFormatter.format(export), JSON_MEDIA_TYPE, filename);
        };
    }

    private LawDocument requireVisibleLaw(String lawId) {
        return lawRepository.findById(lawId)
                .filter(law -> !law.isDeleted())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        LawErrorCodes.NOT_FOUND,
                        "法律不存在"));
    }

    private ContentVersionDocument requireCurrentVersion(LawDocument law) {
        return contentVersionRepository.findById(law.getCurrentContentVersionId())
                .filter(version -> law.getId().equals(version.getLawId()))
                .orElseThrow(ExportService::versionInconsistent);
    }

    private List<ArticleSnapshot> selectArticles(
            List<ArticleSnapshot> latestArticles,
            LawExportRequest request) {
        List<ArticleSnapshot> orderedArticles = latestArticles.stream()
                .sorted(Comparator.comparingInt(ArticleSnapshot::getOrder))
                .toList();
        if (request.scope() == LawExportRequest.Scope.WHOLE) {
            if (!request.articleIds().isEmpty()) {
                throw selectionInvalid("articleIds", "WHOLE导出不得提交articleIds");
            }
            return orderedArticles;
        }
        if (request.articleIds().isEmpty()) {
            throw selectionInvalid("articleIds", "SELECTED导出必须至少选择一个articleId");
        }

        Map<String, ArticleSnapshot> articlesById = new LinkedHashMap<>();
        orderedArticles.forEach(article -> articlesById.put(article.getArticleId(), article));
        Set<String> requestedIds = new LinkedHashSet<>();
        for (int index = 0; index < request.articleIds().size(); index++) {
            String articleId = request.articleIds().get(index);
            String path = "articleIds[" + index + "]";
            if (articleId == null || articleId.isBlank()) {
                throw selectionInvalid(path, "articleId不能为空");
            }
            if (!requestedIds.add(articleId)) {
                throw selectionInvalid(path, "articleId不能重复");
            }
            if (!articlesById.containsKey(articleId)) {
                throw selectionInvalid(path, "articleId不属于法律当前内容版本");
            }
        }
        return orderedArticles.stream()
                .filter(article -> requestedIds.contains(article.getArticleId()))
                .toList();
    }

    private PlainLawExport buildExport(
            LawDocument law,
            ContentVersionDocument version,
            List<ArticleSnapshot> articles) {
        List<LawStructureNode> structure = law.getStructure().stream()
                .sorted(Comparator.comparingInt(LawStructureNode::getOrder))
                .toList();
        Set<String> currentArticleIds = version.getSemanticArticlesSnapshot().stream()
                .map(ArticleSnapshot::getArticleId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<String, List<String>> structurePaths = structurePaths(structure, currentArticleIds);

        return new PlainLawExport(
                new PlainLawExport.LawInfo(
                        law.getId(),
                        law.getName(),
                        law.getIssuingAuthority(),
                        law.getPublicationDate(),
                        law.getValidityStatus(),
                        version.getId(),
                        version.getSeq()),
                structure.stream()
                        .map(node -> new PlainLawExport.StructureNode(
                                node.getNodeId(),
                                node.getType(),
                                node.getTitle(),
                                node.getParentNodeId(),
                                node.getOrder(),
                                node.getArticleIds()))
                        .toList(),
                articles.stream()
                        .map(article -> new PlainLawExport.Article(
                                article.getArticleId(),
                                article.getNumber(),
                                article.getBody(),
                                article.getOrder(),
                                structurePaths.getOrDefault(article.getArticleId(), List.of())))
                        .toList());
    }

    private static Map<String, List<String>> structurePaths(
            List<LawStructureNode> structure,
            Set<String> currentArticleIds) {
        Map<String, LawStructureNode> nodesById = new HashMap<>();
        for (LawStructureNode node : structure) {
            if (nodesById.put(node.getNodeId(), node) != null) {
                throw versionInconsistent();
            }
        }

        Map<String, List<String>> pathsByArticleId = new HashMap<>();
        for (LawStructureNode node : structure) {
            List<String> path = pathForNode(node, nodesById);
            for (String articleId : node.getArticleIds()) {
                if (!currentArticleIds.contains(articleId)
                        || pathsByArticleId.put(articleId, path) != null) {
                    throw versionInconsistent();
                }
            }
        }
        return Map.copyOf(pathsByArticleId);
    }

    private static List<String> pathForNode(
            LawStructureNode node,
            Map<String, LawStructureNode> nodesById) {
        List<String> reversedPath = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        LawStructureNode current = node;
        while (current != null) {
            if (!visited.add(current.getNodeId())) {
                throw versionInconsistent();
            }
            reversedPath.add(current.getTitle());
            String parentNodeId = current.getParentNodeId();
            if (parentNodeId == null || parentNodeId.isBlank()) {
                break;
            }
            current = nodesById.get(parentNodeId);
            if (current == null) {
                throw versionInconsistent();
            }
        }
        Collections.reverse(reversedPath);
        return List.copyOf(reversedPath);
    }

    private static ApiException selectionInvalid(String path, String message) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ExportErrorCodes.SELECTION_INVALID,
                "导出法条选择不合法",
                List.of(new ErrorLocator(path, message)));
    }

    private static ApiException versionInconsistent() {
        return new ApiException(
                HttpStatus.CONFLICT,
                LawErrorCodes.VERSION_INCONSISTENT,
                "法律当前内容版本数据不一致");
    }

    private static String safeFilenamePart(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9_-]", "_");
        if (sanitized.isBlank()) {
            sanitized = Integer.toUnsignedString(value.hashCode(), 36);
        }
        return sanitized.length() <= 64 ? sanitized : sanitized.substring(0, 64);
    }
}
