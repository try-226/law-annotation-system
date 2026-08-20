package com.law.annotation.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApiResponseTests {

    @Test
    void createsTypedSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("payload");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.error()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void createsFailureResponseWithoutData() {
        ApiError error = new ApiError(
                "COMMON.VALIDATION_FAILED",
                "请求参数校验失败",
                List.of(new ErrorLocator("name", "不能为空")));

        ApiResponse<String> response = ApiResponse.failure(error);

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.error()).isEqualTo(error);
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void defensivelyCopiesErrorLocators() {
        ErrorLocator locator = new ErrorLocator("name", "不能为空");
        ApiError error = new ApiError("CODE", "message", List.of(locator));

        assertThat(error.locators()).containsExactly(locator).isUnmodifiable();
    }
}
