package com.cancerweb.backend.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.cancerweb.backend.model.SiteSettings;

public interface SiteSettingsRepository extends MongoRepository<SiteSettings, String> {
}
