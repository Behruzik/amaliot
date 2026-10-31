package com.example.amaliyot.web;

import com.example.amaliyot.service.LocationCatalogService;
import com.example.amaliyot.web.ApiDtos.DistrictView;
import com.example.amaliyot.web.ApiDtos.RegionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
@Tag(name = "Manzillar", description = "Ro‘yxatdan o‘tish va profil formalaridagi faol viloyat/tuman katalogi")
public class LocationController {
    private final LocationCatalogService locations;

    public LocationController(LocationCatalogService locations) { this.locations = locations; }

    @Operation(summary = "Faol viloyatlarni olish")
    @GetMapping("/regions")
    public List<RegionView> regions() { return locations.publicRegions(); }

    @Operation(summary = "Viloyat bo‘yicha faol tumanlarni olish")
    @GetMapping("/regions/{regionId}/districts")
    public List<DistrictView> districtsForRegion(@PathVariable Long regionId) {
        return locations.publicDistricts(regionId);
    }

    @Operation(summary = "Faol tumanlar ro‘yxati", description = "`regionId` berilsa, natija shu viloyat bilan cheklanadi.")
    @GetMapping("/districts")
    public List<DistrictView> districts(@RequestParam(required = false) Long regionId) {
        return locations.publicDistricts(regionId);
    }
}
