package com.cancerweb.backend.knowledge;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.cancerweb.backend.knowledge.KnowledgeContent.Reference;
import com.cancerweb.backend.knowledge.KnowledgeContent.Section;

/**
 * A Knowledge Hub module as stored. Visitors see only published ones. {@code seeded} marks modules that came from
 * knowledge/modules.json; {@code seedHash} is the SHA-256 of the seed content last written, so the seeder can tell
 * an untouched module (safe to bring up to date) from one an administrator edited (never overwritten).
 */
@Document("knowledge_modules")
public record KnowledgeModule(
        @Id String id,
        String slug,
        int order,
        String title,
        String category,
        String illustration,
        int readingMinutes,
        String description,
        List<Section> sections,
        List<Reference> references,
        List<String> related,
        boolean published,
        boolean seeded,
        Instant createdAt,
        Instant updatedAt,
        String seedHash) {

    static KnowledgeModule from(KnowledgeContent c, String id, boolean published, boolean seeded, Instant createdAt,
            Instant updatedAt, String seedHash) {
        return new KnowledgeModule(id, c.slug(), c.order(), c.title(), c.category(), c.illustration(), c.readingMinutes(),
                c.description(), c.sections(), c.references(), c.related(), published, seeded, createdAt, updatedAt, seedHash);
    }

    KnowledgeContent content() {
        return new KnowledgeContent(slug, order, title, category, illustration, readingMinutes, description, sections,
                references, related);
    }
}
