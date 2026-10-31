package com.cancerweb.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.cancerweb.backend.model.PasswordResetRequest;
import com.cancerweb.backend.model.ResetRequestStatus;

public interface PasswordResetRequestRepository extends MongoRepository<PasswordResetRequest, String> {

    boolean existsByUserIdAndStatus(String userId, ResetRequestStatus status);

    Optional<PasswordResetRequest> findFirstByUserIdAndStatusOrderByCreatedAtDesc(String userId, ResetRequestStatus status);

    List<PasswordResetRequest> findAllByOrderByCreatedAtDesc();

    List<PasswordResetRequest> findAllByStatusOrderByCreatedAtDesc(ResetRequestStatus status);

    long countByStatus(ResetRequestStatus status);
}
