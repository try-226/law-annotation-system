package com.law.annotation.law;

import java.util.HashSet;
import java.util.Set;

public class PendingChangeSet {

    private final Set<String> addedArticleIds;
    private final Set<String> modifiedArticleIds;
    private final Set<String> deletedArticleIds;

    public PendingChangeSet(
            Set<String> addedArticleIds,
            Set<String> modifiedArticleIds,
            Set<String> deletedArticleIds) {
        this.addedArticleIds = immutableIdentifiers(addedArticleIds, "addedArticleIds");
        this.modifiedArticleIds = immutableIdentifiers(modifiedArticleIds, "modifiedArticleIds");
        this.deletedArticleIds = immutableIdentifiers(deletedArticleIds, "deletedArticleIds");
        ensureDisjoint(this.addedArticleIds, this.modifiedArticleIds);
        ensureDisjoint(this.addedArticleIds, this.deletedArticleIds);
        ensureDisjoint(this.modifiedArticleIds, this.deletedArticleIds);
    }

    public static PendingChangeSet empty() {
        return new PendingChangeSet(Set.of(), Set.of(), Set.of());
    }

    public Set<String> getAddedArticleIds() {
        return addedArticleIds;
    }

    public Set<String> getModifiedArticleIds() {
        return modifiedArticleIds;
    }

    public Set<String> getDeletedArticleIds() {
        return deletedArticleIds;
    }

    public boolean isEmpty() {
        return addedArticleIds.isEmpty()
                && modifiedArticleIds.isEmpty()
                && deletedArticleIds.isEmpty();
    }

    private static Set<String> immutableIdentifiers(Set<String> values, String fieldName) {
        if (values == null) {
            return Set.of();
        }
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(fieldName + "不能包含空标识");
        }
        return Set.copyOf(values);
    }

    private static void ensureDisjoint(Set<String> first, Set<String> second) {
        Set<String> overlap = new HashSet<>(first);
        overlap.retainAll(second);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("pendingChangeSet中的变更分类必须互斥");
        }
    }
}
