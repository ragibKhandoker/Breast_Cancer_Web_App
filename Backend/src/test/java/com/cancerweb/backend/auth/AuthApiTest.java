package com.cancerweb.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.RevokedTokenRepository;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

class AuthApiTest extends IntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private RevokedTokenRepository revokedTokens;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static String registration(String fullName, String email, String password) {
        return "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    // ---- Registration ----

    @Test
    void registerCreatesPatientWithBcryptHashAndNoHashInResponse() throws Exception {
        mvc.perform(withJson(post("/api/auth/register"), registration("Ayesha Rahman", "  Ayesha@Example.COM ", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ayesha@example.com"))
                .andExpect(jsonPath("$.fullName").value("Ayesha Rahman"))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        User stored = users.findByEmail("ayesha@example.com").orElseThrow();
        assertThat(stored.passwordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, stored.passwordHash())).isTrue();
        assertThat(stored.createdAt()).isNotNull();
        assertThat(stored.updatedAt()).isNotNull();
    }

    @Test
    void registerIgnoresARoleSentByTheClient() throws Exception {
        String body = "{\"fullName\":\"Sneaky\",\"email\":\"sneaky@example.com\",\"password\":\"" + PASSWORD
                + "\",\"role\":\"ADMIN\"}";
        mvc.perform(withJson(post("/api/auth/register"), body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PATIENT"));
        assertThat(users.findByEmail("sneaky@example.com").orElseThrow().role()).isEqualTo(Role.PATIENT);
    }

    @Test
    void registerRejectsDuplicateEmailIgnoringCase() throws Exception {
        createUser("First", "taken@example.com", Role.PATIENT);
        mvc.perform(withJson(post("/api/auth/register"), registration("Second", "TAKEN@example.com", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.email").value("An account with this email already exists."));
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void simultaneousRegistrationsWithOneEmailCreateOneAccount() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> attempts = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                attempts.add(() -> mvc.perform(withJson(post("/api/auth/register"),
                        registration("Racer", "race@example.com", PASSWORD))).andReturn().getResponse().getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : pool.invokeAll(attempts)) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsOnlyOnce(201).filteredOn(s -> s != 201).containsOnly(409);
        } finally {
            pool.shutdown();
        }
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void databaseUniqueIndexRejectsDuplicateEmailEvenWithoutTheServiceCheck() {
        Instant now = Instant.now();
        users.insert(new User(null, "One", "dup@example.com", "hash", Role.PATIENT, now, now, null, null, null, null, null, null, null, null));
        assertThatThrownBy(() -> users.insert(new User(null, "Two", "dup@example.com", "hash", Role.PATIENT, now, now, null, null, null, null, null, null, null, null)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void registerValidatesFields() throws Exception {
        mvc.perform(withJson(post("/api/auth/register"), registration("A", "not-an-email", "weak")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.email").value("Enter a valid email address"))
                .andExpect(jsonPath("$.errors.password").value(containsString("upper-case")));

        mvc.perform(withJson(post("/api/auth/register"), registration("No Digits", "nodigits@example.com", "Password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());

        mvc.perform(withJson(post("/api/auth/register"), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Email is required"))
                .andExpect(jsonPath("$.errors.password").value("Password is required"));
        assertThat(users.count()).isZero();
    }

    @Test
    void registerRejectsPasswordsLongerThanBcryptCanHash() throws Exception {
        String password = "Aa1" + "é".repeat(40); // 43 characters but 83 UTF-8 bytes
        mvc.perform(withJson(post("/api/auth/register"), registration("Long Password", "long@example.com", password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Password is too long"));
    }

    // ---- Login ----

    @Test
    void loginSetsHttpOnlyCookieAndReturnsProfileWithoutToken() throws Exception {
        createUser("Nadia", "nadia@example.com", Role.DOCTOR);
        mvc.perform(withJson(post("/api/auth/login"), "{\"email\":\"NADIA@example.com \",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("nadia@example.com"))
                .andExpect(jsonPath("$.role").value("DOCTOR"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("bc_auth=ey")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api")));
    }

    @Test
    void loginFailsWithTheSameMessageForWrongPasswordAndUnknownEmail() throws Exception {
        createUser("Rafi", "rafi@example.com", Role.PATIENT);
        mvc.perform(withJson(post("/api/auth/login"), "{\"email\":\"rafi@example.com\",\"password\":\"Wrong1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        mvc.perform(withJson(post("/api/auth/login"), "{\"email\":\"nobody@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
        mvc.perform(withJson(post("/api/auth/login"), "{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithAStaleCookieStillWorks() throws Exception {
        createUser("Stale", "stale@example.com", Role.PATIENT);
        mvc.perform(withJson(post("/api/auth/login"), "{\"email\":\"stale@example.com\",\"password\":\"" + PASSWORD + "\"}")
                        .cookie(authCookie("expired.or.garbage")))
                .andExpect(status().isOk());
    }

    // ---- /me and token validation ----

    @Test
    void meReturnsTheSignedInUser() throws Exception {
        Cookie cookie = signedIn(Role.PATIENT, "me@example.com");
        mvc.perform(get("/api/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void meWithoutLoginIs401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Please log in to continue."));
    }

    @Test
    void tamperedTokenIsRejectedAndTheCookieCleared() throws Exception {
        User user = createUser("Tamper", "tamper@example.com", Role.PATIENT);
        String token = login("tamper@example.com", PASSWORD).getValue();
        String[] parts = token.split("\\.");
        // Swap in a payload claiming to be someone else, keeping the original signature.
        String forgedPayload = signToken(JWT_SECRET, "someone-else", Instant.now(), Instant.now().plusSeconds(600)).split("\\.")[1];
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        mvc.perform(get("/api/auth/me").cookie(authCookie(tampered)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        assertThat(user.id()).isNotEqualTo("someone-else");
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        User admin = createUser("Admin", "admin@example.com", Role.ADMIN);
        String foreign = signToken("a-completely-different-secret-key-0123456789", admin.id(), Instant.now(),
                Instant.now().plusSeconds(600));
        mvc.perform(get("/api/admin/users").cookie(authCookie(foreign))).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        User user = createUser("Old", "old@example.com", Role.PATIENT);
        Instant past = Instant.now().minus(3, ChronoUnit.HOURS);
        String expired = signToken(JWT_SECRET, user.id(), past, past.plus(1, ChronoUnit.HOURS));
        mvc.perform(get("/api/auth/me").cookie(authCookie(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Your session has expired or is no longer valid. Please log in again."));
    }

    @Test
    void validlySignedTokenForCurrentUserWorks() throws Exception {
        User user = createUser("Fresh", "fresh@example.com", Role.PATIENT);
        String token = signToken(JWT_SECRET, user.id(), Instant.now(), Instant.now().plusSeconds(600));
        mvc.perform(get("/api/auth/me").cookie(authCookie(token))).andExpect(status().isOk())
                // The role comes from the database, not from the token's "ADMIN" claim.
                .andExpect(jsonPath("$.role").value("PATIENT"));
        mvc.perform(get("/api/admin/users").cookie(authCookie(token))).andExpect(status().isForbidden());
    }

    @Test
    void malformedTokensAreRejected() throws Exception {
        for (String token : List.of("not-a-jwt", "a.b.c", "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0.")) {
            mvc.perform(get("/api/auth/me").cookie(authCookie(token))).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void tokenOfADeletedAccountIsRejected() throws Exception {
        Cookie cookie = signedIn(Role.PATIENT, "gone@example.com");
        users.deleteAll();
        mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    // ---- Logout ----

    @Test
    void logoutRevokesTheTokenOnTheServer() throws Exception {
        Cookie cookie = signedIn(Role.PATIENT, "bye@example.com");
        mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").cookie(cookie).with(
                        csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        assertThat(revokedTokens.count()).isEqualTo(1);
        assertThat(revokedTokens.findAll().getFirst().expiresAt()).isAfter(Instant.now());
        // A copy of the old cookie no longer works.
        mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutOrWithBrokenCookieStillSucceeds() throws Exception {
        var csrf = csrf();
        mvc.perform(post("/api/auth/logout").with(csrf)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout").cookie(authCookie("garbage")).with(csrf)).andExpect(status().isNoContent());
        assertThat(revokedTokens.count()).isZero();
    }

    // ---- CSRF ----

    @Test
    void stateChangingRequestsWithoutCsrfTokenAreRefused() throws Exception {
        createUser("Csrf", "csrf@example.com", Role.PATIENT);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"csrf@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("security token")));
    }

    @Test
    void errorResponsesDoNotLeakInternals() throws Exception {
        mvc.perform(withJson(post("/api/auth/register"), "{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Exception"))));
    }
}
