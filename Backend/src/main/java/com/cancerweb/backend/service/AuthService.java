package com.cancerweb.backend.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
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
import com.cancerweb.backend.model.Verification;
import com.cancerweb.backend.model.VerificationStatus;
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
    private final NotificationService notifications;

    public AuthService(UserService userService, UserRepository users, AuthenticationManager authenticationManager,
            JwtService jwtService, JwtDecoder jwtDecoder, RevokedTokenRepository revokedTokens,
            NotificationService notifications) {
        this.userService = userService;
        this.users = users;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtDecoder = jwtDecoder;
        this.revokedTokens = revokedTokens;
        this.notifications = notifications;
    }

    /**
     * Public sign-up creates a PATIENT or a DOCTOR; administrators are only ever created by an administrator. A
     * self-registered doctor starts PENDING: patients cannot find them or share reports with them until an
     * administrator verifies the account (or approves their Breast Surgeon profile).
     */
    public User register(RegisterRequest request) {
        Verification verification = request.role() == Role.DOCTOR
                ? new Verification(VerificationStatus.PENDING, null, null, null)
                : null;
        User user = userService.createAccount(request.fullName(), request.email(), request.password(), request.role(), verification);
        boolean doctor = user.role() == Role.DOCTOR;
        notifications.notifyAdmins(doctor ? "NEW_DOCTOR" : "NEW_PATIENT", doctor ? "New doctor registered" : "New patient registered",
                user.fullName() + " (" + user.email() + ") " + (doctor ? "registered as a doctor and is waiting for verification."
                        : "created a patient account."),
                "/admin?tab=verification", null);
        return user;
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
        // Checked only after the password matched, so a disabled account is not revealed to someone guessing.
        if (user.isDisabled()) {
            throw new DisabledException("Account disabled");
        }
        // Also only after the password matched: an administrator's temporary password works for a limited time.
        if (user.isTemporaryPasswordExpired(Instant.now())) {
            throw new CredentialsExpiredException("Temporary password expired");
        }
        userService.recordLogin(user.id());
        return new Login(user, jwtService.issue(user));
    }

    /**
     * A new tab's own login for the same user, copied from another tab's login ({@code rawToken}, read from that
     * tab's cookie). The new token has its own id, so logging out in either tab leaves the other signed in, and
     * it expires no later than the original, so copying cannot extend a login. Anything wrong with the original
     * (expired, revoked, disabled account, older than the last password change) gives BadCredentialsException.
     */
    public Login fork(String rawToken) {
        if (rawToken == null) {
            throw new BadCredentialsException("No login to copy");
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(rawToken);
        } catch (JwtException e) {
            throw new BadCredentialsException("No login to copy");
        }
        User user = users.findById(jwt.getSubject()).orElseThrow(() -> new BadCredentialsException("No login to copy"));
        boolean stale = user.passwordChangedAt() != null && (jwt.getIssuedAt() == null || jwt.getIssuedAt().isBefore(user.passwordChangedAt()));
        if (user.isDisabled() || stale || user.isTemporaryPasswordExpired(Instant.now())) {
            throw new BadCredentialsException("No login to copy");
        }
        return new Login(user, jwtService.issue(user, jwt.getExpiresAt()));
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
