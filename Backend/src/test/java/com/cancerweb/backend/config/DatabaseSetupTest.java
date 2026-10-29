package com.cancerweb.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.data.mongodb.core.index.IndexInfo;

import com.cancerweb.backend.BackendApplication;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.PredictionRepository;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.security.AuthProperties;
import com.cancerweb.backend.support.IntegrationTest;

/** MongoDB connection checks, indexes, repository operations and the administrator bootstrap. */
class DatabaseSetupTest extends IntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private PredictionRepository predictions;

    @Test
    void connectsToTheTestDatabase() {
        assertThat(mongo.executeCommand("{ ping: 1 }").get("ok")).isIn(1, 1.0);
        assertThat(mongo.getDb().getName()).isEqualTo(TEST_DATABASE);
    }

    @Test
    void createsTheIndexesTheAppRelysOn() {
        IndexInfo email = index("users", "email_unique");
        assertThat(email.isUnique()).isTrue();
        assertThat(index("predictions", "owner_createdAt")).isNotNull();
        assertThat(index("predictions", "sharedWithDoctorIds")).isNotNull();
        assertThat(index("predictions", "owner_uploadId_unique").isUnique()).isTrue();
        assertThat(index("revoked_tokens", "expiresAt_ttl").getExpireAfter()).contains(Duration.ZERO);
    }

    @Test
    void indexSetupCanRunAgainSafely() {
        assertThatCode(() -> new DatabaseInitializer(mongo, predictions).ensureIndexes()).doesNotThrowAnyException();
    }

    @Test
    void repositoryFindsUsersByNormalisedEmail() {
        User saved = createUser("  Repo Test ", "Repo@Example.com", Role.PATIENT);
        assertThat(saved.fullName()).isEqualTo("Repo Test");
        assertThat(users.findByEmail("repo@example.com")).map(User::id).contains(saved.id());
        assertThat(users.existsByEmail("repo@example.com")).isTrue();
        assertThat(users.findAllByRoleOrderByFullNameAsc(Role.PATIENT)).hasSize(1);
    }

    @Test
    void adminBootstrapCreatesOnceAndNeverOverwrites() throws Exception {
        AdminBootstrap bootstrap = new AdminBootstrap(userService, users, "Root@Example.com", PASSWORD, "Root Admin");
        bootstrap.run(new DefaultApplicationArguments());
        bootstrap.run(new DefaultApplicationArguments());
        assertThat(users.findAll()).singleElement().satisfies(user -> {
            assertThat(user.email()).isEqualTo("root@example.com");
            assertThat(user.role()).isEqualTo(Role.ADMIN);
        });
        String hashBefore = users.findAll().getFirst().passwordHash();

        new AdminBootstrap(userService, users, "root@example.com", "Different123", "x").run(new DefaultApplicationArguments());
        assertThat(users.findAll().getFirst().passwordHash()).isEqualTo(hashBefore);
        login("root@example.com", PASSWORD);
    }

    @Test
    void adminBootstrapDoesNotPromoteAnExistingAccountOrUseAWeakPassword() throws Exception {
        createUser("Patient", "patient@example.com", Role.PATIENT);
        new AdminBootstrap(userService, users, "patient@example.com", PASSWORD, "x").run(new DefaultApplicationArguments());
        assertThat(users.findByEmail("patient@example.com").orElseThrow().role()).isEqualTo(Role.PATIENT);

        new AdminBootstrap(userService, users, "weak@example.com", "password", "x").run(new DefaultApplicationArguments());
        new AdminBootstrap(userService, users, "", PASSWORD, "x").run(new DefaultApplicationArguments());
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void missingOrInvalidMongoUriFailsWithAClearMessageThatHidesTheUri() {
        assertThatThrownBy(() -> MongoUriCheck.check(null)).hasMessageContaining("MONGODB_URI is not set");
        assertThatThrownBy(() -> MongoUriCheck.check("  ")).hasMessageContaining("MONGODB_URI is not set");
        assertThatThrownBy(() -> MongoUriCheck.check("postgres://user:hunter2@db/x"))
                .hasMessageContaining("must start with mongodb")
                .hasMessageNotContaining("hunter2");
        assertThatCode(() -> MongoUriCheck.check("mongodb+srv://u:p@cluster0.example.mongodb.net/breast_cancer"))
                .doesNotThrowAnyException();
    }

    @Test
    void missingOrShortJwtSecretIsRefused() {
        assertThatThrownBy(() -> new AuthProperties(null, null, false, null)).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new AuthProperties("too-short", null, false, null)).hasMessageContaining("JWT_SECRET");
        AuthProperties ok = new AuthProperties(JWT_SECRET, null, false, null);
        assertThat(ok.tokenTtl()).isEqualTo(Duration.ofHours(2));
        assertThat(ok.toString()).doesNotContain(JWT_SECRET);
    }

    @Test
    void unreachableDatabaseStopsStartupWithoutPrintingCredentials() {
        String[] args = {
                "--spring.mongodb.uri=mongodb://appuser:topsecretpw@127.0.0.1:1/breast_cancer?serverSelectionTimeoutMS=300",
                "--app.auth.jwt-secret=" + JWT_SECRET, "--server.port=0", "--app.admin.email=" };
        Throwable failure = catchThrowable(() -> new SpringApplicationBuilder(BackendApplication.class).run(args).close());
        assertThat(failure).as("startup should fail").isNotNull();

        Throwable setupFailure = failure;
        while (setupFailure != null && (setupFailure.getMessage() == null
                || !setupFailure.getMessage().startsWith("Could not set up MongoDB"))) {
            setupFailure = setupFailure.getCause();
        }
        assertThat(setupFailure).as("failure raised by DatabaseInitializer").isNotNull();
        assertThat(setupFailure.getMessage()).contains("MONGODB_URI").doesNotContain("topsecretpw");
    }

    private IndexInfo index(String collection, String name) {
        List<IndexInfo> indexes = mongo.indexOps(collection).getIndexInfo();
        return indexes.stream().filter(i -> i.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing index " + name + " on " + collection));
    }
}
