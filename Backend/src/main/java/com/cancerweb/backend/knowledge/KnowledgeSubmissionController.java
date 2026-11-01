package com.cancerweb.backend.knowledge;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.security.CurrentUser;

/** Content proposed by approved Breast Surgeons (DOCTOR role; the approval is checked in KnowledgeService). */
@RestController
@RequestMapping("/api/knowledge-submissions")
public class KnowledgeSubmissionController {

    /** {@code targetSlug} is the module to change; leave it empty to propose a new module. */
    public record SubmissionRequest(String targetSlug, KnowledgeContent content) {
    }

    private final KnowledgeService knowledge;

    public KnowledgeSubmissionController(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    @GetMapping
    public List<KnowledgeSubmission> mine(@AuthenticationPrincipal CurrentUser me) {
        return knowledge.mine(me.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeSubmission submit(@RequestBody SubmissionRequest request, @AuthenticationPrincipal CurrentUser me) {
        return knowledge.submit(me, request.targetSlug(), request.content());
    }
}
