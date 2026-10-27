package com.cancerweb.backend.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.service.UserService;

import de.flapdoodle.embed.mongo.commands.ServerAddress;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.embed.process.io.ProcessOutput;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;
import jakarta.servlet.http.Cookie;

/**
 * Base for tests that run the whole app against a throwaway local mongod (downloaded once by Flapdoodle, then
 * cached), never against MongoDB Atlas. Every test starts with empty collections.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    public static final String TEST_DATABASE = "breast_cancer_test";
    public static final String JWT_SECRET = "test-only-secret-that-is-long-enough-for-hs256-signing";
    public static final String ISSUER = "breastcare-backend";
    public static final String PASSWORD = "Secret123";

    private static final TransitionWalker.ReachedState<RunningMongodProcess> MONGOD = startMongod();
    private static final List<String> COLLECTIONS =
            List.of("users", "predictions", "revoked_tokens", "contact_messages", "fs.files", "fs.chunks");

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected MongoTemplate mongo;

    @Autowired
    protected UserService userService;

    private static TransitionWalker.ReachedState<RunningMongodProcess> startMongod() {
        TransitionWalker.ReachedState<RunningMongodProcess> mongod = Mongod.instance()
                .withProcessOutput(Start.to(ProcessOutput.class).initializedWith(ProcessOutput.silent()))
                .start(Version.Main.V7_0);
        Runtime.getRuntime().addShutdownHook(new Thread(mongod::close));
        return mongod;
    }

    public static String testMongoUri() {
        ServerAddress address = MONGOD.current().getServerAddress();
        return "mongodb://" + address.getHost() + ":" + address.getPort() + "/" + TEST_DATABASE;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // These outrank Backend/.env, so the Atlas URI and any real secrets in it are not used.
        registry.add("spring.mongodb.uri", IntegrationTest::testMongoUri);
        registry.add("app.auth.jwt-secret", () -> JWT_SECRET);
        registry.add("app.auth.token-ttl", () -> "PT1H");
        registry.add("app.admin.email", () -> "");
        registry.add("app.admin.password", () -> "");
    }

    @BeforeEach
    void emptyCollections() {
        // Belt and braces: refuse to delete anything unless this really is the local test database.
        if (!TEST_DATABASE.equals(mongo.getDb().getName())) {
            throw new IllegalStateException("Tests are not connected to the test database");
        }
        // remove() keeps the indexes, which DatabaseInitializer created at startup.
        COLLECTIONS.forEach(name -> mongo.remove(new Query(), name));
    }

    protected User createUser(String fullName, String email, Role role) {
        return userService.createAccount(fullName, email, PASSWORD, role);
    }

    /** JSON request with a valid CSRF token, as the frontend sends it. */
    protected static MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body).with(csrf());
    }

    protected Cookie login(String email, String password) throws Exception {
        return mvc.perform(withJson(post("/api/auth/login"),
                        "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("bc_auth");
    }

    /** Creates an account with the given role and returns its login cookie. */
    protected Cookie signedIn(Role role, String email) throws Exception {
        createUser(role.name().charAt(0) + role.name().substring(1).toLowerCase() + " User", email, role);
        return login(email, PASSWORD);
    }

    protected static Cookie authCookie(String token) {
        return new Cookie("bc_auth", token);
    }

    /** Signs a token with any key and claims, to build tampered, foreign or expired tokens. */
    protected static String signToken(String secret, String subject, Instant issuedAt, Instant expiresAt) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
