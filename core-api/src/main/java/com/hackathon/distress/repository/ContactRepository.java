package com.hackathon.distress.repository;

import com.hackathon.distress.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * ContactRepository.java
 * -----------------------
 * Spring Data JPA generates the implementation of this interface at
 * startup - we never write SQL for basic CRUD. `findAllByOrderByPriorityOrderAsc`
 * is a "query method": Spring reads the method name and builds the query
 * "SELECT * FROM contacts ORDER BY priority_order ASC" from it.
 */
public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findAllByOrderByPriorityOrderAsc();
}
