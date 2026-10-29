package com.cancerweb.backend.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cancerweb.backend.dto.ShareRequest;
import com.cancerweb.backend.dto.SharedReportResponse;
import com.cancerweb.backend.dto.UserSummary;
import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.PredictionService;

import jakarta.validation.Valid;

/**
 * Mammogram analysis and reports. SecurityConfig decides the role per endpoint; PredictionService checks each
 * record against the signed-in user, whose id always comes from the verified token.
 */
@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final PredictionService service;

    public PredictionController(PredictionService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Prediction create(@RequestParam("image") MultipartFile image,
            @RequestParam(value = "view", required = false) String view,
            @RequestParam(value = "uploadId", required = false) String uploadId,
            @AuthenticationPrincipal CurrentUser me) throws IOException {
        return service.analyse(image, view, uploadId, me);
    }

    @GetMapping
    public List<Prediction> list(@AuthenticationPrincipal CurrentUser me) {
        return service.findOwn(me.id());
    }

    /** DOCTOR: reports patients have shared with the signed-in doctor. */
    @GetMapping("/shared")
    public List<SharedReportResponse> shared(@AuthenticationPrincipal CurrentUser me) {
        return service.findSharedWith(me.id());
    }

    @GetMapping("/{id}")
    public Prediction get(@PathVariable String id, @AuthenticationPrincipal CurrentUser me) {
        return service.findAccessible(id, me);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable String id, @AuthenticationPrincipal CurrentUser me) {
        Prediction prediction = service.findAccessible(id, me);
        String contentType = prediction.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : prediction.contentType();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(service.loadImage(prediction));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @AuthenticationPrincipal CurrentUser me) {
        service.delete(id, me);
    }

    @GetMapping("/{id}/shares")
    public List<UserSummary> shares(@PathVariable String id, @AuthenticationPrincipal CurrentUser me) {
        return service.listShares(id, me);
    }

    @PostMapping("/{id}/shares")
    public List<UserSummary> share(@PathVariable String id, @Valid @RequestBody ShareRequest request,
            @AuthenticationPrincipal CurrentUser me) {
        return service.share(id, request.doctorId(), me);
    }

    @DeleteMapping("/{id}/shares/{doctorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unshare(@PathVariable String id, @PathVariable String doctorId, @AuthenticationPrincipal CurrentUser me) {
        service.unshare(id, doctorId, me);
    }
}
