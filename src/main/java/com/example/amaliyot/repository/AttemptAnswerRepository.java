package com.example.amaliyot.repository;

import com.example.amaliyot.domain.Entities.AttemptAnswer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {
    Optional<AttemptAnswer> findByAttemptQuestionId(Long attemptQuestionId);
    @EntityGraph(attributePaths = {"attemptQuestion", "selectedOptionIds"})
    List<AttemptAnswer> findByAttemptQuestionAttemptId(Long attemptId);
    void deleteByAttemptQuestionId(Long attemptQuestionId);
}
