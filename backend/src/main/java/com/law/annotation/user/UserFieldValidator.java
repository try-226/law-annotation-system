package com.law.annotation.user;

import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class UserFieldValidator {

    private static final String VALIDATION_FAILED = "COMMON.VALIDATION_FAILED";
    private static final Pattern ACCOUNT_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{3,31}$");
    private static final Pattern NAME_PATTERN =
            Pattern.compile("^[\\p{L}\\p{N}](?:[\\p{L}\\p{N} \\-·]*[\\p{L}\\p{N}])?$");

    public String validateName(String value, String path) {
        if (value == null) {
            throw validation(path, "姓名不能为空");
        }
        String normalized = value.trim();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > 50 || !NAME_PATTERN.matcher(normalized).matches()) {
            throw validation(path, "姓名须为1至50个字符，仅允许字母、数字、空格和常用连接符");
        }
        return normalized;
    }

    public String validateLoginAccount(String value, String path) {
        if (value == null) {
            throw validation(path, "登录账号不能为空");
        }
        String normalized = value.trim();
        if (!ACCOUNT_PATTERN.matcher(normalized).matches()) {
            throw validation(path, "登录账号须为4至32位，并仅包含字母、数字、点、下划线或连字符");
        }
        return normalized;
    }

    public String normalizeAccount(String account) {
        return account.trim().toLowerCase(Locale.ROOT);
    }

    public void validatePassword(String value, String path) {
        if (value == null) {
            throw validation(path, "密码不能为空");
        }
        int length = value.codePointCount(0, value.length());
        boolean containsForbiddenCharacter = value.codePoints()
                .anyMatch(codePoint -> Character.isWhitespace(codePoint) || Character.isISOControl(codePoint));
        if (length < 6 || length > 64 || containsForbiddenCharacter) {
            throw validation(path, "密码须为6至64个字符，且不得包含空白或控制字符");
        }
    }

    public void validatePasswordConfirmation(String password, String confirmation, String path) {
        if (confirmation == null || !password.equals(confirmation)) {
            throw validation(path, "两次输入的密码不一致");
        }
    }

    private static ApiException validation(String path, String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                VALIDATION_FAILED,
                "请求参数校验失败",
                List.of(new ErrorLocator(path, message)));
    }
}
