package com.law.annotation.review.dto;

import com.law.annotation.common.enums.ReviewItemState;
import com.law.annotation.review.ReviewIssue;
import com.law.annotation.review.ReviewItemLocator;

public record ReviewItemResponse(
        ReviewItemLocator locator,
        ReviewItemState state,
        ReviewIssue issue) {
}
