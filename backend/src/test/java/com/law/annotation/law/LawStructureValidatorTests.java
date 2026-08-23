package com.law.annotation.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LawStructureValidatorTests {

    @Test
    void rejectsTwoNodeCycle() {
        List<LawStructureNode> structure = List.of(
                node("chapter-a", LawStructureNodeType.CHAPTER, "chapter-b", List.of()),
                node("chapter-b", LawStructureNodeType.CHAPTER, "chapter-a", List.of()));

        assertThatThrownBy(() -> LawStructureValidator.validate(structure, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("循环");
    }

    @Test
    void rejectsThreeNodeCycle() {
        List<LawStructureNode> structure = List.of(
                node("chapter-a", LawStructureNodeType.CHAPTER, "chapter-b", List.of()),
                node("chapter-b", LawStructureNodeType.CHAPTER, "chapter-c", List.of()),
                node("chapter-c", LawStructureNodeType.CHAPTER, "chapter-a", List.of()));

        assertThatThrownBy(() -> LawStructureValidator.validate(structure, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("循环");
    }

    @Test
    void rejectsSelfParent() {
        LawStructureNode selfParent =
                node("chapter-a", LawStructureNodeType.CHAPTER, "chapter-a", List.of());

        assertThatThrownBy(() -> LawStructureValidator.validate(List.of(selfParent), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("自身");
    }

    @Test
    void rejectsMissingParent() {
        LawStructureNode missingParent =
                node("chapter-a", LawStructureNodeType.CHAPTER, "missing", List.of());

        assertThatThrownBy(() -> LawStructureValidator.validate(List.of(missingParent), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在的parentNodeId");
    }

    @Test
    void rejectsArticlePlacedInMultipleNodes() {
        List<LawStructureNode> structure = List.of(
                node("chapter-a", LawStructureNodeType.CHAPTER, null, List.of("article-1")),
                node("chapter-b", LawStructureNodeType.CHAPTER, null, List.of("article-1")));

        assertThatThrownBy(() -> LawStructureValidator.validate(structure, Set.of("article-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("多个结构节点");
    }

    @Test
    void acceptsValidPartChapterSectionTree() {
        List<LawStructureNode> structure = List.of(
                node("part-1", LawStructureNodeType.PART, null, List.of()),
                node("chapter-1", LawStructureNodeType.CHAPTER, "part-1", List.of()),
                node("section-1", LawStructureNodeType.SECTION, "chapter-1", List.of("article-1")));

        assertThat(LawStructureValidator.validate(structure, Set.of("article-1")))
                .containsExactlyElementsOf(structure);
    }

    private static LawStructureNode node(
            String nodeId,
            LawStructureNodeType type,
            String parentNodeId,
            List<String> articleIds) {
        return new LawStructureNode(
                nodeId,
                type,
                nodeId,
                parentNodeId,
                0,
                articleIds);
    }
}
