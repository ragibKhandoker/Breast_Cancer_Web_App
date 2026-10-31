package com.cancerweb.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.dto.AppointmentRequest;
import com.cancerweb.backend.dto.AppointmentResponse;
import com.cancerweb.backend.dto.CancelAppointmentRequest;
import com.cancerweb.backend.dto.RescheduleRequest;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.AppointmentService;

import jakarta.validation.Valid;

/**
 * Breast Surgeon appointments. Patients book, cancel and reschedule their own; surgeons confirm, reject
 * (cancel) and complete theirs. SecurityConfig limits each endpoint to its role, and AppointmentService
 * limits each record to its patient and surgeon.
 */
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse book(@Valid @RequestBody AppointmentRequest request, @AuthenticationPrincipal CurrentUser me) {
        return service.toResponse(service.book(me, request));
    }

    @GetMapping("/my")
    public List<AppointmentResponse> mine(@AuthenticationPrincipal CurrentUser me) {
        return service.toResponses(service.listForPatient(me.id()));
    }

    @GetMapping("/doctor")
    public List<AppointmentResponse> surgeons(@AuthenticationPrincipal CurrentUser me) {
        return service.toResponses(service.listForSurgeon(me.id()));
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(@PathVariable String id, @AuthenticationPrincipal CurrentUser me) {
        return service.toResponse(service.getForViewer(id, me));
    }

    @PutMapping("/{id}/confirm")
    public AppointmentResponse confirm(@PathVariable String id, @AuthenticationPrincipal CurrentUser me) {
        return service.toResponse(service.confirm(me.id(), id));
    }

    /** A patient cancels; a surgeon cancels or rejects a request. The body (a reason) is optional. */
    @PutMapping("/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable String id,
            @Valid @RequestBody(required = false) CancelAppointmentRequest request, @AuthenticationPrincipal CurrentUser me) {
        String reason = request == null ? null : request.reason();
        return service.toResponse(me.role() == Role.DOCTOR
                ? service.cancelBySurgeon(me.id(), id, reason)
                : service.cancelByPatient(me.id(), id, reason));
    }

    @PutMapping("/{id}/reschedule")
    public AppointmentResponse reschedule(@PathVariable String id, @Valid @RequestBody RescheduleRequest request,
            @AuthenticationPrincipal CurrentUser me) {
        return service.toResponse(service.reschedule(me.id(), id, request));
    }

    @PutMapping("/{id}/complete")
    public AppointmentResponse complete(@PathVariable String id, @AuthenticationPrincipal CurrentUser me) {
        return service.toResponse(service.complete(me.id(), id));
    }
}
