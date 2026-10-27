package com.cancerweb.backend.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Stops startup with a plain message when MONGODB_URI is missing or is not a MongoDB connection string, before
 * the driver tries to use it. Runs ahead of every other bean. The URI itself is never printed, because it
 * contains the database password.
 */
@Component
public class MongoUriCheck implements BeanFactoryPostProcessor, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        check(environment.getProperty("spring.mongodb.uri"));
    }

    static void check(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException("MONGODB_URI is not set. Copy Backend/.env.example to Backend/.env "
                    + "and paste your MongoDB Atlas connection string.");
        }
        if (!uri.startsWith("mongodb://") && !uri.startsWith("mongodb+srv://")) {
            throw new IllegalStateException("MONGODB_URI must start with mongodb+srv:// (Atlas) or mongodb://. "
                    + "Copy it from Atlas -> Connect -> Drivers.");
        }
    }
}
