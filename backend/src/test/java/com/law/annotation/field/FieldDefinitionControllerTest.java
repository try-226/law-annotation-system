package com.law.annotation.field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.field.dto.FieldDefinitionResponse;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FieldDefinitionController.class)
@Import({
        SecurityConfig.class,
        RestSecurityErrorHandler.class,
        ActiveUserFilter.class,
        GlobalExceptionHandler.class
})
class FieldDefinitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FieldDefinitionService service;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MongoUserDetailsService mongoUserDetailsService;

    private UserDocument admin;
    private UserDocument annotator;

    @BeforeEach
    void setUpUsers() {
        admin = activeUser("admin", Role.ADMIN);
        annotator = activeUser("annotator", Role.ANNOTATOR);
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findById("annotator")).thenReturn(Optional.of(annotator));
    }

    @Test
    void createsFieldDefinition() throws Exception {
        when(service.create(any())).thenReturn(response(FieldDefinitionStatus.ACTIVE));

        mockMvc.perform(post("/field-definitions")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"penalty_amount",
                                  "displayName":"处罚金额",
                                  "description":"处罚金额字段",
                                  "fieldType":"NUMBER",
                                  "required":true,
                                  "options":[]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("penalty_amount"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void duplicateNameReturnsConflict() throws Exception {
        when(service.create(any())).thenThrow(new ApiException(
                HttpStatus.CONFLICT,
                FieldDefinitionErrorCodes.NAME_ALREADY_EXISTS,
                "系统字段名称已存在"));

        mockMvc.perform(post("/field-definitions")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"penalty_amount",
                                  "displayName":"处罚金额",
                                  "fieldType":"NUMBER",
                                  "required":false,
                                  "options":[]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value(FieldDefinitionErrorCodes.NAME_ALREADY_EXISTS));
    }

    @Test
    void listsFieldDefinitions() throws Exception {
        when(service.list(0, 20)).thenReturn(new PageResponse<>(
                List.of(response(FieldDefinitionStatus.ACTIVE)), 0, 20, 1, 1));

        mockMvc.perform(get("/field-definitions")
                        .with(user(UserPrincipal.from(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].displayName").value("处罚金额"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void updatesMutableFields() throws Exception {
        FieldDefinitionResponse updated = new FieldDefinitionResponse(
                "field-1", "penalty_amount", "处罚数额", "更新后的说明",
                FieldType.NUMBER, false, List.of(), FieldDefinitionStatus.ACTIVE,
                Instant.parse("2026-08-22T01:00:00Z"), Instant.parse("2026-08-22T02:00:00Z"));
        when(service.update(anyString(), any())).thenReturn(updated);

        mockMvc.perform(put("/field-definitions/field-1")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName":"处罚数额",
                                  "description":"更新后的说明",
                                  "required":false,
                                  "options":[],
                                  "status":"ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("penalty_amount"))
                .andExpect(jsonPath("$.data.displayName").value("处罚数额"));
    }

    @Test
    void deleteChangesStatusToInactive() throws Exception {
        when(service.deactivate("field-1")).thenReturn(response(FieldDefinitionStatus.INACTIVE));

        mockMvc.perform(delete("/field-definitions/field-1")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    void annotatorCannotAccessFieldDefinitions() throws Exception {
        mockMvc.perform(get("/field-definitions")
                        .with(user(UserPrincipal.from(annotator))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH.FORBIDDEN"));
    }

    @Test
    void nameCannotBeChanged() throws Exception {
        mockMvc.perform(put("/field-definitions/field-1")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"changed_name",
                                  "displayName":"处罚金额",
                                  "description":null,
                                  "required":true,
                                  "options":[],
                                  "status":"ACTIVE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON.VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.locators[0].path").value("name"));
    }

    private static FieldDefinitionResponse response(FieldDefinitionStatus status) {
        Instant createdAt = Instant.parse("2026-08-22T01:00:00Z");
        return new FieldDefinitionResponse(
                "field-1",
                "penalty_amount",
                "处罚金额",
                "处罚金额字段",
                FieldType.NUMBER,
                true,
                List.of(),
                status,
                createdAt,
                createdAt);
    }

    private static UserDocument activeUser(String id, Role role) {
        Instant now = Instant.now();
        UserDocument user = new UserDocument(
                "测试用户", id, id, "$2a$12$hash", role, true, now, now);
        user.setId(id);
        return user;
    }
}
