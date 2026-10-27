package com.cancerweb.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.dto.UserSummary;
import com.cancerweb.backend.service.UserService;

/** The doctors a patient can choose from when sharing a report (names only). PATIENT only. */
@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final UserService userService;

    public DoctorController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserSummary> list() {
        return userService.listDoctors();
    }
}
