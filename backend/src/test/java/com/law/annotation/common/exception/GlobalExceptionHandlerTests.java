package com.law.annotation.common.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTests.TestController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTests.TestController.class})
@ImportAutoConfiguration(exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
class GlobalExceptionHandlerTests {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Test
    void returnsFieldLocatorForInvalidRequestBody() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("COMMON.VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.locators", hasSize(1)))
                .andExpect(jsonPath("$.error.locators[0].path").value("name"));
    }

    @Test
    void returnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON.MALFORMED_REQUEST"));
    }

    @Test
    void returnsLocatorForInvalidMethodParameter() throws Exception {
        mockMvc.perform(get("/test/constraint").param("value", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON.VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.locators[0].path").value("value"));
    }

    @Test
    void preservesApiExceptionStatusAndCode() throws Exception {
        mockMvc.perform(get("/test/api-exception"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COMMON.CONFLICT"))
                .andExpect(jsonPath("$.error.userMessage").value("请求冲突"));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON.INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.userMessage").value("服务器内部错误"))
                .andExpect(content().string(not(containsString("secret-internal-message"))));
    }

    @RestController
    @Validated
    public static class TestController {

        @PostMapping("/test/validation")
        TestRequest validate(@Valid @RequestBody TestRequest request) {
            return request;
        }

        @GetMapping("/test/constraint")
        String constraint(@RequestParam @Size(min = 2) String value) {
            return value;
        }

        @GetMapping("/test/api-exception")
        void apiException() {
            throw new ApiException(HttpStatus.CONFLICT, "COMMON.CONFLICT", "请求冲突");
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("secret-internal-message");
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
