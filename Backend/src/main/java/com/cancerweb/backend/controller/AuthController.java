package com.cancerweb.backend.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.dto.LoginRequest;
import com.cancerweb.backend.dto.RegisterRequest;
import com.cancerweb.backend.dto.UserResponse;
import com.cancerweb.backend.security.AuthCookies;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.AuthService;
import com.cancerweb.backend.service.AuthService.Login;
import com.cancerweb.backend.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final AuthCookies cookies;

    public AuthController(AuthService authService, UserService userService, AuthCookies cookies) {
        this.authService = authService;
        this.userService = userService;
        this.cookies = cookies;
    }

    /**
     * Sets the XSRF-TOKEN cookie. The frontend calls this before its first POST, since the token is otherwise
     * only created on demand.
     */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(authService.register(request));
    }

    /** On success the token is set as an HttpOnly cookie; the body is the user's profile, without the token. */
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
        Login login = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(login.token().value(), login.token().expiresAt()).toString())
                .body(UserResponse.from(login.user()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(cookies.read(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal CurrentUser me) {
        return UserResponse.from(userService.get(me.id()));
    }
}
