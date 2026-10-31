package com.example.amaliyot.web;

import com.example.amaliyot.security.AuthPrincipal;
import com.example.amaliyot.service.RecommendationService;
import com.example.amaliyot.web.ApiDtos.RecommendationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "Tavsiyalar", description = "Foydalanuvchining kasbiga mos testlar")
@SecurityRequirement(name = "bearerAuth")
public class RecommendationController {
    private final RecommendationService recommendations;

    public RecommendationController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @Operation(summary = "Kasbga mos test va uning tayyorlik holati")
    @GetMapping
    public RecommendationView get(@AuthenticationPrincipal AuthPrincipal principal) {
        return recommendations.forUser(principal.userId());
    }
}
