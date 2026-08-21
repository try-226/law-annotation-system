package com.law.annotation.user;

import com.law.annotation.auth.UserPrincipal;
import com.law.annotation.common.enums.Role;
import com.law.annotation.common.response.ApiResponse;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.user.dto.CreateUserRequest;
import com.law.annotation.user.dto.ResetPasswordRequest;
import com.law.annotation.user.dto.UpdateUserRequest;
import com.law.annotation.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(userService.listUsers(search, role, enabled, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUser(@PathVariable String id) {
        return ApiResponse.success(userService.getUser(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success(userService.createUser(
                request.name(),
                request.loginAccount(),
                request.initialPassword(),
                request.role()));
    }

    @PatchMapping("/{id}")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success(userService.updateName(id, request.name()));
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(principal.id(), id, request.newPassword(), request.confirmPassword());
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<UserResponse> enableUser(@PathVariable String id) {
        return ApiResponse.success(userService.enableUser(id));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<UserResponse> disableUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id) {
        return ApiResponse.success(userService.disableUser(principal.id(), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id) {
        userService.deleteUser(principal.id(), id);
        return ApiResponse.success(null);
    }
}
