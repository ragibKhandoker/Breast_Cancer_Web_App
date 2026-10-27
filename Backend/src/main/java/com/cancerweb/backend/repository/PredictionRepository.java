package com.cancerweb.backend.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.cancerweb.backend.model.Prediction;

public interface PredictionRepository extends MongoRepository<Prediction, String> {

    List<Prediction> findAllByOwnerIdOrderByCreatedAtDesc(String ownerId);

    /** Matches records whose {@code sharedWithDoctorIds} array contains {@code doctorId}. */
    List<Prediction> findAllBySharedWithDoctorIdsOrderByCreatedAtDesc(String doctorId);

    long countByOwnerIdIsNull();
}
