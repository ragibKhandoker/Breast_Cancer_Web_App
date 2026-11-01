package com.cancerweb.backend.controller;

import java.util.List;
import java.util.Map;

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
import com.cancerweb.backend.dto.AdminUserDetail;
import com.cancerweb.backend.dto.ChangeRoleRequest;
import com.cancerweb.backend.dto.CreateUserRequest;
import com.cancerweb.backend.dto.PasswordResetResponse;
import com.cancerweb.backend.dto.UserResponse;
import com.cancerweb.backend.dto.VerificationRequest;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.UserService;
import com.cancerweb.backend.service.UserService.PasswordReset;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.audit.AuditEventType;
import com.cancerweb.backend.audit.AuditService;
import com.cancerweb.backend.audit.AuditService.Actor;

import jakarta.validation.Valid;

/** User management, patient verification and password resets. SecurityConfig limits /api/admin/** to ADMIN. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final AuditService audit;

    public AdminUserController(UserService userService, AuditService audit) {
        this.audit = audit;
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.listAll();
    }

    /** One account with its private profile details, for checking a patient's information. */
    @GetMapping("/{id}")
    public AdminUserDetail detail(@PathVariable String id, @AuthenticationPrincipal CurrentUser admin) {
        userService.requireNotOwnersAccount(admin.email(), id);
        return userService.detail(id);
    }

    /** Marks a patient's details VERIFIED or REJECTED (with a note), or puts them back to PENDING. */
    @PatchMapping("/{id}/verification")
    public AdminUserDetail setVerification(@PathVariable String id, @Valid @RequestBody VerificationRequest request,
            @AuthenticationPrincipal CurrentUser admin) {
        userService.requireNotOwnersAccount(admin.email(), id);
        userService.setVerification(admin.email(), id, request.status(), request.note());
        return userService.detail(id);
    }

    /** Sets a random temporary password and returns it once; the user must choose a new one after logging in. */
    @PostMapping("/{id}/password-reset")
    public PasswordResetResponse resetPassword(@PathVariable String id, @AuthenticationPrincipal CurrentUser admin) {
        userService.requireNotOwnersAccount(admin.email(), id);
        PasswordReset reset = userService.resetPassword(admin.id(), admin.email(), id);
        return new PasswordResetResponse(userService.toResponse(reset.user()), reset.temporaryPassword());
    }

    /** The only way to create DOCTOR and ADMIN accounts. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal CurrentUser admin) {
        User created = userService.createAccount(request.fullName(), request.email(), request.password(), request.role());
        audit.record(AuditEventType.ACCOUNT_CREATED_BY_ADMIN, Actor.of(admin), "USER", created.id(),
                Map.of("role", created.role().name()));
        return UserResponse.from(created);
    }

    /** Disable or re-enable an account. A disabled account cannot log in, and its current logins stop working. */
    @PatchMapping("/{id}/status")
    public UserResponse setStatus(@PathVariable String id, @Valid @RequestBody AccountStatusRequest request,
            @AuthenticationPrincipal CurrentUser admin) {
        userService.requireNotOwnersAccount(admin.email(), id);
        return userService.toResponse(userService.setDisabled(admin.id(), id, request.disabled()));
    }

    @PatchMapping("/{id}/role")
    public UserResponse changeRole(@PathVariable String id, @Valid @RequestBody ChangeRoleRequest request,
            @AuthenticationPrincipal CurrentUser admin) {
        userService.requireNotOwnersAccount(admin.email(), id);
        return userService.toResponse(userService.changeRole(admin.id(), id, request.role()));
    }
}
