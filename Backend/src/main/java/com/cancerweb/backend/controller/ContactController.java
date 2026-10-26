package com.cancerweb.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.dto.ContactRequest;
import com.cancerweb.backend.dto.ContactResponse;
import com.cancerweb.backend.model.ContactMessage;
import com.cancerweb.backend.service.ContactService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private final ContactService service;

    public ContactController(ContactService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactResponse submit(@Valid @RequestBody ContactRequest request) {
        ContactMessage saved = service.submit(request);
        return new ContactResponse(saved.id(), "Thank you, your message has been received.", saved.createdAt());
    }
}
