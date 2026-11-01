package com.example.amaliyot.web;

import com.example.amaliyot.repository.CategoryRepository;
import com.example.amaliyot.web.ApiDtos.CategoryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/professions")
@Tag(name = "Kasblar", description = "Profil uchun kasb tanlash katalogi")
public class ProfessionController {
    private final CategoryRepository categories;

    public ProfessionController(CategoryRepository categories) {
        this.categories = categories;
    }

    @Operation(summary = "Faol kasblar ro‘yxati", description = "Profilning Kasb maydonida tanlash uchun to‘liq faol kasblar katalogini qaytaradi.")
    @Transactional(readOnly = true)
    @GetMapping
    public java.util.List<CategoryView> list() {
        return categories.findByActiveTrueOrderByNameAsc().stream()
                .map(category -> new CategoryView(category.getId(), category.getName(), category.getDescription(),
                        category.getImageUrl(), category.isActive(), category.getDurationMinutes(),
                        category.getQuestionCount(), category.getAdditionalQuestionCount(),
                        category.getRetakeDelayHours(), null, null, null, null, null, null,
                        category.getCreatedAt(), category.getUpdatedAt(), category.getDeletedAt()))
                .toList();
    }
}
