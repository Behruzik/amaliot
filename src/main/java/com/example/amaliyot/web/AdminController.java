package com.example.amaliyot.web;

import com.example.amaliyot.domain.AttemptStatus;
import com.example.amaliyot.domain.AnswerType;
import com.example.amaliyot.domain.Role;
import com.example.amaliyot.security.AuthPrincipal;
import com.example.amaliyot.service.AdminService;
import com.example.amaliyot.service.LocalImageStorage;
import com.example.amaliyot.web.ApiDtos.*;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('ADMIN','STAFF','SUPER_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {
    private final AdminService admin;
    private final LocalImageStorage images;
    public AdminController(AdminService admin, LocalImageStorage images) { this.admin = admin; this.images = images; }

    @Operation(summary = "Dashboard statistikasi")
    @Tag(name = "Admin — Boshqaruv paneli", description = "Umumiy tizim statistikasi")
    @GetMapping("/dashboard") public DashboardView dashboard() { return admin.dashboard(); }

    @Operation(summary = "Foydalanuvchilarni qidirish va sahifalash")
    @Tag(name = "Admin — Foydalanuvchilar", description = "User, Staff va akkaunt rollarini boshqarish")
    @GetMapping("/users")
    public Page<UserView> users(@RequestParam(required = false) String search,
                                @RequestParam(required = false) Role role,
                                @RequestParam(required = false) Boolean active,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return admin.users(search, role, active, page, size);
    }
    @Operation(summary = "Foydalanuvchi yoki Staff akkauntini yaratish", description = "Faqat ADMIN yoki SUPER_ADMIN ishlata oladi. Unikal @gmail.com email va telefon talab qilinadi. STAFF akkauntini Admin yoki Super Admin yaratadi; ADMIN rolini faqat SUPER_ADMIN bera oladi.")
    @Tag(name = "Admin — Foydalanuvchilar")
    @PostMapping("/users") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public UserView createUser(@Valid @RequestBody AdminUserRequest request, @AuthenticationPrincipal AuthPrincipal actor) {
        return admin.createUser(request, actor.userId());
    }
    @Operation(summary = "Akkauntni faollashtirish yoki deaktivatsiya qilish", description = "Faqat ADMIN yoki SUPER_ADMIN ishlata oladi, shu jumladan Staff akkauntini deaktivatsiya qilish.")
    @Tag(name = "Admin — Foydalanuvchilar")
    @PatchMapping("/users/{userId}/active") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public UserView setUserActive(@PathVariable Long userId, @RequestBody UserStatusRequest request,
                                  @AuthenticationPrincipal AuthPrincipal actor) {
        return admin.setUserActive(userId, request.active(), actor.userId());
    }
    @Operation(summary = "Foydalanuvchi ma’lumotlarini tahrirlash", description = "Akkauntga ADMIN rolini faqat SUPER_ADMIN bera oladi.")
    @Tag(name = "Admin — Foydalanuvchilar")
    @PutMapping("/users/{userId}") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public UserView updateUser(@PathVariable Long userId, @Valid @RequestBody AdminUserUpdateRequest request,
                               @AuthenticationPrincipal AuthPrincipal actor) {
        return admin.updateUser(userId, request, actor.userId());
    }

    @Operation(summary = "Barcha kategoriyalarni boshqarish")
    @Tag(name = "Admin — Kategoriyalar", description = "Imtihon yo‘nalishlarini yaratish, tahrirlash va arxivlash")
    @GetMapping("/categories") public List<CategoryView> categories() { return admin.categories(); }
    @Operation(summary = "Kategoriya yaratish", description = "Faqat ADMIN va SUPER_ADMIN yangi imtihon kategoriyasini yarata oladi.")
    @Tag(name = "Admin — Kategoriyalar")
    @PostMapping("/categories") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public CategoryView createCategory(@Valid @RequestBody CategoryRequest request, @AuthenticationPrincipal AuthPrincipal actor) {
        return admin.createCategory(request, actor.userId());
    }
    @Operation(summary = "Kategoriyani tahrirlash")
    @Tag(name = "Admin — Kategoriyalar")
    @PutMapping("/categories/{id}") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public CategoryView updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request,
                                       @AuthenticationPrincipal AuthPrincipal actor) {
        return admin.updateCategory(id, request, actor.userId());
    }
    @Operation(summary = "Kategoriyani arxivlash", description = "Kategoriyani o‘chirmasdan faol holatini o‘chiradi.")
    @Tag(name = "Admin — Kategoriyalar")
    @DeleteMapping("/categories/{id}") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public MessageView archiveCategory(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal actor) {
        admin.archiveCategory(id, actor.userId()); return new MessageView("Category deactivated");
    }

    @Operation(summary = "Kategoriya rasmini yuklash", description = "PNG, JPEG yoki WebP rasm; maksimal hajm 3 MB.")
    @Tag(name = "Admin — Kategoriyalar")
    @PostMapping("/categories/{id}/image") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public CategoryView categoryImage(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                      @AuthenticationPrincipal AuthPrincipal actor) {
        return admin.setCategoryImage(id, images.save(file), actor.userId());
    }

    @Operation(summary = "Savollarni filtrlash va sahifalash")
    @Tag(name = "Admin — Savollar", description = "Savol banki va savollarga tegishli rasmlarni boshqarish")
    @GetMapping("/questions")
    public Page<QuestionView> questions(@RequestParam(required = false) Long categoryId,
                                        @RequestParam(required = false) String search,
                                        @RequestParam(required = false) AnswerType type,
                                        @RequestParam(required = false) com.example.amaliyot.domain.Difficulty difficulty,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return admin.questions(categoryId, search, type, difficulty, page, size);
    }
    @Operation(summary = "Savol tafsilotini ko‘rish")
    @Tag(name = "Admin — Savollar")
    @GetMapping("/questions/{id}") public QuestionView question(@PathVariable Long id) { return admin.question(id); }
    @Operation(summary = "Savol yaratish", description = "Faqat ADMIN va SUPER_ADMIN savol qo‘shadi. globalPool=true bo‘lsa categoryId shart emas.")
    @Tag(name = "Admin — Savollar")
    @PostMapping("/questions") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public QuestionView createQuestion(@Valid @RequestBody QuestionRequest request, @AuthenticationPrincipal AuthPrincipal actor) {
        return admin.createQuestion(request, actor.userId());
    }
    @Operation(summary = "Savolni tahrirlash")
    @Tag(name = "Admin — Savollar")
    @PutMapping("/questions/{id}") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public QuestionView updateQuestion(@PathVariable Long id, @Valid @RequestBody QuestionRequest request,
                                       @AuthenticationPrincipal AuthPrincipal actor) {
        return admin.updateQuestion(id, request, actor.userId());
    }
    @Operation(summary = "Savolga rasm yuklash", description = "PNG, JPEG yoki WebP rasm faylini yuklaydi; maksimal hajm 3 MB.")
    @Tag(name = "Admin — Savollar")
    @PostMapping("/questions/{id}/image") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public QuestionView questionImage(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                      @AuthenticationPrincipal AuthPrincipal actor) {
        return admin.setQuestionImage(id, images.save(file), actor.userId());
    }
    @Operation(summary = "Savolni arxivlash", description = "Savolni o‘chirmasdan faol holatini o‘chiradi.")
    @Tag(name = "Admin — Savollar")
    @DeleteMapping("/questions/{id}") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public MessageView archiveQuestion(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal actor) {
        admin.archiveQuestion(id, actor.userId()); return new MessageView("Question deactivated");
    }

    @Operation(summary = "Test urinishlarini qidirish")
    @Tag(name = "Admin — Imtihon natijalari", description = "Natijalarni qidirish, arxivlash va ko‘rib chiqish")
    @GetMapping("/attempts")
    public Page<AdminAttemptView> attempts(@RequestParam(required = false) String search,
                                           @RequestParam(required = false) Long categoryId,
                                           @RequestParam(required = false) AttemptStatus status,
                                           @RequestParam(defaultValue = "false") boolean archived,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return admin.attempts(search, categoryId, status, archived, page, size);
    }
    @Operation(summary = "Arxivlangan natijalar ro‘yxati")
    @Tag(name = "Admin — Imtihon natijalari")
    @GetMapping("/archive")
    public Page<AdminAttemptView> archivedAttempts(@RequestParam(required = false) String search,
                                                   @RequestParam(required = false) Long categoryId,
                                                   @RequestParam(required = false) AttemptStatus status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return admin.attempts(search, categoryId, status, true, page, size);
    }
    @Operation(summary = "Urinish tafsilotlari va javoblarini ko‘rish")
    @Tag(name = "Admin — Imtihon natijalari")
    @GetMapping("/attempts/{id}") public AdminAttemptView attempt(@PathVariable Long id) { return admin.attempt(id); }
    @Operation(summary = "Natijani arxivga ko‘chirish", description = "Yakunlangan urinishni statusini o‘zgartirmasdan arxivlaydi.")
    @Tag(name = "Admin — Imtihon natijalari")
    @PostMapping("/attempts/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public AdminAttemptView archiveAttempt(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal actor) {
        return admin.archiveAttempt(id, true, actor.userId());
    }
    @Operation(summary = "Natijani arxivdan qaytarish")
    @Tag(name = "Admin — Imtihon natijalari")
    @PostMapping("/attempts/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public AdminAttemptView restoreAttempt(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal actor) {
        return admin.archiveAttempt(id, false, actor.userId());
    }
    @Operation(summary = "Natijani tasdiqlash", description = "Faqat ADMIN yoki SUPER_ADMIN.")
    @Tag(name = "Admin — Imtihon natijalari")
    @PostMapping("/attempts/{id}/approve") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public AdminAttemptView approveAttempt(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal actor) {
        return admin.changeAttemptStatus(id, AttemptStatus.APPROVED, actor.userId());
    }
    @Operation(summary = "Natijani bekor qilish", description = "Faqat ADMIN yoki SUPER_ADMIN.")
    @Tag(name = "Admin — Imtihon natijalari")
    @PostMapping("/attempts/{id}/cancel") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public AdminAttemptView cancelAttempt(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal actor) {
        return admin.changeAttemptStatus(id, AttemptStatus.CANCELLED, actor.userId());
    }
    @Operation(summary = "Qayta topshirishga ruxsat berish", description = "Foydalanuvchi va kategoriya uchun bir martalik ruxsat yaratadi. Faqat ADMIN yoki SUPER_ADMIN.")
    @Tag(name = "Admin — Imtihon natijalari")
    @PostMapping("/retake-grants") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public MessageView grantRetake(@Valid @RequestBody GrantRetakeRequest request, @AuthenticationPrincipal AuthPrincipal actor) {
        return admin.grantRetake(request, actor.userId());
    }
}
