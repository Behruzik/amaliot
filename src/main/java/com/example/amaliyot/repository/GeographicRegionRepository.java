package com.example.amaliyot.repository;

import com.example.amaliyot.domain.Entities.GeographicRegion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GeographicRegionRepository extends JpaRepository<GeographicRegion, Long> {
    List<GeographicRegion> findByActiveTrueOrderByNameAsc();
    Optional<GeographicRegion> findByNameIgnoreCaseAndActiveTrue(String name);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
