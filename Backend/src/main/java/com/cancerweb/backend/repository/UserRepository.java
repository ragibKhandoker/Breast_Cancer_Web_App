package com.cancerweb.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;

public interface UserRepository extends MongoRepository<User, String> {

    /** {@code email} must already be normalised (trimmed, lower case). */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findAllByOrderByCreatedAtDesc();

    List<User> findAllByRoleOrderByFullNameAsc(Role role);
}
