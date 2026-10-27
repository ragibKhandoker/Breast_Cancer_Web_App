package com.cancerweb.backend.security;

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
        // A password change signs out every login from before it.
        if (user.passwordChangedAt() != null
                && (jwt.getIssuedAt() == null || jwt.getIssuedAt().isBefore(user.passwordChangedAt()))) {
            throw new InvalidBearerTokenException("Token was issued before the last password change");
        }
        CurrentUser principal = new CurrentUser(user.id(), user.email(), user.fullName(), user.role());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority(user.role().authority())));
    }
}
