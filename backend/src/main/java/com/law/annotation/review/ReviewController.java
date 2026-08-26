package com.law.annotation.review;

import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.response.ApiResponse;
import com.law.annotation.review.dto.ReviewDetailResponse;
import com.law.annotation.review.dto.ReviewIssueRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tasks/{taskId}/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ApiResponse<ReviewDetailResponse> getReview(
            @PathVariable String taskId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(reviewService.getReview(taskId, principal));
    }

    @PostMapping("/start")
    public ApiResponse<ReviewDetailResponse> start(
            @PathVariable String taskId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(reviewService.start(taskId, principal));
    }

    @PostMapping("/rounds/{roundId}/overall/check")
    public ApiResponse<ReviewDetailResponse> checkOverall(
            @PathVariable String taskId,
            @PathVariable String roundId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(reviewService.check(
                taskId, roundId, ReviewItemLocator.overall(), principal));
    }

    @PostMapping("/rounds/{roundId}/overall/issue")
    public ApiResponse<ReviewDetailResponse> issueOverall(
            @PathVariable String taskId,
            @PathVariable String roundId,
            @RequestBody ReviewIssueRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(reviewService.issue(
                taskId, roundId, ReviewItemLocator.overall(), request.getReason(), principal));
    }

    @PostMapping("/rounds/{roundId}/articles/{articleId}/check")
    public ApiResponse<ReviewDetailResponse> checkArticle(
            @PathVariable String taskId,
            @PathVariable String roundId,
            @PathVariable String articleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(reviewService.check(
                taskId, roundId, ReviewItemLocator.article(articleId), principal));
    }

    @PostMapping("/rounds/{roundId}/articles/{articleId}/issue")
    public ApiResponse<ReviewDetailResponse> issueArticle(
            @PathVariable String taskId,
            @PathVariable String roundId,
            @PathVariable String articleId,
            @RequestBody ReviewIssueRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(reviewService.issue(
                taskId, roundId, ReviewItemLocator.article(articleId), request.getReason(), principal));
    }

    @PostMapping("/rounds/{roundId}/complete")
    public ApiResponse<ReviewDetailResponse> complete(
            @PathVariable String taskId,
            @PathVariable String roundId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(reviewService.complete(taskId, roundId, principal));
    }
}
