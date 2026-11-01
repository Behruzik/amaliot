package com.example.amaliyot.web;

import com.example.amaliyot.repository.CategoryRepository;
import com.example.amaliyot.web.ApiDtos.CategoryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Kategoriyalar", description = "Foydalanuvchilar uchun faol test kategoriyalari")
public class CategoryController {
    private final CategoryRepository categories;
    public CategoryController(CategoryRepository categories) {
        this.categories = categories;
    }
    @Operation(summary = "Faol test kategoriyalari", description = "Barcha foydalanuvchilar uchun faol kategoriyalarni qaytaradi. Foydalanuvchi istalgan kategoriyadan test boshlashi mumkin; savollar umumiy oson savollar bankidan tasodifiy tanlanadi.")
    @Transactional(readOnly = true)
    @GetMapping public java.util.List<CategoryView> list() {
        return categories.findByActiveTrueOrderByNameAsc().stream()
                .map(c -> new CategoryView(c.getId(), c.getName(), c.getDescription(), c.getImageUrl(), c.isActive(),
                        c.getDurationMinutes(), c.getQuestionCount(), c.getAdditionalQuestionCount(), c.getRetakeDelayHours(),
                        null, null, null, null, null, null, c.getCreatedAt(), c.getUpdatedAt(), c.getDeletedAt())).toList();
    }
}
