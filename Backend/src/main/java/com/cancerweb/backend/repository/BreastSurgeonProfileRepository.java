package com.cancerweb.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.cancerweb.backend.model.BreastSurgeonProfile;
import com.cancerweb.backend.model.SurgeonProfileStatus;

public interface BreastSurgeonProfileRepository extends MongoRepository<BreastSurgeonProfile, String> {

    Optional<BreastSurgeonProfile> findByUserId(String userId);

    boolean existsByUserId(String userId);

    List<BreastSurgeonProfile> findAllByStatusAndSpecialtyOrderByFullNameAsc(SurgeonProfileStatus status, String specialty);

    List<BreastSurgeonProfile> findAllByOrderBySubmittedAtDesc();

    List<BreastSurgeonProfile> findAllByStatusOrderBySubmittedAtDesc(SurgeonProfileStatus status);
}
