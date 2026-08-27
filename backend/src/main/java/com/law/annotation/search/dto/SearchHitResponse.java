package com.law.annotation.search.dto;

import com.law.annotation.search.SearchHitSource;
import java.util.List;

public record SearchHitResponse(
        String lawId,
        String lawName,
        String articleId,
        String articleNumber,
        List<String> structurePath,
        SearchHitSource hitSource,
        String hitField,
        String snippet,
        int highlightStart,
        int highlightEnd) {

    public SearchHitResponse {
        structurePath = structurePath == null ? List.of() : List.copyOf(structurePath);
    }
}
