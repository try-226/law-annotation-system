package com.law.annotation.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.law.annotation.auth.ActiveUserFilter;
import com.law.annotation.auth.MongoUserDetailsService;
import com.law.annotation.auth.RestSecurityErrorHandler;
import com.law.annotation.auth.SecurityConfig;
import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.exception.GlobalExceptionHandler;
import com.law.annotation.field.dto.FieldConfigItemResponse;
import com.law.annotation.field.dto.FieldConfigResponse;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@WebMvcTest(controllers = FieldConfigController.class)
@Import({
        SecurityConfig.class,
        RestSecurityErrorHandler.class,
        ActiveUserFilter.class,
        GlobalExceptionHandler.class
})
class FieldConfigControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @MockitoBean
    private FieldConfigService fieldConfigService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MongoUserDetailsService mongoUserDetailsService;

    @Test
    void getReturnsNineFieldsInFixedOrderForAnnotator() throws Exception {
        UserDocument annotator = activeUser("annotator", Role.ANNOTATOR);
        when(userRepository.findById("annotator")).thenReturn(Optional.of(annotator));
        when(fieldConfigService.getCurrentConfig()).thenReturn(currentConfig());

        mockMvc.perform(get("/field-config").with(user(UserPrincipal.from(annotator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fields.length()").value(9))
                .andExpect(jsonPath("$.data.fields[0].fieldKey").value("lawCategory"))
                .andExpect(jsonPath("$.data.fields[1].fieldKey").value("overallKeywords"))
                .andExpect(jsonPath("$.data.fields[2].fieldKey").value("summary"))
                .andExpect(jsonPath("$.data.fields[3].fieldKey").value("overallNote"))
                .andExpect(jsonPath("$.data.fields[4].fieldKey").value("itemType"))
                .andExpect(jsonPath("$.data.fields[8].fieldKey").value("annotationNote"));
    }

    @Test
    void adminCanUpdateConfigurableField() throws Exception {
        UserDocument admin = activeUser("admin", Role.ADMIN);
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));
        when(fieldConfigService.updateRequired("summary", true, "admin", Role.ADMIN))
                .thenReturn(currentConfig());

        mockMvc.perform(patch("/field-config")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldKey\":\"summary\",\"required\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fields.length()").value(9));

        verify(fieldConfigService).updateRequired("summary", true, "admin", Role.ADMIN);
    }

    @Test
    void annotatorCannotUpdateConfiguration() throws Exception {
        UserDocument annotator = activeUser("annotator", Role.ANNOTATOR);
        when(userRepository.findById("annotator")).thenReturn(Optional.of(annotator));

        mockMvc.perform(patch("/field-config")
                        .with(user(UserPrincipal.from(annotator)))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldKey\":\"summary\",\"required\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH.FORBIDDEN"));

        verifyNoInteractions(fieldConfigService);
    }

    @Test
    void coreFieldUpdateReturnsBusinessError() throws Exception {
        UserDocument admin = activeUser("admin", Role.ADMIN);
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));
        when(fieldConfigService.updateRequired("lawCategory", false, "admin", Role.ADMIN))
                .thenThrow(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        FieldConfigErrorCodes.CORE_REQUIRED_IMMUTABLE,
                        "核心字段必须保持必填"));

        mockMvc.perform(patch("/field-config")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldKey\":\"lawCategory\",\"required\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code")
                        .value(FieldConfigErrorCodes.CORE_REQUIRED_IMMUTABLE));
    }

    @Test
    void requestRejectsPropertiesOutsideFieldKeyAndRequired() throws Exception {
        UserDocument admin = activeUser("admin", Role.ADMIN);
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));

        mockMvc.perform(patch("/field-config")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fieldKey":"summary","required":true,"displayName":"新名称"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON.VALIDATION_FAILED"));

        verifyNoInteractions(fieldConfigService);
    }

    @Test
    void createAndDeleteEndpointsDoNotExist() {
        Set<RequestMethod> registeredMethods = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType() == FieldConfigController.class)
                .flatMap(entry -> entry.getKey().getMethodsCondition().getMethods().stream())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(registeredMethods).containsExactlyInAnyOrder(RequestMethod.GET, RequestMethod.PATCH);
    }

    private static FieldConfigResponse currentConfig() {
        List<FieldConfigItemResponse> fields = Arrays.stream(FixedAnnotationField.values())
                .map(field -> new FieldConfigItemResponse(
                        field.fieldKey(),
                        field.displayName(),
                        field.valueKind(),
                        field.scope(),
                        field.defaultRequired(),
                        field.configurable()))
                .toList();
        return new FieldConfigResponse(fields);
    }

    private static UserDocument activeUser(String id, Role role) {
        Instant now = Instant.now();
        UserDocument user = new UserDocument(
                "测试用户", id, id, "$2a$12$hash", role, true, now, now);
        user.setId(id);
        return user;
    }
}
