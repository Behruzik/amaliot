package com.example.amaliyot.web;

import com.example.amaliyot.security.AuthPrincipal;
import com.example.amaliyot.service.LocationCatalogService;
import com.example.amaliyot.web.ApiDtos.DistrictRequest;
import com.example.amaliyot.web.ApiDtos.DistrictView;
import com.example.amaliyot.web.ApiDtos.MessageView;
import com.example.amaliyot.web.ApiDtos.RegionRequest;
import com.example.amaliyot.web.ApiDtos.RegionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/locations")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Manzil katalogini boshqarish", description = "ADMIN viloyat va tumanlarni qo‘shadi, tahrirlaydi yoki faol holatini o‘zgartiradi.")
@SecurityRequirement(name = "bearerAuth")
public class AdminLocationController {
    private final LocationCatalogService locations;

    public AdminLocationController(LocationCatalogService locations) { this.locations = locations; }

    @Operation(summary = "Barcha viloyatlarni olish")
    @GetMapping("/regions")
    public List<RegionView> regions() { return locations.allRegions(); }

    @Operation(summary = "Viloyat yaratish")
    @PostMapping("/regions")
    public RegionView createRegion(@Valid @RequestBody RegionRequest request,
                                   @AuthenticationPrincipal AuthPrincipal actor) {
        return locations.createRegion(request, actor.userId());
    }

    @Operation(summary = "Viloyatni yangilash")
    @PutMapping("/regions/{id}")
    public RegionView updateRegion(@PathVariable Long id, @Valid @RequestBody RegionRequest request,
                                   @AuthenticationPrincipal AuthPrincipal actor) {
        return locations.updateRegion(id, request, actor.userId());
    }

    @Operation(summary = "Viloyatni o‘chirish", description = "Tarixiy profil ma’lumotlarini saqlab, viloyatni katalogdan nofaol qiladi.")
    @DeleteMapping("/regions/{id}")
    public MessageView deactivateRegion(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal actor) {
        locations.deactivateRegion(id, actor.userId());
        return new MessageView("Region deactivated");
    }

    @Operation(summary = "Barcha tumanlarni olish")
    @GetMapping("/districts")
    public List<DistrictView> districts(@RequestParam(required = false) Long regionId) {
        return locations.allDistricts(regionId);
    }

    @Operation(summary = "Tuman yaratish")
    @PostMapping("/districts")
    public DistrictView createDistrict(@Valid @RequestBody DistrictRequest request,
                                       @AuthenticationPrincipal AuthPrincipal actor) {
        return locations.createDistrict(request, actor.userId());
    }

    @Operation(summary = "Tumanni yangilash")
    @PutMapping("/districts/{id}")
    public DistrictView updateDistrict(@PathVariable Long id, @Valid @RequestBody DistrictRequest request,
                                       @AuthenticationPrincipal AuthPrincipal actor) {
        return locations.updateDistrict(id, request, actor.userId());
    }

    @Operation(summary = "Tumanni o‘chirish", description = "Tumanni fizik o‘chirmasdan nofaol qiladi.")
    @DeleteMapping("/districts/{id}")
    public MessageView deactivateDistrict(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal actor) {
        locations.deactivateDistrict(id, actor.userId());
        return new MessageView("District deactivated");
    }
}
