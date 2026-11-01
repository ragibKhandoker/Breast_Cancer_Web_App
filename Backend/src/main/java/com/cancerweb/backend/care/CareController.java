package com.cancerweb.backend.care;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import com.cancerweb.backend.care.CareService.CareDoctor;
import com.cancerweb.backend.care.CareService.CarePatient;
import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.security.CurrentUser;

/** Doctor-patient care: who sees whom is set in SecurityConfig by role and checked per record in CareService. */
@RestController
@RequestMapping("/api/care")
public class CareController {

    public record ReviewRequestBody(String predictionId, String doctorId, String message) {
    }

    public record ReviewBody(String note) {
    }

    private final CareService care;

    public CareController(CareService care) {
        this.care = care;
    }

    // ----- Patients -----

    @GetMapping("/my-doctors")
    public List<CareDoctor> myDoctors(@AuthenticationPrincipal CurrentUser me) {
        return care.doctors(me.id());
    }

    @GetMapping("/review-requests")
    public List<ReviewRequest> myRequests(@AuthenticationPrincipal CurrentUser me) {
        return care.patientRequests(me.id());
    }

    @PostMapping("/review-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewRequest requestReview(@RequestBody ReviewRequestBody body, @AuthenticationPrincipal CurrentUser me) {
        return care.requestReview(me, body.predictionId(), body.doctorId(), body.message());
    }

    // ----- Doctors -----

    @GetMapping("/my-patients")
    public List<CarePatient> myPatients(@AuthenticationPrincipal CurrentUser me) {
        return care.patients(me.id());
    }

    @GetMapping("/my-patients/{patientId}/scans")
    public List<Prediction> patientScans(@PathVariable String patientId, @AuthenticationPrincipal CurrentUser me) {
        return care.patientScans(me, patientId);
    }

    @PostMapping(path = "/my-patients/{patientId}/scans", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Prediction runDetection(@PathVariable String patientId, @RequestParam("image") MultipartFile image,
            @RequestParam(required = false) String view, @RequestParam(required = false) String uploadId,
            @AuthenticationPrincipal CurrentUser me) throws IOException {
        return care.runDetection(me, patientId, image, view, uploadId);
    }

    @GetMapping("/review-inbox")
    public List<ReviewRequest> inbox(@AuthenticationPrincipal CurrentUser me) {
        return care.inbox(me.id());
    }

    @PutMapping("/review-inbox/{id}")
    public ReviewRequest review(@PathVariable String id, @RequestBody ReviewBody body, @AuthenticationPrincipal CurrentUser me) {
        return care.review(me, id, body.note());
    }

    // ----- Both -----

    @GetMapping("/reports/{predictionId}/reviews")
    public List<ReviewRequest> reviews(@PathVariable String predictionId, @AuthenticationPrincipal CurrentUser me) {
        return care.forReport(predictionId, me);
    }
}
