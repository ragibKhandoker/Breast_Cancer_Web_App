package com.cancerweb.backend.knowledge;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cancerweb.backend.knowledge.KnowledgeSubmission.Status;
import com.cancerweb.backend.security.CurrentUser;

/** Knowledge Hub content management for administrators (/api/admin/** is ADMIN only in SecurityConfig). */
@RestController
@RequestMapping("/api/admin/knowledge")
public class AdminKnowledgeController {

    public record PublishRequest(boolean published) {
    }

    public record ReviewRequest(Status status, String note) {
    }

    private final KnowledgeService knowledge;

    public AdminKnowledgeController(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    /** Every module, published or not, with full content. */
    @GetMapping("/modules")
    public List<KnowledgeModule> modules() {
        return knowledge.listAll();
    }

    @GetMapping("/modules/{slug}")
    public KnowledgeModule module(@PathVariable String slug) {
        return knowledge.get(slug);
    }

    @PostMapping("/modules")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeModule create(@RequestBody KnowledgeContent content, @AuthenticationPrincipal CurrentUser me) {
        return knowledge.create(content, me);
    }

    @PutMapping("/modules/{slug}")
    public KnowledgeModule update(@PathVariable String slug, @RequestBody KnowledgeContent content,
            @AuthenticationPrincipal CurrentUser me) {
        return knowledge.update(slug, content, me);
    }

    @PutMapping("/modules/{slug}/published")
    public KnowledgeModule publish(@PathVariable String slug, @RequestBody PublishRequest request,
            @AuthenticationPrincipal CurrentUser me) {
        return knowledge.setPublished(slug, request.published(), me);
    }

    /** Uploads a diagram; put the returned id in a section's imageFileId. */
    @PostMapping("/images")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> image(@RequestParam("image") MultipartFile image) throws IOException {
        String id = knowledge.storeImage(image);
        return Map.of("fileId", id, "url", "/api/knowledge/images/" + id);
    }

    @GetMapping("/submissions")
    public List<KnowledgeSubmission> submissions(@RequestParam(required = false) Status status) {
        return knowledge.submissions(status);
    }

    @PutMapping("/submissions/{id}/review")
    public KnowledgeSubmission review(@PathVariable String id, @RequestBody ReviewRequest request,
            @AuthenticationPrincipal CurrentUser me) {
        return knowledge.review(id, request.status(), request.note(), me);
    }
}
