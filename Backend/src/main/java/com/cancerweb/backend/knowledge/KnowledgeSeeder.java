package com.cancerweb.backend.knowledge;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** After the indexes exist (DatabaseInitializer is order 0), adds any Knowledge Hub module that is missing. */
@Component
@Order(1)
public class KnowledgeSeeder implements ApplicationRunner {

    private final KnowledgeService knowledge;

    public KnowledgeSeeder(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public void run(ApplicationArguments args) {
        knowledge.seed();
    }
}
