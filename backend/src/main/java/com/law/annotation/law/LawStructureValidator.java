package com.law.annotation.law;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        Set<String> placedArticleIds = new HashSet<>();
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
            for (String articleId : node.getArticleIds()) {
                if (!placedArticleIds.add(articleId)) {
                    throw new IllegalArgumentException("同一articleId不能挂载到多个结构节点");
                }
            }
        }
        Map<String, String> parentByNodeId = new HashMap<>();
        for (LawStructureNode node : structure) {
            String parentNodeId = node.getParentNodeId();
            if (parentNodeId != null
                    && !parentNodeId.isBlank()
                    && !nodeIds.contains(parentNodeId)) {
                throw new IllegalArgumentException("结构节点引用了不存在的parentNodeId");
            }
            if (node.getNodeId().equals(parentNodeId)) {
                throw new IllegalArgumentException("结构节点不能以自身作为父节点");
            }
            if (parentNodeId != null && !parentNodeId.isBlank()) {
                parentByNodeId.put(node.getNodeId(), parentNodeId);
            }
        }
        for (LawStructureNode node : structure) {
            Set<String> path = new HashSet<>();
            String currentNodeId = node.getNodeId();
            while (currentNodeId != null) {
                if (!path.add(currentNodeId)) {
                    throw new IllegalArgumentException("结构节点不能形成循环");
                }
                currentNodeId = parentByNodeId.get(currentNodeId);
            }
        }
        return List.copyOf(structure);
    }
}
