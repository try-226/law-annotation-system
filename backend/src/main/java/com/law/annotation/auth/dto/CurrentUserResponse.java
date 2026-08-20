package com.law.annotation.auth.dto;

import com.law.annotation.common.enums.Role;
import com.law.annotation.user.UserDocument;

public record CurrentUserResponse(
        String id,
        String name,
        String loginAccount,
        Role role) {

    public static CurrentUserResponse from(UserDocument user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getName(),
                user.getLoginAccount(),
                user.getRole());
    }
}
