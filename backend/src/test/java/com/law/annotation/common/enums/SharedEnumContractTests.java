package com.law.annotation.common.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SharedEnumContractTests {

    @Test
    void roleValuesAreFrozen() {
        assertThat(names(Role.values())).containsExactly("ADMIN", "ANNOTATOR");
    }

    @Test
    void taskTypeValuesAreFrozen() {
        assertThat(names(TaskType.values())).containsExactly("ORDINARY", "REVISION");
    }

    @Test
    void taskStateValuesAreFrozen() {
        assertThat(names(TaskState.values())).containsExactly(
                "PENDING_ANNOTATION",
                "ANNOTATING",
                "PENDING_REVIEW",
                "PARTIALLY_REJECTED",
                "PENDING_REREVIEW",
                "APPROVED",
                "CANCELED");
    }

    @Test
    void reviewItemStateValuesAreFrozen() {
        assertThat(names(ReviewItemState.values())).containsExactly(
                "UNREVIEWED",
                "CHECKED",
                "NEEDS_CHANGE");
    }

    @Test
    void validityStatusValuesAreFrozen() {
        assertThat(names(ValidityStatus.values())).containsExactly(
                "ACTIVE",
                "NOT_EFFECTIVE",
                "INVALID",
                "REPEALED");
    }

    @Test
    void itemTypeValuesAreFrozen() {
        assertThat(names(ItemType.values())).containsExactly(
                "DEFINITION",
                "RIGHTS_DUTIES",
                "AUTHORITY_DUTY",
                "PROHIBITION_RESTRICTION",
                "PROCEDURE",
                "LIABILITY",
                "OTHER");
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }
}
