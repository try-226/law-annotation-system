package com.law.annotation.law.dto;

import com.law.annotation.common.enums.ValidityStatus;
import java.time.Instant;
import java.time.LocalDate;

public record LawListItemResponse(
        String id,
        String name,
        String issuingAuthority,
        LocalDate publicationDate,
        ValidityStatus validityStatus,
        int articleCount,
        Instant updatedAt) {
}
