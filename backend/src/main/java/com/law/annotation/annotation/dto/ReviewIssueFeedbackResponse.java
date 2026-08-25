package com.law.annotation.annotation.dto;

import com.law.annotation.review.ReviewItemLocator;

public record ReviewIssueFeedbackResponse(
        String reviewRoundId,
        ReviewItemLocator locator,
        String reason) {
}
