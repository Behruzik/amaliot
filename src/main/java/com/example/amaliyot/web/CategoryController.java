package com.example.amaliyot.web;

import com.example.amaliyot.repository.CategoryRepository;
import com.example.amaliyot.repository.UserAccountRepository;
import com.example.amaliyot.security.AuthPrincipal;
import com.example.amaliyot.web.ApiDtos.CategoryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Kategoriyalar", description = "Foydalanuvchilar uchun faol test kategoriyalari")
public class CategoryController {
    private final CategoryRepository categories;
    private final UserAccountRepository users;
    public CategoryController(CategoryRepository categories, UserAccountRepository users) {
        this.categories = categories; this.users = users;
    }
    @Operation(summary = "Faol kasb va test yo‘nalishlari", description = "Kasb tanlanmagan bo‘lsa katalogni qaytaradi; login qilingan foydalanuvchida kasb tanlangan bo‘lsa faqat shu kasb testini ko‘rsatadi.")
    @Transactional(readOnly = true)
    @GetMapping public java.util.List<CategoryView> list(@AuthenticationPrincipal AuthPrincipal principal) {
        var activeCategories = categories.findByActiveTrueOrderByNameAsc();
        if (principal != null) {
            var user = users.findById(principal.userId()).orElse(null);
            if (user != null && user.getProfessionCategory() != null) {
                Long professionId = user.getProfessionCategory().getId();
                activeCategories = activeCategories.stream().filter(c -> c.getId().equals(professionId)).toList();
            }
        }
        return activeCategories.stream()
                .map(c -> new CategoryView(c.getId(), c.getName(), c.getDescription(), c.getImageUrl(), c.isActive(),
                        c.getDurationMinutes(), c.getQuestionCount(), c.getAdditionalQuestionCount(), c.getRetakeDelayHours())).toList();
    }
}
