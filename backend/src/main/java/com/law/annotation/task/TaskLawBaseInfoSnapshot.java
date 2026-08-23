package com.law.annotation.task;

import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.law.LawDocument;
import java.time.LocalDate;

public record TaskLawBaseInfoSnapshot(
        String name,
        String issuingAuthority,
        LocalDate publicationDate,
        ValidityStatus validityStatus) {

    public static TaskLawBaseInfoSnapshot from(LawDocument law) {
        return new TaskLawBaseInfoSnapshot(
                law.getName(),
                law.getIssuingAuthority(),
                law.getPublicationDate(),
                law.getValidityStatus());
    }
}
