package com.cancerweb.backend.controller;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.dto.SurgeonProfileDetail;
import com.cancerweb.backend.dto.SurgeonReviewRequest;
import com.cancerweb.backend.model.SurgeonProfileStatus;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.BreastSurgeonService;

import jakarta.validation.Valid;

/** Checking Breast Surgeons' credentials. SecurityConfig limits /api/admin/** to ADMIN. */
@RestController
@RequestMapping("/api/admin/breast-surgeons")
public class AdminSurgeonController {

    private final BreastSurgeonService surgeons;

    public AdminSurgeonController(BreastSurgeonService surgeons) {
        this.surgeons = surgeons;
    }

    /** Most recently submitted first; {@code status} PENDING, APPROVED or REJECTED, or omitted for all. */
    @GetMapping
    public List<SurgeonProfileDetail> list(@RequestParam(required = false) SurgeonProfileStatus status) {
        return surgeons.listForReview(status).stream().map(SurgeonProfileDetail::from).toList();
    }

    @GetMapping("/{id}")
    public SurgeonProfileDetail get(@PathVariable String id) {
        return SurgeonProfileDetail.from(surgeons.get(id));
    }

    @PatchMapping("/{id}/review")
    public SurgeonProfileDetail review(@PathVariable String id, @Valid @RequestBody SurgeonReviewRequest request,
            @AuthenticationPrincipal CurrentUser admin) {
        return SurgeonProfileDetail.from(surgeons.review(admin.email(), id, request.status(), request.note()));
    }

    @GetMapping("/{id}/documents/{documentId}")
    public ResponseEntity<Resource> document(@PathVariable String id, @PathVariable String documentId) {
        return BreastSurgeonController.download(surgeons.document(surgeons.get(id), documentId));
    }
}
