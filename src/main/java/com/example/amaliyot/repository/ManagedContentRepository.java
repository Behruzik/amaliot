package com.example.amaliyot.repository;

import com.example.amaliyot.domain.ContentType;
import com.example.amaliyot.domain.Entities.ManagedContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ManagedContentRepository extends JpaRepository<ManagedContent, Long> {
    Optional<ManagedContent> findByType(ContentType type);
}
