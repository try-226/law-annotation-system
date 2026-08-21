package com.law.annotation.law;

/**
 * Extension point for the Task module to block Law mutations while an active task exists.
 */
public interface LawMutationGuard {

    void assertMutationAllowed(String lawId);
}
