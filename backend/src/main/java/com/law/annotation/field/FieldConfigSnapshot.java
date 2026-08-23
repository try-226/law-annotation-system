package com.law.annotation.field;

import java.util.List;

public record FieldConfigSnapshot(
        List<FieldConfigSnapshotItem> overall,
        List<FieldConfigSnapshotItem> article) {

    public FieldConfigSnapshot {
        overall = List.copyOf(overall);
        article = List.copyOf(article);
    }
}
