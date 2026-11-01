package com.cancerweb.backend.knowledge;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.security.CurrentUser;

/** The signed-in user's own Knowledge Hub progress and bookmarks (any role). */
@RestController
@RequestMapping("/api/learning")
public class LearningProgressController {

    public record CompletedRequest(boolean completed) {
    }

    public record BookmarkRequest(boolean bookmarked) {
    }

    public record MergeRequest(List<String> completed, List<String> bookmarks) {
    }

    private final LearningProgressService progress;

    public LearningProgressController(LearningProgressService progress) {
        this.progress = progress;
    }

    @GetMapping("/progress")
    public LearningProgress get(@AuthenticationPrincipal CurrentUser me) {
        return progress.get(me.id());
    }

    @PutMapping("/progress/{slug}")
    public LearningProgress completed(@PathVariable String slug, @RequestBody CompletedRequest request,
            @AuthenticationPrincipal CurrentUser me) {
        return progress.setCompleted(me.id(), slug, request.completed());
    }

    @PutMapping("/bookmarks/{slug}")
    public LearningProgress bookmark(@PathVariable String slug, @RequestBody BookmarkRequest request,
            @AuthenticationPrincipal CurrentUser me) {
        return progress.setBookmarked(me.id(), slug, request.bookmarked());
    }

    @PutMapping("/last/{slug}")
    public LearningProgress last(@PathVariable String slug, @AuthenticationPrincipal CurrentUser me) {
        return progress.setLast(me.id(), slug);
    }

    @PostMapping("/progress/merge")
    public LearningProgress merge(@RequestBody MergeRequest request, @AuthenticationPrincipal CurrentUser me) {
        return progress.merge(me.id(), request.completed(), request.bookmarks());
    }
}
