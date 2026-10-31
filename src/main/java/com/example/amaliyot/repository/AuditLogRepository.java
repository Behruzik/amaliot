package com.example.amaliyot.repository;

import com.example.amaliyot.domain.Entities.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
