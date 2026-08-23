package com.law.annotation.task;

import com.law.annotation.law.LawStructureNode;
import com.law.annotation.law.LawStructureNodeType;
import java.util.List;

public record TaskStructureNodeSnapshot(
        String nodeId,
        LawStructureNodeType type,
        String title,
        String parentNodeId,
        int order,
        List<String> articleIds) {

    public TaskStructureNodeSnapshot {
        articleIds = List.copyOf(articleIds);
    }

    public static TaskStructureNodeSnapshot from(LawStructureNode node) {
        return new TaskStructureNodeSnapshot(
                node.getNodeId(),
                node.getType(),
                node.getTitle(),
                node.getParentNodeId(),
                node.getOrder(),
                node.getArticleIds());
    }
}
