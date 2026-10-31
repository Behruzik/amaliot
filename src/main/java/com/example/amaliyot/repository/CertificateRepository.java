package com.example.amaliyot.repository;

import com.example.amaliyot.domain.Entities.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    List<Certificate> findByUserIdAndRevokedAtIsNullOrderByIssuedAtDesc(Long userId);
    List<Certificate> findByUserIdOrderByIssuedAtDesc(Long userId);
    Optional<Certificate> findByCertificateNumberAndUserId(String certificateNumber, Long userId);
    Optional<Certificate> findByCertificateNumber(String certificateNumber);
    Optional<Certificate> findByAttemptId(Long attemptId);
}
