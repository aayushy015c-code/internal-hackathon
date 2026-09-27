package com.hackathon.distress.repository;

import com.hackathon.distress.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByKeyHash(String keyHash);

    boolean existsByCallId(String callId);
}
