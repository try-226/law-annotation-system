package com.law.annotation.review.dto;

public record ReviewProgressResponse(
        int total,
        int reviewed,
        int unreviewed,
        int needsChange) {
}
