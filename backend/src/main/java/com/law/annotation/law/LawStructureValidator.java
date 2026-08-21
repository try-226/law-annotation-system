package com.law.annotation.law;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class LawStructureValidator {

    private LawStructureValidator() {
    }

    static List<LawStructureNode> validate(
            List<LawStructureNode> structure,
            Collection<String> articleIds) {
        if (structure == null) {
            return List.of();
        }
        Set<String> availableArticleIds = new HashSet<>(articleIds);
        Set<String> nodeIds = new HashSet<>();
        for (LawStructureNode node : structure) {
            if (node == null) {
                throw new IllegalArgumentException("structure不能包含null");
            }
            if (!nodeIds.add(node.getNodeId())) {
                throw new IllegalArgumentException("structure.nodeId不能重复");
            }
            if (!availableArticleIds.containsAll(node.getArticleIds())) {
                throw new IllegalArgumentException("结构节点引用了当前内容版本中不存在的articleId");
            }
        }
        for (LawStructureNode node : structure) {
            if (node.getParentNodeId() != null
                    && !node.getParentNodeId().isBlank()
                    && !nodeIds.contains(node.getParentNodeId())) {
                throw new IllegalArgumentException("结构节点引用了不存在的parentNodeId");
            }
            if (node.getNodeId().equals(node.getParentNodeId())) {
                throw new IllegalArgumentException("结构节点不能以自身作为父节点");
            }
        }
        return List.copyOf(structure);
    }
}
