package com.example.amaliyot.repository;

import com.example.amaliyot.domain.Entities.RetakeGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RetakeGrantRepository extends JpaRepository<RetakeGrant, Long> {
    Optional<RetakeGrant> findFirstByUserIdAndCategoryIdAndConsumedFalseOrderByCreatedAtAsc(Long userId, Long categoryId);
}
