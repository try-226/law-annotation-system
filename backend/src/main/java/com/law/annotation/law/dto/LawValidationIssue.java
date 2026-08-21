package com.law.annotation.law.dto;

public record LawValidationIssue(
        String code,
        String field,
        Integer articleIndex,
        String articleNumber,
        String structurePath,
        String message) {
}
