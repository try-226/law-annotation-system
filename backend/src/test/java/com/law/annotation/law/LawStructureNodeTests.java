package com.law.annotation.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class LawStructureNodeTests {

    @Test
    void trimsStructureTitleBeforeSaving() {
        LawStructureNode node = node("  第一章 总则  ");

        assertThat(node.getTitle()).isEqualTo("第一章 总则");
    }

    @Test
    void rejectsEmptyStructureTitle() {
        assertThatThrownBy(() -> node(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWhitespaceOnlyStructureTitle() {
        assertThatThrownBy(() -> node("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsOneHundredCodePointsInStructureTitle() {
        String title = "𠀀".repeat(100);

        assertThat(node(title).getTitle()).isEqualTo(title);
    }

    @Test
    void rejectsOneHundredAndOneCodePointsInStructureTitle() {
        assertThatThrownBy(() -> node("𠀀".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static LawStructureNode node(String title) {
        return new LawStructureNode(
                "chapter-1",
                LawStructureNodeType.CHAPTER,
                title,
                null,
                0,
                List.of());
    }
}
