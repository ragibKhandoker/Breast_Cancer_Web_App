package com.cancerweb.backend.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.cancerweb.backend.model.RevokedToken;

public interface RevokedTokenRepository extends MongoRepository<RevokedToken, String> {
}
