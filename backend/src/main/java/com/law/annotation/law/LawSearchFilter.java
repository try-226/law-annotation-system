package com.law.annotation.law;

import com.law.annotation.common.enums.ValidityStatus;
import java.util.Set;

record LawSearchFilter(
        String normalizedName,
        ValidityStatus validityStatus,
        LawDisplayStatus displayStatus,
        Set<String> includeLawIds,
        Set<String> excludeLawIds) {

    LawSearchFilter {
        includeLawIds = includeLawIds == null ? Set.of() : Set.copyOf(includeLawIds);
        excludeLawIds = excludeLawIds == null ? Set.of() : Set.copyOf(excludeLawIds);
    }
}
