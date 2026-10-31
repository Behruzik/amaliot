package com.example.amaliyot.web;

import com.example.amaliyot.domain.ContentType;
import com.example.amaliyot.security.AuthPrincipal;
import com.example.amaliyot.service.ManagedContentService;
import com.example.amaliyot.web.ApiDtos.ManagedContentRequest;
import com.example.amaliyot.web.ApiDtos.ManagedContentView;
import com.example.amaliyot.web.ApiDtos.MessageView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/content")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Sahifa matnlarini boshqarish", description = "ABOUT_WE va LOGIN_PAGE kontentlarini xavfsiz boshqarish")
@SecurityRequirement(name = "bearerAuth")
public class AdminContentController {
    private final ManagedContentService content;

    public AdminContentController(ManagedContentService content) { this.content = content; }

    @Operation(summary = "Boshqariladigan matnlarni olish")
    @GetMapping
    public List<ManagedContentView> list() { return content.list(); }

    @Operation(summary = "Sahifa matnini yaratish yoki yangilash", description = "Bir kontent turi uchun yagona matn saqlanadi.")
    @PutMapping("/{type}")
    public ManagedContentView upsert(@PathVariable ContentType type,
                                     @Valid @RequestBody ManagedContentRequest request,
                                     @AuthenticationPrincipal AuthPrincipal actor) {
        return content.upsert(type, request, actor.userId());
    }

    @Operation(summary = "Sahifa matnini o‘chirish")
    @DeleteMapping("/{type}")
    public MessageView delete(@PathVariable ContentType type, @AuthenticationPrincipal AuthPrincipal actor) {
        return content.delete(type, actor.userId());
    }
}
