package com.example.amaliyot.service;

import com.example.amaliyot.domain.Entities.AuditLog;
import com.example.amaliyot.domain.Entities.GeographicDistrict;
import com.example.amaliyot.domain.Entities.GeographicRegion;
import com.example.amaliyot.repository.AuditLogRepository;
import com.example.amaliyot.repository.GeographicDistrictRepository;
import com.example.amaliyot.repository.GeographicRegionRepository;
import com.example.amaliyot.web.ApiDtos.DistrictRequest;
import com.example.amaliyot.web.ApiDtos.DistrictView;
import com.example.amaliyot.web.ApiDtos.RegionRequest;
import com.example.amaliyot.web.ApiDtos.RegionView;
import com.example.amaliyot.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LocationCatalogService {
    private final GeographicRegionRepository regions;
    private final GeographicDistrictRepository districts;
    private final AuditLogRepository audit;

    public LocationCatalogService(GeographicRegionRepository regions,
                                  GeographicDistrictRepository districts,
                                  AuditLogRepository audit) {
        this.regions = regions;
        this.districts = districts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RegionView> publicRegions() {
        return regions.findByActiveTrueOrderByNameAsc().stream().map(LocationCatalogService::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<DistrictView> publicDistricts(Long regionId) {
        List<GeographicDistrict> found = regionId == null
                ? districts.findPublicDistricts()
                : districts.findPublicDistrictsByRegionId(regionId);
        return found.stream().map(LocationCatalogService::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<RegionView> allRegions() {
        return regions.findAll().stream().sorted(java.util.Comparator.comparing(GeographicRegion::getName,
                        String.CASE_INSENSITIVE_ORDER))
                .map(LocationCatalogService::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<DistrictView> allDistricts(Long regionId) {
        List<GeographicDistrict> found = regionId == null
                ? districts.findAllWithRegion()
                : districts.findAllWithRegionByRegionId(regionId);
        return found.stream().map(LocationCatalogService::toView).toList();
    }

    @Transactional
    public RegionView createRegion(RegionRequest request, Long actorId) {
        String name = clean(request.name());
        if (regions.existsByNameIgnoreCase(name))
            throw ApiException.conflict("REGION_ALREADY_EXISTS", "A region with this name already exists");
        GeographicRegion region = new GeographicRegion();
        region.setName(name);
        region.setActive(request.active() == null || request.active());
        regions.save(region);
        log(actorId, "REGION_CREATED", "REGION", region.getId());
        return toView(region);
    }

    @Transactional
    public RegionView updateRegion(Long id, RegionRequest request, Long actorId) {
        GeographicRegion region = findRegion(id);
        String name = clean(request.name());
        if (regions.existsByNameIgnoreCaseAndIdNot(name, id))
            throw ApiException.conflict("REGION_ALREADY_EXISTS", "A region with this name already exists");
        region.setName(name);
        if (request.active() != null) region.setActive(request.active());
        log(actorId, "REGION_UPDATED", "REGION", id);
        return toView(region);
    }

    @Transactional
    public void deactivateRegion(Long id, Long actorId) {
        GeographicRegion region = findRegion(id);
        if (region.isActive()) {
            region.setActive(false);
            log(actorId, "REGION_DEACTIVATED", "REGION", id);
        }
    }

    @Transactional
    public DistrictView createDistrict(DistrictRequest request, Long actorId) {
        GeographicRegion region = findRegion(request.regionId());
        if (!region.isActive()) throw ApiException.conflict("REGION_INACTIVE", "An inactive region cannot receive new districts");
        String name = clean(request.name());
        if (districts.existsByRegion_IdAndNameIgnoreCase(request.regionId(), name))
            throw ApiException.conflict("DISTRICT_ALREADY_EXISTS", "A district with this name already exists in the selected region");
        GeographicDistrict district = new GeographicDistrict();
        district.setRegion(region);
        district.setName(name);
        district.setActive(request.active() == null || request.active());
        districts.save(district);
        log(actorId, "DISTRICT_CREATED", "DISTRICT", district.getId());
        return toView(district);
    }

    @Transactional
    public DistrictView updateDistrict(Long id, DistrictRequest request, Long actorId) {
        GeographicDistrict district = findDistrict(id);
        GeographicRegion region = findRegion(request.regionId());
        String name = clean(request.name());
        if (districts.existsByRegion_IdAndNameIgnoreCaseAndIdNot(request.regionId(), name, id))
            throw ApiException.conflict("DISTRICT_ALREADY_EXISTS", "A district with this name already exists in the selected region");
        district.setRegion(region);
        district.setName(name);
        if (request.active() != null) district.setActive(request.active());
        log(actorId, "DISTRICT_UPDATED", "DISTRICT", id);
        return toView(district);
    }

    @Transactional
    public void deactivateDistrict(Long id, Long actorId) {
        GeographicDistrict district = findDistrict(id);
        if (district.isActive()) {
            district.setActive(false);
            log(actorId, "DISTRICT_DEACTIVATED", "DISTRICT", id);
        }
    }

    @Transactional(readOnly = true)
    public void validateSelection(String regionName, String districtName) {
        String regionValue = clean(regionName);
        String districtValue = clean(districtName);
        if (regionValue == null) {
            if (districtValue != null)
                throw ApiException.badRequest("REGION_REQUIRED", "A region must be selected before a district");
            return;
        }
        GeographicRegion region = regions.findByNameIgnoreCaseAndActiveTrue(regionValue)
                .orElseThrow(() -> ApiException.badRequest("INVALID_REGION", "Select an active region from the location catalog"));
        if (districtValue != null && !districts.existsByRegion_IdAndNameIgnoreCaseAndActiveTrue(region.getId(), districtValue))
            throw ApiException.badRequest("INVALID_DISTRICT", "Select an active district belonging to the selected region");
    }

    private GeographicRegion findRegion(Long id) {
        return regions.findById(id).orElseThrow(() -> ApiException.notFound("REGION_NOT_FOUND", "Region was not found"));
    }

    private GeographicDistrict findDistrict(Long id) {
        return districts.findById(id).orElseThrow(() -> ApiException.notFound("DISTRICT_NOT_FOUND", "District was not found"));
    }

    private void log(Long actorId, String action, String type, Long id) {
        audit.save(new AuditLog(actorId, action, type, id, null));
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static RegionView toView(GeographicRegion region) {
        return new RegionView(region.getId(), region.getName(), region.isActive());
    }
    private static DistrictView toView(GeographicDistrict district) {
        return new DistrictView(district.getId(), district.getRegion().getId(), district.getRegion().getName(),
                district.getName(), district.isActive());
    }
}
