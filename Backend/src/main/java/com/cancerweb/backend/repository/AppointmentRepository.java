package com.cancerweb.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.cancerweb.backend.model.Appointment;

public interface AppointmentRepository extends MongoRepository<Appointment, String> {

    List<Appointment> findAllByPatientIdOrderByDateDescTimeDesc(String patientId);

    List<Appointment> findAllBySurgeonUserIdOrderByDateAscTimeAsc(String surgeonUserId);

    Optional<Appointment> findByIdAndPatientId(String id, String patientId);

    Optional<Appointment> findByIdAndSurgeonUserId(String id, String surgeonUserId);
}
