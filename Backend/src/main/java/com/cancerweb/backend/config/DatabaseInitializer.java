package com.cancerweb.backend.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import com.cancerweb.backend.model.ContactMessage;
import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.model.RevokedToken;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.PredictionRepository;

/**
 * Creates the indexes the app relies on, every start. Creating an index that already exists does nothing, so
 * this is safe to repeat, and MongoDB creates a collection the first time an index or document is added to it.
 * If MongoDB cannot be reached the app stops here: without the unique email index, duplicate accounts could
 * slip in.
 */
@Component
@Order(0)
public class DatabaseInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final MongoTemplate mongo;
    private final PredictionRepository predictions;

    public DatabaseInitializer(MongoTemplate mongo, PredictionRepository predictions) {
        this.mongo = mongo;
        this.predictions = predictions;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureIndexes();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Could not set up MongoDB. " + StartupReport.connectionHint(e), e);
        }
        long unowned = predictions.countByOwnerIdIsNull();
        if (unowned > 0) {
            log.info("{} prediction(s) from before accounts existed have no owner. They are kept, but no one can "
                    + "open them until an owner is assigned (see README, 'Older predictions').", unowned);
        }
    }

    void ensureIndexes() {
        mongo.indexOps(User.class).createIndex(new Index().on("email", Direction.ASC).unique().named("email_unique"));
        mongo.indexOps(Prediction.class).createIndex(
                new Index().on("ownerId", Direction.ASC).on("createdAt", Direction.DESC).named("owner_createdAt"));
        mongo.indexOps(Prediction.class).createIndex(
                new Index().on("sharedWithDoctorIds", Direction.ASC).named("sharedWithDoctorIds"));
        // One record per upload attempt, so retrying an upload cannot save the image twice. Only records that
        // carry an uploadId are indexed; older ones have none.
        mongo.indexOps(Prediction.class).createIndex(new Index().on("ownerId", Direction.ASC).on("uploadId", Direction.ASC)
                .unique().partial(PartialIndexFilter.of(Criteria.where("uploadId").exists(true))).named("owner_uploadId_unique"));
        // Admin overview counts and chart, and the Contact inbox.
        mongo.indexOps(Prediction.class).createIndex(new Index().on("createdAt", Direction.DESC).named("createdAt"));
        mongo.indexOps(User.class).createIndex(new Index().on("role", Direction.ASC).named("role"));
        mongo.indexOps(ContactMessage.class).createIndex(
                new Index().on("status", Direction.ASC).on("createdAt", Direction.DESC).named("status_createdAt"));
        // MongoDB deletes each entry once its token's own expiry has passed.
        mongo.indexOps(RevokedToken.class).createIndex(
                new Index().on("expiresAt", Direction.ASC).expire(Duration.ZERO).named("expiresAt_ttl"));
    }
}
