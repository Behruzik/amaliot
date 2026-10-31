package com.example.amaliyot.repository;

import com.example.amaliyot.domain.Entities.TestAttempt;
import com.example.amaliyot.domain.AttemptStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import jakarta.persistence.LockModeType;

public interface TestAttemptRepository extends JpaRepository<TestAttempt, Long>, JpaSpecificationExecutor<TestAttempt> {
    @EntityGraph(attributePaths = {"category"})
    List<TestAttempt> findByUserId(Long userId);
    @EntityGraph(attributePaths = {"category"})
    List<TestAttempt> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, AttemptStatus status);
    Optional<TestAttempt> findFirstByUserIdAndCategoryIdOrderByCreatedAtDesc(Long userId, Long categoryId);
    boolean existsByUserIdAndCategoryIdAndStatus(Long userId, Long categoryId, AttemptStatus status);
    long countByStatus(AttemptStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<TestAttempt> findByStatusAndDeadlineAtLessThanEqual(AttemptStatus status, Instant now);
    @EntityGraph(attributePaths = {"category", "questions", "questions.options"})
    Optional<TestAttempt> findWithSnapshotById(Long id);
    @EntityGraph(attributePaths = {"category", "questions", "questions.options"})
    Optional<TestAttempt> findWithSnapshotByIdAndUserId(Long id, Long userId);
    // Lock only the parent row. Fetching the snapshot with an outer join in the
    // locking statement is not portable to PostgreSQL (FOR UPDATE on nullable joins).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TestAttempt> findLockedByIdAndUserId(Long id, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TestAttempt> findLockedById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<TestAttempt> findByUserIdAndCategoryIdAndStatus(Long userId, Long categoryId, AttemptStatus status);
}
