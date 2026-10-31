package com.cancerweb.backend.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.dto.ChangeEmailRequest;
import com.cancerweb.backend.dto.ChangePasswordRequest;
import com.cancerweb.backend.dto.ProfileResponse;
import com.cancerweb.backend.dto.UpdateProfileRequest;
import com.cancerweb.backend.security.AuthCookies;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.ProfileService;
import com.cancerweb.backend.service.ProfileService.PasswordChange;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** The signed-in user's own account, for every role. There is no id in the path: it is always "me". */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profiles;
    private final AuthCookies cookies;

    public ProfileController(ProfileService profiles, AuthCookies cookies) {
        this.profiles = profiles;
        this.cookies = cookies;
    }

    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal CurrentUser me) {
        return ProfileResponse.from(profiles.get(me.id()));
    }

    @PutMapping
    public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest request, @AuthenticationPrincipal CurrentUser me) {
        return ProfileResponse.from(profiles.updateProfile(me.id(), request));
    }

    @PutMapping("/email")
    public ProfileResponse changeEmail(@Valid @RequestBody ChangeEmailRequest request, @AuthenticationPrincipal CurrentUser me) {
        return ProfileResponse.from(profiles.changeEmail(me.id(), request));
    }

    /** Other devices are signed out; this browser gets a new login cookie and stays signed in. */
    @PutMapping("/password")
    public ResponseEntity<ProfileResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal CurrentUser me, HttpServletRequest httpRequest) {
        PasswordChange change = profiles.changePassword(me.id(), request, cookies.read(httpRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(change.token().value(), change.token().expiresAt(), httpRequest).toString())
                .body(ProfileResponse.from(change.user()));
    }
}
