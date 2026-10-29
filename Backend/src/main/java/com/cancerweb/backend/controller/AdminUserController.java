package com.cancerweb.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.dto.AccountStatusRequest;
import com.cancerweb.backend.dto.ChangeRoleRequest;
import com.cancerweb.backend.dto.CreateUserRequest;
import com.cancerweb.backend.dto.UserResponse;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.UserService;

import jakarta.validation.Valid;

/** User management. SecurityConfig limits /api/admin/** to ADMIN. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.listAll();
    }

    /** The only way to create DOCTOR and ADMIN accounts. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return UserResponse.from(
                userService.createAccount(request.fullName(), request.email(), request.password(), request.role()));
    }

    /** Disable or re-enable an account. A disabled account cannot log in, and its current logins stop working. */
    @PatchMapping("/{id}/status")
    public UserResponse setStatus(@PathVariable String id, @Valid @RequestBody AccountStatusRequest request,
            @AuthenticationPrincipal CurrentUser admin) {
        return UserResponse.from(userService.setDisabled(admin.id(), id, request.disabled()));
    }

    @PatchMapping("/{id}/role")
    public UserResponse changeRole(@PathVariable String id, @Valid @RequestBody ChangeRoleRequest request,
            @AuthenticationPrincipal CurrentUser admin) {
        return UserResponse.from(userService.changeRole(admin.id(), id, request.role()));
    }
}
