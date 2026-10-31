package com.example.amaliyot.web;

import com.example.amaliyot.domain.ContentType;
import com.example.amaliyot.service.ManagedContentService;
import com.example.amaliyot.web.ApiDtos.ManagedContentView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/content")
@Tag(name = "Ommaviy sahifa matnlari", description = "Kirish va Biz haqimizda sahifalari uchun administrator kiritgan matnlar")
public class ContentController {
    private final ManagedContentService content;

    public ContentController(ManagedContentService content) { this.content = content; }

    @Operation(summary = "Ommaviy sahifa matnini olish", description = "Mavjud matn bo‘lmasa, bo‘sh content qaytariladi.")
    @GetMapping("/{type}")
    public ManagedContentView get(@PathVariable ContentType type) { return content.get(type); }
}
