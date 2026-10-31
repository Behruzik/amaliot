package com.example.amaliyot.repository;

import com.example.amaliyot.domain.Entities.Question;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {
    @EntityGraph(attributePaths = "options")
    List<Question> findByCategoryIdAndActiveTrue(Long categoryId);
    @EntityGraph(attributePaths = "options")
    List<Question> findByCategoryIdInAndActiveTrue(Collection<Long> categoryIds);
    @EntityGraph(attributePaths = {"options", "category"})
    Optional<Question> findWithOptionsById(Long id);
}
