package com.law.annotation.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.ApiException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTests {

    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
    private final UserBusinessUsagePort businessUsagePort =
            org.mockito.Mockito.mock(UserBusinessUsagePort.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                mongoTemplate,
                passwordEncoder,
                new UserFieldValidator(),
                businessUsagePort);
        when(userRepository.save(any(UserDocument.class))).thenAnswer(invocation -> {
            UserDocument user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId("generated-id");
            }
            return user;
        });
    }

    @Test
    void createsEnabledUserWithNormalizedAccountAndPasswordHash() {
        when(userRepository.existsByNormalizedAccount("admin.user")).thenReturn(false);

        var response = userService.createUser(
                " 系统管理员 ", "Admin.User", "abc123!", Role.ADMIN);

        org.mockito.ArgumentCaptor<UserDocument> captor =
                org.mockito.ArgumentCaptor.forClass(UserDocument.class);
        verify(userRepository).save(captor.capture());
        UserDocument saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("系统管理员");
        assertThat(saved.getLoginAccount()).isEqualTo("Admin.User");
        assertThat(saved.getNormalizedAccount()).isEqualTo("admin.user");
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getPasswordHash()).isNotEqualTo("abc123!");
        assertThat(passwordEncoder.matches("abc123!", saved.getPasswordHash())).isTrue();
        assertThat(response.loginAccount()).isEqualTo("Admin.User");
    }

    @Test
    void rejectsCaseInsensitiveDuplicateAccount() {
        when(userRepository.existsByNormalizedAccount("admin")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(
                        "系统管理员", "Admin", "abc123", Role.ADMIN))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(UserErrorCodes.ACCOUNT_ALREADY_EXISTS);

        verify(userRepository, never()).save(any());
    }

    @Test
    void changesPasswordOnlyWhenOldPasswordMatches() {
        UserDocument user = user("u1", Role.ANNOTATOR, true, "old123");
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        userService.changePassword("u1", "old123", "new123!", "new123!");

        assertThat(passwordEncoder.matches("new123!", user.getPasswordHash())).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void rejectsIncorrectOldPassword() {
        UserDocument user = user("u1", Role.ANNOTATOR, true, "old123");
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changePassword(
                        "u1", "wrong1", "new123!", "new123!"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("AUTH.OLD_PASSWORD_INCORRECT");
    }

    @Test
    void cannotDisableSelf() {
        assertThatThrownBy(() -> userService.disableUser("admin", "admin"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(UserErrorCodes.SELF_ACTION_FORBIDDEN);
    }

    @Test
    void cannotDisableLastEnabledAdmin() {
        UserDocument target = user("admin2", Role.ADMIN, true, "abc123");
        when(userRepository.findById("admin2")).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.disableUser("admin1", "admin2"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(UserErrorCodes.LAST_ENABLED_ADMIN);
    }

    @Test
    void taskAndReviewUsagePortsBlockDisable() {
        UserDocument annotator = user("a1", Role.ANNOTATOR, true, "abc123");
        when(userRepository.findById("a1")).thenReturn(Optional.of(annotator));
        when(businessUsagePort.hasActiveTask("a1")).thenReturn(true);

        assertThatThrownBy(() -> userService.disableUser("admin", "a1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(UserErrorCodes.ACTIVE_TASK_EXISTS);

        UserDocument reviewer = user("r1", Role.ADMIN, true, "abc123");
        when(userRepository.findById("r1")).thenReturn(Optional.of(reviewer));
        when(businessUsagePort.hasUnfinishedReviewRound("r1")).thenReturn(true);

        assertThatThrownBy(() -> userService.disableUser("admin", "r1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(UserErrorCodes.UNFINISHED_REVIEW_EXISTS);
    }

    @Test
    void onlyUserWithoutBusinessHistoryCanBeDeleted() {
        UserDocument target = user("a1", Role.ANNOTATOR, false, "abc123");
        when(userRepository.findById("a1")).thenReturn(Optional.of(target));
        when(businessUsagePort.hasBusinessHistory("a1")).thenReturn(true);

        assertThatThrownBy(() -> userService.deleteUser("admin", "a1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(UserErrorCodes.BUSINESS_HISTORY_EXISTS);
        verify(userRepository, never()).delete(target);

        when(businessUsagePort.hasBusinessHistory("a1")).thenReturn(false);
        userService.deleteUser("admin", "a1");
        verify(userRepository).delete(target);
    }

    private UserDocument user(String id, Role role, boolean enabled, String password) {
        Instant now = Instant.now();
        UserDocument user = new UserDocument(
                "测试用户",
                "test-" + id,
                "test-" + id,
                passwordEncoder.encode(password),
                role,
                enabled,
                now,
                now);
        user.setId(id);
        return user;
    }
}
