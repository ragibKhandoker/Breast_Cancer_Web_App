package com.cancerweb.backend.security;

import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.cancerweb.backend.repository.RevokedTokenRepository;

/**
 * Who may call what. Sign-in is a signed JWT (HS256) in the HttpOnly {@code bc_auth} cookie; there is no server
 * session. Because a cookie is sent automatically, state-changing requests also need the CSRF token: Spring
 * puts it in the readable XSRF-TOKEN cookie and axios echoes it back in the X-XSRF-TOKEN header.
 *
 * <p>Roles gate whole endpoints here. Which records a patient or doctor may see is decided per record in
 * PredictionService, using the id of the signed-in user.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemResponses problems, AuthCookies cookies,
            JwtDecoder jwtDecoder, TokenUserConverter tokenUserConverter) throws Exception {
        http
                .csrf(csrf -> csrf.spa())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/logout", "/api/auth/fork",
                                "/api/auth/password-help", "/api/auth/password-help/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/contact").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/site-settings").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/model").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // A patient's or doctor's own conversation with the administrators.
                        .requestMatchers("/api/messages", "/api/messages/**").hasAnyRole("PATIENT", "DOCTOR")
                        .requestMatchers(HttpMethod.GET, "/api/doctors").hasRole("PATIENT")
                        // Breast Surgeons: /profile is the signed-in doctor's own; approved surgeons, their
                        // calendars and pictures are public. The literal /profile rule must come first.
                        .requestMatchers("/api/doctors/breast-surgeons/profile", "/api/doctors/breast-surgeons/profile/**")
                                .hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.GET, "/api/doctors/breast-surgeons", "/api/doctors/breast-surgeons/*",
                                "/api/doctors/breast-surgeons/*/slots", "/api/doctors/breast-surgeons/*/photo").permitAll()
                        // Appointments: patients book and move them, surgeons confirm and complete them, both may
                        // cancel. Each record is limited to its own patient and surgeon in AppointmentService.
                        .requestMatchers(HttpMethod.POST, "/api/appointments").hasRole("PATIENT")
                        .requestMatchers(HttpMethod.GET, "/api/appointments/my").hasRole("PATIENT")
                        .requestMatchers(HttpMethod.PUT, "/api/appointments/*/reschedule").hasRole("PATIENT")
                        .requestMatchers(HttpMethod.GET, "/api/appointments/doctor").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.PUT, "/api/appointments/*/confirm", "/api/appointments/*/complete")
                                .hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.PUT, "/api/appointments/*/cancel").hasAnyRole("PATIENT", "DOCTOR")
                        .requestMatchers(HttpMethod.GET, "/api/appointments/*").hasAnyRole("PATIENT", "DOCTOR")
                        .requestMatchers("/api/appointments", "/api/appointments/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/predictions/shared").hasRole("DOCTOR")
                        // Doctors may open a single report, its image and heatmap, but only ones shared with them.
                        .requestMatchers(HttpMethod.GET, "/api/predictions/*", "/api/predictions/*/image",
                                "/api/predictions/*/heatmap")
                                .hasAnyRole("PATIENT", "DOCTOR")
                        .requestMatchers("/api/predictions", "/api/predictions/**").hasRole("PATIENT")
                        // PDF report of a scan: the owner, or a doctor it is shared with (checked per record).
                        .requestMatchers(HttpMethod.GET, "/api/scans/*/report").hasAnyRole("PATIENT", "DOCTOR")
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // The Vue site, its assets and /error.
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(cookies)
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems)
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(tokenUserConverter)))
                // After the login cookie is read: someone on a temporary password may only change it.
                .addFilterAfter(new PasswordChangeRequiredFilter(problems), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Checks email and password at login (see AuthService), with BCrypt and the users collection. */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    JwtEncoder jwtEncoder(AuthProperties properties) {
        return NimbusJwtEncoder.withSecretKey(secretKey(properties)).algorithm(JwtService.ALGORITHM).build();
    }

    /**
     * Accepts only tokens signed with our key using HS256, from our issuer, unexpired, carrying a {@code jti},
     * and not logged out. Tampered, malformed, expired and revoked tokens all fail here and get a 401.
     */
    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties, RevokedTokenRepository revokedTokens) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(JwtService.ALGORITHM)
                .build();
        OAuth2TokenValidator<Jwt> notRevoked = jwt -> {
            if (jwt.getId() == null || jwt.getExpiresAt() == null) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token lacks jti or exp", null));
            }
            return revokedTokens.existsById(jwt.getId())
                    ? OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token has been revoked", null))
                    : OAuth2TokenValidatorResult.success();
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), notRevoked));
        return decoder;
    }

    /** Lets a frontend on another origin (CORS_ORIGINS) call the API with the cookie. The Vite proxy needs none. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", AuthCookies.SLOT_HEADER, AuthCookies.FROM_SLOT_HEADER));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static SecretKey secretKey(AuthProperties properties) {
        return new SecretKeySpec(properties.secretBytes(), "HmacSHA256");
    }
}
