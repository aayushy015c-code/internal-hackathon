package com.hackathon.distress.repository;

import com.hackathon.distress.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

// Every query is for ONE user, so nobody can see someone else's contacts.
public interface ContactRepository extends JpaRepository<Contact, Long> {

    // SELECT * FROM contacts WHERE user_id = ? ORDER BY priority_order
    List<Contact> findAllByUserIdOrderByPriorityOrderAsc(Long userId);

    Optional<Contact> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("delete from Contact c where c.userId = :userId")
    int deleteAllForUser(Long userId);

    // contacts saved before users existed belong to the first user
    @Modifying
    @Query("update Contact c set c.userId = :userId where c.userId is null")
    int claimUnowned(Long userId);
}
