package com.law.annotation.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.law.LawCreationService;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawDomainRules;
import com.law.annotation.law.LawRepository;
import com.law.annotation.law.InitialLawCreation;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import com.law.annotation.user.UserService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.HttpStatus;

class DemoSeedRunnerTests {

    private final UserService userService = mock(UserService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final LawCreationService lawCreationService = mock(LawCreationService.class);
    private final LawRepository lawRepository = mock(LawRepository.class);
    private final BootstrapSeedStateRepository seedStateRepository =
            mock(BootstrapSeedStateRepository.class);

    @Test
    void disabledSeedDoesNotReadOrCreateDemoData() throws Exception {
        runner(false).run(new DefaultApplicationArguments());

        verify(userService, never()).findForAuthentication(anyString());
        verify(userService, never()).createUser(anyString(), anyString(), anyString(), any());
        verify(lawRepository, never()).findByNormalizedName(anyString());
        verify(seedStateRepository, never()).findById(anyString());
    }

    @Test
    void enabledSeedCreatesAnnotatorAndEightArticleLaw() throws Exception {
        UserDocument admin = user("admin-id", Role.ADMIN, true);
        when(userService.findForAuthentication("annotator")).thenReturn(Optional.empty());
        LawDocument createdLaw = law("law-id");
        when(seedStateRepository.findById(DemoSeedRunner.DEMO_LAW_MARKER))
                .thenReturn(Optional.empty());
        when(lawRepository.findByNormalizedName(normalizedDemoLawName())).thenReturn(Optional.empty());
        when(userRepository.findFirstByRoleOrderByCreatedAtAscIdAsc(Role.ADMIN))
                .thenReturn(Optional.of(admin));
        when(lawCreationService.createInitialLaw(
                anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new InitialLawCreation(createdLaw, null));

        runner(true).run(new DefaultApplicationArguments());

        verify(userService).createUser(
                DemoSeedData.ANNOTATOR_NAME, "annotator", "annotator123", Role.ANNOTATOR);
        verify(lawCreationService).createInitialLaw(
                DemoSeedData.LAW_NAME,
                DemoSeedData.ISSUING_AUTHORITY,
                DemoSeedData.PUBLICATION_DATE,
                DemoSeedData.validityStatus(),
                DemoSeedData.structure(),
                DemoSeedData.articles(),
                "admin-id");
        verify(seedStateRepository).insert(any(BootstrapSeedStateDocument.class));
        assertThat(DemoSeedData.articles()).hasSize(8);
        assertThat(DemoSeedData.structure()).hasSize(2);
    }

    @Test
    void restartKeepsExistingAnnotatorAndLawWithoutResettingThem() throws Exception {
        UserDocument annotator = user("annotator-id", Role.ANNOTATOR, true);
        LawDocument law = law("law-id");
        when(userService.findForAuthentication("annotator")).thenReturn(Optional.of(annotator));
        when(seedStateRepository.findById(DemoSeedRunner.DEMO_LAW_MARKER))
                .thenReturn(Optional.of(state("law-id")));
        when(lawRepository.findById("law-id")).thenReturn(Optional.of(law));

        runner(true).run(new DefaultApplicationArguments());

        verify(userService, never()).createUser(anyString(), anyString(), anyString(), any());
        verify(lawCreationService, never()).createInitialLaw(
                anyString(), anyString(), any(), any(), any(), any(), anyString());
        verify(userRepository, never()).findFirstByRoleOrderByCreatedAtAscIdAsc(any());
        verify(lawRepository, never()).findByNormalizedName(anyString());
    }

    @Test
    void existingUserAndMissingLawOnlyCreatesLaw() throws Exception {
        LawDocument createdLaw = law("law-id");
        when(userService.findForAuthentication("annotator"))
                .thenReturn(Optional.of(user("annotator-id", Role.ANNOTATOR, true)));
        when(seedStateRepository.findById(DemoSeedRunner.DEMO_LAW_MARKER))
                .thenReturn(Optional.empty());
        when(lawRepository.findByNormalizedName(normalizedDemoLawName())).thenReturn(Optional.empty());
        when(userRepository.findFirstByRoleOrderByCreatedAtAscIdAsc(Role.ADMIN))
                .thenReturn(Optional.of(user("admin-id", Role.ADMIN, true)));
        when(lawCreationService.createInitialLaw(
                anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new InitialLawCreation(createdLaw, null));

        runner(true).run(new DefaultApplicationArguments());

        verify(userService, never()).createUser(anyString(), anyString(), anyString(), any());
        verify(lawCreationService).createInitialLaw(
                anyString(), anyString(), any(), any(), any(), any(), anyString());
    }

    @Test
    void existingLawAndMissingUserOnlyCreatesUser() throws Exception {
        LawDocument existingLaw = law("law-id");
        when(userService.findForAuthentication("annotator")).thenReturn(Optional.empty());
        when(seedStateRepository.findById(DemoSeedRunner.DEMO_LAW_MARKER))
                .thenReturn(Optional.of(state("law-id")));
        when(lawRepository.findById("law-id")).thenReturn(Optional.of(existingLaw));

        runner(true).run(new DefaultApplicationArguments());

        verify(userService).createUser(
                DemoSeedData.ANNOTATOR_NAME, "annotator", "annotator123", Role.ANNOTATOR);
        verify(lawCreationService, never()).createInitialLaw(
                anyString(), anyString(), any(), any(), any(), any(), anyString());
    }

    @Test
    void wrongRoleExistingDemoAccountFailsWithoutChangingIt() {
        when(userService.findForAuthentication("annotator"))
                .thenReturn(Optional.of(user("user-id", Role.ADMIN, true)));

        assertThatThrownBy(() -> runner(true).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("角色不是ANNOTATOR");
        verify(userService, never()).createUser(anyString(), anyString(), anyString(), any());
    }

    @Test
    void disabledExistingDemoAccountFailsWithoutEnablingIt() {
        when(userService.findForAuthentication("annotator"))
                .thenReturn(Optional.of(user("user-id", Role.ANNOTATOR, false)));

        assertThatThrownBy(() -> runner(true).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已停用");
        verify(userService, never()).createUser(anyString(), anyString(), anyString(), any());
    }

    @Test
    void enabledSeedWithoutAdminFailsBeforeCreatingLaw() {
        when(userService.findForAuthentication("annotator"))
                .thenReturn(Optional.of(user("annotator-id", Role.ANNOTATOR, true)));
        when(seedStateRepository.findById(DemoSeedRunner.DEMO_LAW_MARKER))
                .thenReturn(Optional.empty());
        when(lawRepository.findByNormalizedName(normalizedDemoLawName())).thenReturn(Optional.empty());
        when(userRepository.findFirstByRoleOrderByCreatedAtAscIdAsc(Role.ADMIN))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> runner(true).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不存在可作为创建人的ADMIN");
        verify(lawCreationService, never()).createInitialLaw(
                anyString(), anyString(), any(), any(), any(), any(), anyString());
    }

    @Test
    void stableStateWithoutItsLawFailsInsteadOfCreatingAnotherLaw() {
        when(userService.findForAuthentication("annotator"))
                .thenReturn(Optional.of(user("annotator-id", Role.ANNOTATOR, true)));
        when(seedStateRepository.findById(DemoSeedRunner.DEMO_LAW_MARKER))
                .thenReturn(Optional.of(state("missing-law-id")));
        when(lawRepository.findById("missing-law-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> runner(true).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("稳定初始化状态指向的演示法律不存在")
                .hasMessageContaining("拒绝静默重建");
        verify(lawCreationService, never()).createInitialLaw(
                anyString(), anyString(), any(), any(), any(), any(), anyString());
    }

    @Test
    void failedStateWriteRecoversExistingCreatedLawOnNextStartup() throws Exception {
        LawDocument createdLaw = law("law-id");
        when(userService.findForAuthentication("annotator"))
                .thenReturn(Optional.of(user("annotator-id", Role.ANNOTATOR, true)));
        when(seedStateRepository.findById(DemoSeedRunner.DEMO_LAW_MARKER))
                .thenReturn(Optional.empty());
        when(lawRepository.findByNormalizedName(normalizedDemoLawName()))
                .thenReturn(Optional.empty(), Optional.of(createdLaw));
        when(userRepository.findFirstByRoleOrderByCreatedAtAscIdAsc(Role.ADMIN))
                .thenReturn(Optional.of(user("admin-id", Role.ADMIN, true)));
        when(lawCreationService.createInitialLaw(
                anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new InitialLawCreation(createdLaw, null));
        when(seedStateRepository.insert(any(BootstrapSeedStateDocument.class)))
                .thenThrow(new IllegalStateException("temporary write failure"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> runner(true).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无法持久化稳定初始化状态");

        runner(true).run(new DefaultApplicationArguments());

        verify(lawCreationService, times(1)).createInitialLaw(
                anyString(), anyString(), any(), any(), any(), any(), anyString());
        verify(seedStateRepository, times(2)).insert(any(BootstrapSeedStateDocument.class));
    }

    @Test
    void invalidDemoCredentialsFailWithoutLeakingPassword() {
        when(userService.findForAuthentication("annotator")).thenReturn(Optional.empty());
        when(userService.createUser(
                DemoSeedData.ANNOTATOR_NAME, "annotator", "annotator123", Role.ANNOTATOR))
                .thenThrow(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "COMMON.VALIDATION_FAILED",
                        "请求参数校验失败"));

        assertThatThrownBy(() -> runner(true).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("演示数据初始化失败")
                .hasMessageNotContaining("annotator123");
    }

    @Test
    void springRunnerOrderingExecutesBootstrapAdminBeforeDemoSeed() throws Exception {
        List<String> sequence = new ArrayList<>();
        LawDocument createdLaw = law("law-id");
        InitAdminProperties adminProperties = new InitAdminProperties(
                true, "admin", "admin123", "系统管理员");
        when(userService.countAdmins()).thenReturn(0L);
        when(userService.createUser("系统管理员", "admin", "admin123", Role.ADMIN))
                .thenAnswer(invocation -> {
                    sequence.add("admin");
                    return null;
                });
        when(userService.findForAuthentication("annotator")).thenReturn(Optional.empty());
        when(userService.createUser(
                DemoSeedData.ANNOTATOR_NAME, "annotator", "annotator123", Role.ANNOTATOR))
                .thenAnswer(invocation -> {
                    sequence.add("annotator");
                    return null;
                });
        when(seedStateRepository.findById(DemoSeedRunner.DEMO_LAW_MARKER))
                .thenReturn(Optional.empty());
        when(lawRepository.findByNormalizedName(normalizedDemoLawName())).thenReturn(Optional.empty());
        when(userRepository.findFirstByRoleOrderByCreatedAtAscIdAsc(Role.ADMIN))
                .thenAnswer(invocation -> {
                    sequence.add("law");
                    return Optional.of(user("admin-id", Role.ADMIN, true));
                });
        when(lawCreationService.createInitialLaw(
                anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(new InitialLawCreation(createdLaw, null));

        List<ApplicationRunner> runners = new ArrayList<>(List.of(
                runner(true), new BootstrapAdminRunner(userService, adminProperties)));
        AnnotationAwareOrderComparator.sort(runners);
        for (ApplicationRunner applicationRunner : runners) {
            applicationRunner.run(new DefaultApplicationArguments());
        }

        assertThat(sequence).containsExactly("admin", "annotator", "law");
        InOrder order = inOrder(userService, userRepository);
        order.verify(userService).createUser("系统管理员", "admin", "admin123", Role.ADMIN);
        order.verify(userService).createUser(
                DemoSeedData.ANNOTATOR_NAME, "annotator", "annotator123", Role.ANNOTATOR);
        order.verify(userRepository).findFirstByRoleOrderByCreatedAtAscIdAsc(Role.ADMIN);
    }

    private DemoSeedRunner runner(boolean enabled) {
        return new DemoSeedRunner(
                new DemoSeedProperties(enabled, "annotator", "annotator123"),
                userService,
                userRepository,
                lawCreationService,
                lawRepository,
                seedStateRepository);
    }

    private static UserDocument user(String id, Role role, boolean enabled) {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        UserDocument user = new UserDocument(
                role == Role.ADMIN ? "系统管理员" : "演示标注员",
                role == Role.ADMIN ? "admin" : "annotator",
                role == Role.ADMIN ? "admin" : "annotator",
                "hash",
                role,
                enabled,
                now,
                now);
        user.setId(id);
        return user;
    }

    private static String normalizedDemoLawName() {
        return LawDomainRules.normalizeLawName(DemoSeedData.LAW_NAME);
    }

    private static LawDocument law(String id) {
        LawDocument law = mock(LawDocument.class);
        when(law.getId()).thenReturn(id);
        return law;
    }

    private static BootstrapSeedStateDocument state(String lawId) {
        return new BootstrapSeedStateDocument(
                DemoSeedRunner.DEMO_LAW_MARKER,
                lawId,
                Instant.parse("2026-08-19T00:00:00Z"));
    }
}
