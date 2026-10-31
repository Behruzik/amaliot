package com.example.amaliyot.repository;

import com.example.amaliyot.domain.Entities.GeographicDistrict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GeographicDistrictRepository extends JpaRepository<GeographicDistrict, Long> {
    @Query("select d from GeographicDistrict d join fetch d.region r "
            + "where d.active = true and r.active = true order by r.name, d.name")
    List<GeographicDistrict> findPublicDistricts();

    @Query("select d from GeographicDistrict d join fetch d.region r "
            + "where d.region.id = :regionId and d.active = true and r.active = true order by d.name")
    List<GeographicDistrict> findPublicDistrictsByRegionId(@Param("regionId") Long regionId);

    @Query("select d from GeographicDistrict d join fetch d.region r order by r.name, d.name")
    List<GeographicDistrict> findAllWithRegion();

    @Query("select d from GeographicDistrict d join fetch d.region r where d.region.id = :regionId order by d.name")
    List<GeographicDistrict> findAllWithRegionByRegionId(@Param("regionId") Long regionId);

    boolean existsByRegion_IdAndNameIgnoreCase(Long regionId, String name);
    boolean existsByRegion_IdAndNameIgnoreCaseAndIdNot(Long regionId, String name, Long id);
    boolean existsByRegion_IdAndNameIgnoreCaseAndActiveTrue(Long regionId, String name);
}
