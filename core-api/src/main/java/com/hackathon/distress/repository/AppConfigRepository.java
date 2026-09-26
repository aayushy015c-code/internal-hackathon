package com.hackathon.distress.repository;

import com.hackathon.distress.entity.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

/** AppConfigRepository.java - CRUD for the single AppConfig row (id = 1). */
public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {
}
