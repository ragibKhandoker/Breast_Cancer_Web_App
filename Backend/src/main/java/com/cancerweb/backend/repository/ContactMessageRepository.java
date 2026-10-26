package com.cancerweb.backend.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.cancerweb.backend.model.ContactMessage;

public interface ContactMessageRepository extends MongoRepository<ContactMessage, String> {
}
