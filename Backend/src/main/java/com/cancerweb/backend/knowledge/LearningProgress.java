package com.cancerweb.backend.knowledge;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** A signed-in user's Knowledge Hub progress: finished modules, bookmarks and the last module opened. */
@Document("knowledge_progress")
public record LearningProgress(
        @Id String userId,
        List<String> completed,
        List<String> bookmarks,
        String lastSlug,
        Instant updatedAt) {

    static LearningProgress empty(String userId) {
        return new LearningProgress(userId, List.of(), List.of(), null, null);
    }
}
