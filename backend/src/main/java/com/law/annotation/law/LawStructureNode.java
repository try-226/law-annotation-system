package com.law.annotation.law;

import java.util.HashSet;
import java.util.List;

public class LawStructureNode {

    private final String nodeId;
    private final LawStructureNodeType type;
    private final String title;
    private final String parentNodeId;
    private final int order;
    private final List<String> articleIds;

    public LawStructureNode(
            String nodeId,
            LawStructureNodeType type,
            String title,
            String parentNodeId,
            int order,
            List<String> articleIds) {
        this.nodeId = LawDomainRules.requireIdentifier(nodeId, "structure.nodeId");
        if (type == null) {
            throw new IllegalArgumentException("结构节点类型不能为空");
        }
        this.type = type;
        this.title = LawDomainRules.validateStructureTitle(title);
        this.parentNodeId = parentNodeId;
        if (order < 0) {
            throw new IllegalArgumentException("结构节点顺序不能小于0");
        }
        this.order = order;
        this.articleIds = articleIds == null ? List.of() : List.copyOf(articleIds);
        if (this.articleIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("结构节点中的articleId不能为空");
        }
        if (new HashSet<>(this.articleIds).size() != this.articleIds.size()) {
            throw new IllegalArgumentException("同一结构节点不能重复引用articleId");
        }
    }

    public String getNodeId() {
        return nodeId;
    }

    public LawStructureNodeType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getParentNodeId() {
        return parentNodeId;
    }

    public int getOrder() {
        return order;
    }

    public List<String> getArticleIds() {
        return articleIds;
    }
}
