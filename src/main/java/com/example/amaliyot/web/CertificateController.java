package com.example.amaliyot.web;

import com.example.amaliyot.security.AuthPrincipal;
import com.example.amaliyot.service.CertificateService;
import com.example.amaliyot.web.ApiDtos.CertificateView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/certificates")
@Tag(name = "Sertifikatlar", description = "Yo‘nalish imtihonidan o‘tgan foydalanuvchi sertifikatlari")
public class CertificateController {
    private final CertificateService certificates;

    public CertificateController(CertificateService certificates) {
        this.certificates = certificates;
    }

    @Operation(summary = "Mening sertifikatlarim")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public List<CertificateView> mine(@AuthenticationPrincipal AuthPrincipal principal) {
        return certificates.myCertificates(principal.userId());
    }

    @Operation(summary = "Sertifikat tafsiloti", description = "Faqat sertifikat egasi ko‘ra oladi.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{certificateNumber}")
    public CertificateView mine(@AuthenticationPrincipal AuthPrincipal principal,
                                @PathVariable String certificateNumber) {
        return certificates.myCertificate(principal.userId(), certificateNumber);
    }

    @Operation(summary = "Sertifikatni tekshirish", description = "Sertifikat raqami bo‘yicha uning haqiqiyligini tekshiradi.")
    @GetMapping("/verify/{certificateNumber}")
    public CertificateView verify(@PathVariable String certificateNumber) {
        return certificates.verify(certificateNumber);
    }
}
