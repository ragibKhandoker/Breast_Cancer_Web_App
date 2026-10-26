package com.cancerweb.backend.service;

import java.time.Instant;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.cancerweb.backend.dto.ContactRequest;
import com.cancerweb.backend.model.ContactMessage;
import com.cancerweb.backend.repository.ContactMessageRepository;

@Service
public class ContactService {

    private final ContactMessageRepository repository;

    public ContactService(ContactMessageRepository repository) {
        this.repository = repository;
    }

    /** Stores a validated Contact form submission. */
    public ContactMessage submit(ContactRequest request) {
        ContactMessage message = new ContactMessage(null, request.name().strip(),
                request.email().strip().toLowerCase(Locale.ROOT), request.subject().strip(),
                request.message().strip(), Instant.now());
        return repository.save(message);
    }
}
