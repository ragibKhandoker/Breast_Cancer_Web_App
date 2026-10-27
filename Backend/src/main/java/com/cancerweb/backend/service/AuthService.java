package com.cancerweb.backend.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import com.cancerweb.backend.dto.LoginRequest;
import com.cancerweb.backend.dto.RegisterRequest;
import com.cancerweb.backend.model.RevokedToken;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.RevokedTokenRepository;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.security.JwtService;
import com.cancerweb.backend.security.JwtService.IssuedToken;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserService userService;
    private final UserRepository users;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtDecoder jwtDecoder;
    private final RevokedTokenRepository revokedTokens;

    public AuthService(UserService userService, UserRepository users, AuthenticationManager authenticationManager,
            JwtService jwtService, JwtDecoder jwtDecoder, RevokedTokenRepository revokedTokens) {
        this.userService = userService;
        this.users = users;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtDecoder = jwtDecoder;
        this.revokedTokens = revokedTokens;
    }

    /** Public sign-up always creates a PATIENT; doctors and administrators are created by an administrator. */
    public User register(RegisterRequest request) {
        return userService.createAccount(request.fullName(), request.email(), request.password(), Role.PATIENT);
    }

    /**
     * Checks the password with BCrypt and issues a token. An unknown email and a wrong password give the same
     * BadCredentialsException (and take about as long), so the answer does not reveal which accounts exist.
     */
    public Login login(LoginRequest request) {
        if (UserService.passwordTooLong(request.password())) {
            throw new BadCredentialsException("Bad credentials");
        }
        Authentication result = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                UserService.normalizeEmail(request.email()), request.password()));
        String userId = ((UserDetails) result.getPrincipal()).getUsername();
        User user = users.findById(userId).orElseThrow(() -> new BadCredentialsException("Bad credentials"));
        return new Login(user, jwtService.issue(user));
    }

    /**
     * Revokes the token so it stops working at once, even if a copy of it exists elsewhere. A token that is
     * already invalid, expired or revoked needs nothing more, so logout always succeeds.
     */
    public void logout(String rawToken) {
        if (rawToken == null) {
            return;
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(rawToken);
        } catch (JwtException e) {
            return;
        }
        revokedTokens.save(new RevokedToken(jwt.getId(), jwt.getSubject(), jwt.getExpiresAt(), Instant.now()));
        log.debug("Revoked token {} for user {}", jwt.getId(), jwt.getSubject());
    }

    public record Login(User user, IssuedToken token) {
    }
}
