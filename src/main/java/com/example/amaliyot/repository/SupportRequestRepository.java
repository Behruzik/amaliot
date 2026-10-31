package com.example.amaliyot.repository;

import com.example.amaliyot.domain.Entities.SupportRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {
    List<SupportRequest> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<SupportRequest> findAllByOrderByCreatedAtDesc();
}
