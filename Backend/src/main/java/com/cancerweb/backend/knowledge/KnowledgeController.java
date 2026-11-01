package com.cancerweb.backend.knowledge;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.knowledge.KnowledgeService.Faq;
import com.cancerweb.backend.knowledge.KnowledgeService.GlossaryTerm;
import com.cancerweb.backend.knowledge.KnowledgeService.StoredImage;

/** The Knowledge Hub for everyone, signed in or not: published modules, glossary, FAQ and diagrams. */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    /** What the hub page needs for a card, the search and previous/next links. */
    public record ModuleSummary(String slug, int order, String title, String category, String illustration,
            int readingMinutes, String description, List<String> sectionTitles, Instant updatedAt) {

        static ModuleSummary from(KnowledgeModule m) {
            return new ModuleSummary(m.slug(), m.order(), m.title(), m.category(), m.illustration(), m.readingMinutes(),
                    m.description(), m.sections().stream().map(KnowledgeContent.Section::title).toList(), m.updatedAt());
        }
    }

    private final KnowledgeService knowledge;

    public KnowledgeController(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    @GetMapping("/modules")
    public List<ModuleSummary> modules() {
        return knowledge.listPublished().stream().map(ModuleSummary::from).toList();
    }

    @GetMapping("/modules/{slug}")
    public KnowledgeModule module(@PathVariable String slug) {
        return knowledge.getPublished(slug);
    }

    @GetMapping("/glossary")
    public List<GlossaryTerm> glossary() {
        return knowledge.glossary();
    }

    @GetMapping("/faqs")
    public List<Faq> faqs() {
        return knowledge.faqs();
    }

    @GetMapping("/images/{id}")
    public ResponseEntity<Resource> image(@PathVariable String id) {
        StoredImage image = knowledge.image(id);
        // A replaced diagram gets a new id, so its URL never changes content and caching is safe.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(image.resource());
    }
}
