package com.hackathon.distress.repository;

import com.hackathon.distress.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    // SELECT * FROM contacts ORDER BY priority_order
    List<Contact> findAllByOrderByPriorityOrderAsc();
}
