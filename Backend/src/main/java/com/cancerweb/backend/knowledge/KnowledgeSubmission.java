package com.cancerweb.backend.knowledge;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Content a verified Breast Surgeon proposed: a change to an existing module ({@code targetSlug} set) or a new
 * module. Nothing is shown to visitors until an administrator approves it.
 */
@Document("knowledge_submissions")
public record KnowledgeSubmission(
        @Id String id,
        String authorId,
        String authorName,
        String targetSlug,
        KnowledgeContent content,
        Status status,
        String reviewNote,
        Instant createdAt,
        Instant reviewedAt) {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }
}
