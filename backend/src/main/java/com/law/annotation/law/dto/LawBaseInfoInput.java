package com.law.annotation.law.dto;

import com.law.annotation.common.enums.ValidityStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record LawBaseInfoInput(
        @NotBlank String name,
        @NotBlank String issuingAuthority,
        @NotNull LocalDate publicationDate,
        @NotNull ValidityStatus validityStatus) {
}
