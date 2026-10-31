package com.example.amaliyot.web;

import com.example.amaliyot.security.AuthPrincipal;
import com.example.amaliyot.service.LocalImageStorage;
import com.example.amaliyot.service.ProfileService;
import com.example.amaliyot.web.ApiDtos.ProfileUpdateRequest;
import com.example.amaliyot.web.ApiDtos.UserView;
import com.example.amaliyot.web.ApiDtos.UserDashboardView;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profil", description = "Joriy foydalanuvchining profili va avatarini boshqarish")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {
    private final ProfileService profiles;
    private final LocalImageStorage images;
    public ProfileController(ProfileService profiles, LocalImageStorage images) { this.profiles = profiles; this.images = images; }

    @Operation(summary = "Profilni ko‘rish")
    @GetMapping public UserView get(@AuthenticationPrincipal AuthPrincipal principal) { return profiles.get(principal.userId()); }
    @Operation(summary = "Foydalanuvchi menyusi statistikasi", description = "Ro‘yxatdan o‘tgan sana, faol vaqt, imtihon natijalari, yo‘nalishlar va sertifikatlar.")
    @GetMapping("/dashboard") public UserDashboardView dashboard(@AuthenticationPrincipal AuthPrincipal principal) {
        return profiles.dashboard(principal.userId());
    }
    @Operation(summary = "Profilni yangilash", description = "Faqat token egasining profil maydonlarini yangilaydi. Kasb tanlash uchun GET /api/professions dagi kasb ID sini professionId sifatida yuboring.")
    @PatchMapping public UserView update(@AuthenticationPrincipal AuthPrincipal principal,
                                         @Valid @RequestBody ProfileUpdateRequest request) {
        return profiles.update(principal.userId(), request);
    }
    @Operation(summary = "Avatar yuklash", description = "PNG, JPEG yoki WebP rasm faylini yuklaydi; maksimal hajm 3 MB.")
    @PostMapping("/avatar") public UserView avatar(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @RequestParam("file") MultipartFile file) {
        return profiles.updateAvatar(principal.userId(), images.save(file));
    }
}
