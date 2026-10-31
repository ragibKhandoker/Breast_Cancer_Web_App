package com.cancerweb.backend.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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

import com.cancerweb.backend.dto.AvailabilityRequest;
import com.cancerweb.backend.dto.BreastSurgeonProfileRequest;
import com.cancerweb.backend.dto.BreastSurgeonResponse;
import com.cancerweb.backend.dto.SlotDay;
import com.cancerweb.backend.dto.SurgeonProfileDetail;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.AppointmentService;
import com.cancerweb.backend.service.BreastSurgeonService;
import com.cancerweb.backend.service.BreastSurgeonService.StoredFile;

import jakarta.validation.Valid;

/**
 * Breast Surgeons. Anyone may browse approved ones, their calendars and pictures; {@code /profile} is the
 * signed-in DOCTOR's own profile (SecurityConfig sets who may call what).
 */
@RestController
@RequestMapping("/api/doctors/breast-surgeons")
public class BreastSurgeonController {

    private final BreastSurgeonService surgeons;
    private final AppointmentService appointments;

    public BreastSurgeonController(BreastSurgeonService surgeons, AppointmentService appointments) {
        this.surgeons = surgeons;
        this.appointments = appointments;
    }

    /** Approved Breast Surgeons only. Every filter is optional. */
    @GetMapping
    public List<BreastSurgeonResponse> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Integer minExperience, @RequestParam(required = false) String hospital) {
        return surgeons.listApproved(q, minExperience, hospital).stream().map(BreastSurgeonResponse::from).toList();
    }

    @GetMapping("/{id}")
    public BreastSurgeonResponse get(@PathVariable String id) {
        return BreastSurgeonResponse.from(surgeons.getApproved(id));
    }

    /** Offered slots for the next {@code days} days (default 14, at most 60), each marked free or not. */
    @GetMapping("/{id}/slots")
    public List<SlotDay> slots(@PathVariable String id, @RequestParam(required = false) Integer days) {
        return appointments.calendar(id, days);
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<Resource> photo(@PathVariable String id) {
        StoredFile photo = surgeons.photo(id);
        // The URL carries the picture's id, so a new picture gets a new URL and caching is safe.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(photo.resource());
    }

    // ----- The signed-in surgeon's own profile -----

    @GetMapping("/profile")
    public SurgeonProfileDetail own(@AuthenticationPrincipal CurrentUser me) {
        return SurgeonProfileDetail.from(surgeons.getOwn(me.id()));
    }

    @PostMapping("/profile")
    @ResponseStatus(HttpStatus.CREATED)
    public SurgeonProfileDetail create(@Valid @RequestBody BreastSurgeonProfileRequest request,
            @AuthenticationPrincipal CurrentUser me) {
        return SurgeonProfileDetail.from(surgeons.create(me.id(), request));
    }

    @PutMapping("/profile")
    public SurgeonProfileDetail update(@Valid @RequestBody BreastSurgeonProfileRequest request,
            @AuthenticationPrincipal CurrentUser me) {
        return SurgeonProfileDetail.from(surgeons.update(me.id(), request));
    }

    @PutMapping("/profile/availability")
    public SurgeonProfileDetail availability(@RequestBody AvailabilityRequest request, @AuthenticationPrincipal CurrentUser me) {
        return SurgeonProfileDetail.from(surgeons.updateAvailability(me.id(), request));
    }

    @PostMapping("/profile/photo")
    public SurgeonProfileDetail photo(@RequestParam("photo") MultipartFile photo, @AuthenticationPrincipal CurrentUser me)
            throws IOException {
        return SurgeonProfileDetail.from(surgeons.setPhoto(me.id(), photo));
    }

    @DeleteMapping("/profile/photo")
    public SurgeonProfileDetail removePhoto(@AuthenticationPrincipal CurrentUser me) {
        return SurgeonProfileDetail.from(surgeons.removePhoto(me.id()));
    }

    @PostMapping("/profile/documents")
    public SurgeonProfileDetail addDocument(@RequestParam("document") MultipartFile document,
            @AuthenticationPrincipal CurrentUser me) throws IOException {
        return SurgeonProfileDetail.from(surgeons.addDocument(me.id(), document));
    }

    @DeleteMapping("/profile/documents/{documentId}")
    public SurgeonProfileDetail removeDocument(@PathVariable String documentId, @AuthenticationPrincipal CurrentUser me) {
        return SurgeonProfileDetail.from(surgeons.removeDocument(me.id(), documentId));
    }

    @GetMapping("/profile/documents/{documentId}")
    public ResponseEntity<Resource> document(@PathVariable String documentId, @AuthenticationPrincipal CurrentUser me) {
        return download(surgeons.document(surgeons.getOwn(me.id()), documentId));
    }

    /** A verification document, always as a download so nothing uploaded runs inside the site. */
    static ResponseEntity<Resource> download(StoredFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(file.resource());
    }
}
