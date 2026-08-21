package com.law.annotation.law;

import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.law.dto.LawImportArticleInput;
import com.law.annotation.law.dto.LawImportConfirmRequest;
import com.law.annotation.law.dto.LawImportPreviewResponse;
import com.law.annotation.law.dto.LawStructureInput;
import com.law.annotation.law.dto.LawDetailResponse;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LawImportService {

    private final LawTextParser parser;
    private final LawCreationService lawCreationService;

    public LawImportService(LawTextParser parser, LawCreationService lawCreationService) {
        this.parser = parser;
        this.lawCreationService = lawCreationService;
    }

    public LawImportPreviewResponse parse(String fullTextPaste) {
        return parser.parse(fullTextPaste);
    }

    public LawDetailResponse confirm(LawImportConfirmRequest request, String operatorId) {
        if (request == null || request.baseInfo() == null) {
            throw validation("baseInfo", "法律基础信息不能为空");
        }
        if (request.articles() == null || request.articles().isEmpty()) {
            throw validation("articles", "首次创建法律至少需要一条法条");
        }
        Map<String, String> articleIdByClientKey = new LinkedHashMap<>();
        List<NewArticleDraft> drafts;
        try {
            drafts = request.articles().stream()
                    .map(article -> toDraft(article, articleIdByClientKey))
                    .toList();
        } catch (IllegalArgumentException exception) {
            throw validation("articles", exception.getMessage());
        }
        List<LawStructureNode> structure;
        try {
            structure = toStructure(request.structure(), articleIdByClientKey);
        } catch (IllegalArgumentException exception) {
            throw validation("structure", exception.getMessage());
        }

        try {
            InitialLawCreation creation = lawCreationService.createInitialLaw(
                    request.baseInfo().name(),
                    request.baseInfo().issuingAuthority(),
                    request.baseInfo().publicationDate(),
                    request.baseInfo().validityStatus(),
                    structure,
                    drafts,
                    operatorId);
            return LawResponseMapper.toDetail(creation.law(), creation.contentVersion());
        } catch (ApiException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw validation("confirm", exception.getMessage());
        }
    }

    private static NewArticleDraft toDraft(
            LawImportArticleInput article,
            Map<String, String> articleIdByClientKey) {
        if (article == null) {
            throw new IllegalArgumentException("articles不能包含null");
        }
        String clientKey = LawDomainRules.requireIdentifier(article.clientKey(), "articles.clientKey");
        String articleId = UUID.randomUUID().toString();
        if (articleIdByClientKey.putIfAbsent(clientKey, articleId) != null) {
            throw new IllegalArgumentException("articles.clientKey不能重复");
        }
        return new NewArticleDraft(articleId, article.number(), article.body(), article.order());
    }

    static List<LawStructureNode> toStructure(
            List<LawStructureInput> inputs,
            Map<String, String> articleIdByReference) {
        if (inputs == null) {
            throw new IllegalArgumentException("structure不能为空");
        }
        Map<String, String> nodeIds = new HashMap<>();
        for (LawStructureInput input : inputs) {
            if (input == null) {
                throw new IllegalArgumentException("structure不能包含null");
            }
            if (nodeIds.putIfAbsent(input.nodeId(), input.nodeId()) != null) {
                throw new IllegalArgumentException("structure.nodeId不能重复");
            }
        }
        return inputs.stream()
                .map(input -> new LawStructureNode(
                        input.nodeId(),
                        input.type(),
                        input.title(),
                        input.parentNodeId(),
                        input.order(),
                        input.articleRefs().stream()
                                .map(reference -> {
                                    String articleId = articleIdByReference.get(reference);
                                    if (articleId == null) {
                                        throw new IllegalArgumentException(
                                                "structure引用了不存在的articleRef: " + reference);
                                    }
                                    return articleId;
                                })
                                .toList()))
                .toList();
    }

    private static ApiException validation(String path, String message) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                LawErrorCodes.VALIDATION_FAILED,
                "法律导入确认校验失败",
                List.of(new ErrorLocator(path, message)));
    }
}
