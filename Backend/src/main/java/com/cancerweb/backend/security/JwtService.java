package com.cancerweb.backend.security;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.cancerweb.backend.model.User;

/** Issues the signed login token. Verification is Spring Security's JwtDecoder, configured in SecurityConfig. */
@Service
public class JwtService {

    static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;

    private final JwtEncoder encoder;
    private final AuthProperties properties;

    public JwtService(JwtEncoder encoder, AuthProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    /** The token's {@code jti} is random, so a single login can be revoked on logout. */
    public IssuedToken issue(User user) {
        return issue(user, null);
    }

    /** As above, but expiring no later than {@code notAfter} (null: the usual lifetime). */
    public IssuedToken issue(User user, Instant notAfter) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.tokenTtl());
        if (notAfter != null && notAfter.isBefore(expiresAt)) {
            expiresAt = notAfter;
        }
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.id())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("role", user.role().name())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(ALGORITHM).build(), claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
