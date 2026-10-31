package com.cancerweb.backend.controller;

import java.io.IOException;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.ScanReportService;

/**
 * Downloadable scan reports. A "scan" is one analysed mammogram, the same record as /api/predictions/{id}.
 * SecurityConfig allows patients and doctors; ScanReportService checks the record against the signed-in user.
 */
@RestController
@RequestMapping("/api/scans")
public class ScanReportController {

    private final ScanReportService service;

    public ScanReportController(ScanReportService service) {
        this.service = service;
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<byte[]> report(@PathVariable String id, @AuthenticationPrincipal CurrentUser me)
            throws IOException {
        byte[] pdf = service.report(id, me);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ScanReportService.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("breastcare-report-" + id + ".pdf").build().toString())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(pdf);
    }
}
