package com.example.amaliyot.web;

import com.example.amaliyot.security.AuthPrincipal;
import com.example.amaliyot.service.SupportService;
import com.example.amaliyot.web.ApiDtos.SupportRequestCreate;
import com.example.amaliyot.web.ApiDtos.SupportRequestUpdate;
import com.example.amaliyot.web.ApiDtos.SupportRequestView;
import com.example.amaliyot.web.ApiDtos.SupportContactView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@RestController
@RequestMapping("/api/support/requests")
@Tag(name = "Qayta aloqa", description = "Foydalanuvchi murojaatlari va Support Team javoblari")
@SecurityRequirement(name = "bearerAuth")
public class SupportController {
    private final SupportService support;
    private final String contactPhone;
    public SupportController(SupportService support, @Value("${SUPER_ADMIN_PHONE:}") String contactPhone) {
        this.support = support; this.contactPhone = contactPhone;
    }

    @Operation(summary = "Support bilan bog‘lanish usullari")
    @GetMapping("/contact") public SupportContactView contact() {
        return support.contact(contactPhone);
    }

    @Operation(summary = "Murojaat yoki fikr qoldirish", description = "PHONE_CALL, APPLICATION yoki FEEDBACK turidan foydalaning.")
    @PostMapping public SupportRequestView create(@Valid @RequestBody SupportRequestCreate input,
                                                  @AuthenticationPrincipal AuthPrincipal actor) {
        return support.create(actor.userId(), input);
    }

    @Operation(summary = "O‘z murojaatlarim")
    @GetMapping("/mine") public List<SupportRequestView> mine(@AuthenticationPrincipal AuthPrincipal actor) {
        return support.mine(actor.userId());
    }

    @Operation(summary = "Barcha murojaatlarni ko‘rish", description = "Nickname, email yoki telefon bo‘yicha qidirish mumkin.")
    @GetMapping @PreAuthorize("hasAnyRole('STAFF','ADMIN','SUPER_ADMIN')")
    public List<SupportRequestView> list(@RequestParam(required = false) String search,
                                        @RequestParam(required = false) String status) {
        return support.list(search, status);
    }

    @Operation(summary = "Murojaat holati va javobini yangilash")
    @PatchMapping("/{id}") @PreAuthorize("hasAnyRole('STAFF','ADMIN','SUPER_ADMIN')")
    public SupportRequestView update(@PathVariable Long id, @Valid @RequestBody SupportRequestUpdate input,
                                     @AuthenticationPrincipal AuthPrincipal actor) {
        return support.update(id, input, actor.userId());
    }
}
