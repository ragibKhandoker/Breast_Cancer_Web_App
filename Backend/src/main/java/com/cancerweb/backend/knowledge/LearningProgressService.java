package com.cancerweb.backend.knowledge;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Knowledge Hub progress for signed-in users, kept per account so it follows them between devices. Visitors who
 * are not signed in keep theirs in the browser, and it is merged in here when they sign in.
 */
@Service
public class LearningProgressService {

    /** Far more than there are modules; only stops a client filling the record. */
    static final int MAX_ENTRIES = 200;

    private final MongoTemplate mongo;
    private final KnowledgeService knowledge;

    public LearningProgressService(MongoTemplate mongo, KnowledgeService knowledge) {
        this.mongo = mongo;
        this.knowledge = knowledge;
    }

    public LearningProgress get(String userId) {
        LearningProgress p = mongo.findById(userId, LearningProgress.class);
        if (p == null) {
            return LearningProgress.empty(userId);
        }
        return new LearningProgress(userId, p.completed() == null ? List.of() : p.completed(),
                p.bookmarks() == null ? List.of() : p.bookmarks(), p.lastSlug(), p.updatedAt());
    }

    public LearningProgress setCompleted(String userId, String slug, boolean completed) {
        return toggle(userId, "completed", slug, completed);
    }

    public LearningProgress setBookmarked(String userId, String slug, boolean bookmarked) {
        return toggle(userId, "bookmarks", slug, bookmarked);
    }

    /** The module the user opened last, for "Continue learning". */
    public LearningProgress setLast(String userId, String slug) {
        requireModule(slug);
        return upsert(userId, new Update().set("lastSlug", slug));
    }

    /** Adds what a visitor did before signing in; unknown modules are skipped. */
    public LearningProgress merge(String userId, List<String> completed, List<String> bookmarks) {
        List<String> done = known(completed);
        List<String> marked = known(bookmarks);
        Update update = new Update();
        if (!done.isEmpty()) {
            update.addToSet("completed").each(done.toArray());
        }
        if (!marked.isEmpty()) {
            update.addToSet("bookmarks").each(marked.toArray());
        }
        return done.isEmpty() && marked.isEmpty() ? get(userId) : upsert(userId, update);
    }

    private LearningProgress toggle(String userId, String field, String slug, boolean on) {
        requireModule(slug);
        Update update = new Update();
        if (on) {
            List<String> current = "completed".equals(field) ? get(userId).completed() : get(userId).bookmarks();
            if (current.size() >= MAX_ENTRIES && !current.contains(slug)) {
                throw new IllegalArgumentException("Too many entries");
            }
            update.addToSet(field, slug);
        } else {
            update.pull(field, slug);
        }
        return upsert(userId, update);
    }

    private LearningProgress upsert(String userId, Update update) {
        mongo.upsert(query(where("_id").is(userId)), update.set("updatedAt", Instant.now()), LearningProgress.class);
        return get(userId);
    }

    private void requireModule(String slug) {
        if (!knowledge.exists(slug)) {
            throw new KnowledgeNotFoundException("This lesson was not found");
        }
    }

    private List<String> known(List<String> slugs) {
        if (slugs == null) {
            return List.of();
        }
        return slugs.stream().distinct().limit(MAX_ENTRIES).filter(knowledge::exists).toList();
    }
}
