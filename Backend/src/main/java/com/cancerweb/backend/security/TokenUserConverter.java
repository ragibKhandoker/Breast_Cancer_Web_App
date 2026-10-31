package com.cancerweb.backend.security;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.UserRepository;

/**
 * Turns a verified token into the request's {@link CurrentUser}. The role comes from the database, not from the
 * token, so an administrator's role change applies to that user's next request, and a password change ends
 * older logins.
 */
@Component
public class TokenUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    public TokenUserConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = users.findById(jwt.getSubject())
                .orElseThrow(() -> new InvalidBearerTokenException("The account for this token no longer exists"));
        if (user.isDisabled()) {
            throw new InvalidBearerTokenException("The account has been disabled");
        }
        // A password change signs out every login from before it.
        if (user.passwordChangedAt() != null
                && (jwt.getIssuedAt() == null || jwt.getIssuedAt().isBefore(user.passwordChangedAt()))) {
            throw new InvalidBearerTokenException("Token was issued before the last password change");
        }
        if (user.isTemporaryPasswordExpired(Instant.now())) {
            throw new InvalidBearerTokenException("The temporary password has expired");
        }
        CurrentUser principal = new CurrentUser(user.id(), user.email(), user.fullName(), user.role());
        List<SimpleGrantedAuthority> authorities = new ArrayList<>(List.of(new SimpleGrantedAuthority(user.role().authority())));
        // Signed in with an administrator's temporary password: PasswordChangeRequiredFilter allows only
        // choosing a new password until then.
        if (user.isPasswordChangeRequired()) {
            authorities.add(new SimpleGrantedAuthority(PasswordChangeRequiredFilter.AUTHORITY));
        }
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }
}
