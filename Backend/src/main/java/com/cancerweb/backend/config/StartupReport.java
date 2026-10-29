package com.cancerweb.backend.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.cancerweb.backend.service.CancerModel;

/** Prints whether MongoDB and the model are ready, plus the URLs, once the server has started. */
@Component
public class StartupReport {

    private final MongoTemplate mongo;
    private final CancerModel predictor;
    private final Environment env;

    public StartupReport(MongoTemplate mongo, CancerModel predictor, Environment env) {
        this.mongo = mongo;
        this.predictor = predictor;
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        String port = env.getProperty("local.server.port", "8081");
        String host = mongoHost(env.getProperty("spring.mongodb.uri", ""));

        String database;
        try {
            mongo.executeCommand("{ ping: 1 }");
            database = "[OK] MongoDB connected -> " + host + " / database '" + mongo.getDb().getName() + "'";
        } catch (RuntimeException e) {
            database = "[FAILED] MongoDB connection failed (" + host + ")\n    " + connectionHint(e);
        }

        System.out.println("""

                ======================================================
                 Breast Cancer Detection App is running!
                 -> Website:     http://localhost:%1$s
                 -> API Status:  http://localhost:%1$s/actuator/health
                 %2$s
                 -> Model:       %3$s
                ======================================================
                """.formatted(port, database, predictor.describe()));
    }

    /** Host part of the URI, without the user name and password. */
    private static String mongoHost(String uri) {
        String afterCredentials = uri.substring(uri.lastIndexOf('@') + 1).replaceFirst("^mongodb(\\+srv)?://", "");
        int end = afterCredentials.indexOf('/');
        return end < 0 ? afterCredentials : afterCredentials.substring(0, end);
    }

    /** What to fix when MongoDB cannot be reached, without the connection string. Also used by DatabaseInitializer. */
    static String connectionHint(Throwable e) {
        String message = rootMessage(e);
        // Atlas answers a TLS "internal_error" when the client IP is not on its access list.
        return message.contains("internal_error")
                ? "Atlas refused this computer's IP. Add it in Atlas -> Network Access -> Add IP Address."
                : abbreviate(message) + "\n    Check MONGODB_URI in Backend/.env and the Atlas Network Access IP list.";
    }

    private static String rootMessage(Throwable e) {
        while (e.getCause() != null) {
            e = e.getCause();
        }
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static String abbreviate(String message) {
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }
}
