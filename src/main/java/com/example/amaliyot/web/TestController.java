package com.example.amaliyot.web;

import com.example.amaliyot.security.AuthPrincipal;
import com.example.amaliyot.service.AttemptService;
import com.example.amaliyot.web.ApiDtos.*;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tests")
@Tag(name = "Testlar va natijalar", description = "Test urinishlari, javoblarni saqlash va foydalanuvchi natijalari")
@SecurityRequirement(name = "bearerAuth")
public class TestController {
    private final AttemptService attempts;
    public TestController(AttemptService attempts) { this.attempts = attempts; }

    @Operation(summary = "16 savollik imtihonni boshlash", description = "Tanlangan kategoriya bo‘yicha umumiy bankdagi 400 ta oson savoldan 16 tasini tasodifiy tanlaydi. Kamida 14 ta to‘g‘ri javob sertifikat beradi.")
    @PostMapping("/categories/{categoryId}/attempts")
    public AttemptView start(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long categoryId) {
        return attempts.start(principal.userId(), categoryId);
    }
    @Operation(summary = "Faol urinishni ko‘rish", description = "Faqat urinish egasiga savollar va saqlangan javoblarni beradi; javob kaliti yuborilmaydi.")
    @GetMapping("/attempts/{attemptId}")
    public AttemptView get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long attemptId) {
        return attempts.get(principal.userId(), attemptId);
    }
    @Operation(summary = "Saqlangan va davom ettirish mumkin bo‘lgan imtihonlar", description = "Joriy foydalanuvchining hali yakunlanmagan imtihonlari va javoblari bazada saqlanib turadi.")
    @GetMapping("/attempts/saved")
    public java.util.List<SavedAttemptView> saved(@AuthenticationPrincipal AuthPrincipal principal) {
        return attempts.savedAttempts(principal.userId());
    }
    @Operation(summary = "Imtihonni keyin davom ettirish uchun saqlash", description = "Javoblar allaqachon avtomatik saqlanadi. Bu endpoint urinishni ro‘yxatga belgilaydi; imtihon vaqti to‘xtamaydi.")
    @PostMapping("/attempts/{attemptId}/save")
    public AttemptView save(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long attemptId) {
        return attempts.saveForLater(principal.userId(), attemptId);
    }
    @Operation(summary = "Saqlangan imtihonni davom ettirish", description = "Savollar va saqlangan javoblarni qaytaradi. Muddati o‘tgan urinish davom ettirilmaydi.")
    @PostMapping("/attempts/{attemptId}/resume")
    public AttemptView resume(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long attemptId) {
        return attempts.resume(principal.userId(), attemptId);
    }
    @Operation(summary = "Savol javobini saqlash")
    @PutMapping("/attempts/{attemptId}/questions/{questionId}/answer")
    public AttemptView answer(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long attemptId,
                              @PathVariable Long questionId, @Valid @RequestBody AnswerRequest request) {
        return attempts.answer(principal.userId(), attemptId, questionId, request);
    }
    @Operation(summary = "Imtihonni yakunlash", description = "16 savoldan kamida 14 tasi to‘g‘ri bo‘lsa, sertifikat avtomatik beriladi. Ball serverda hisoblanadi; takroriy yakunlash natijani o‘zgartirmaydi.")
    @PostMapping("/attempts/{attemptId}/finish")
    public ResultView finish(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long attemptId) {
        return attempts.finish(principal.userId(), attemptId);
    }
    @Operation(summary = "Mening natijalarim", description = "Joriy foydalanuvchining sahifalangan natijalari.")
    @GetMapping("/results")
    public Page<ResultView> myResults(@AuthenticationPrincipal AuthPrincipal principal,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return attempts.myResults(principal.userId(), page, size);
    }
    @Operation(summary = "Bitta natijani ko‘rish", description = "Faqat natija egasi o‘zining yakuniy natijasini ko‘ra oladi.")
    @GetMapping("/results/{attemptId}")
    public ResultView result(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long attemptId) {
        return attempts.myResult(principal.userId(), attemptId);
    }
}
