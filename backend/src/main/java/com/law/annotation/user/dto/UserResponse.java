package com.law.annotation.user.dto;

import com.law.annotation.common.enums.Role;
import com.law.annotation.user.UserDocument;
import java.time.Instant;

public record UserResponse(
        String id,
        String name,
        String loginAccount,
        Role role,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(UserDocument user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getLoginAccount(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
