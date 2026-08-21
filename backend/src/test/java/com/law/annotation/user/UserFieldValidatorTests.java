package com.law.annotation.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.law.annotation.common.exception.ApiException;
import org.junit.jupiter.api.Test;

class UserFieldValidatorTests {

    private final UserFieldValidator validator = new UserFieldValidator();

    @Test
    void normalizesAccountWithoutChangingDisplayAccount() {
        String account = validator.validateLoginAccount(" Admin.User ", "loginAccount");

        assertThat(account).isEqualTo("Admin.User");
        assertThat(validator.normalizeAccount(account)).isEqualTo("admin.user");
    }

    @Test
    void rejectsInvalidAccountCharacters() {
        assertThatThrownBy(() -> validator.validateLoginAccount("管理 员", "loginAccount"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("COMMON.VALIDATION_FAILED");
    }

    @Test
    void validatesPasswordWithoutTrimming() {
        validator.validatePassword("Abc!123", "password");

        assertThatThrownBy(() -> validator.validatePassword(" abc123", "password"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validatePassword("abc12", "password"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void trimsAndValidatesName() {
        assertThat(validator.validateName(" 系统-管理员 ", "name"))
                .isEqualTo("系统-管理员");
        assertThatThrownBy(() -> validator.validateName("管理员@", "name"))
                .isInstanceOf(ApiException.class);
    }
}
