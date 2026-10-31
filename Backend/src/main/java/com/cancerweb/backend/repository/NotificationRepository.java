package com.cancerweb.backend.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.cancerweb.backend.model.Notification;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    Optional<Notification> findByIdAndUserId(String id, String userId);

    long countByUserIdAndReadFalse(String userId);
}
