package com.example.amaliyot.web;

import com.example.amaliyot.security.AuthPrincipal;
import com.example.amaliyot.service.AuthService;
import com.example.amaliyot.web.ApiDtos.*;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autentifikatsiya", description = "Ro‘yxatdan o‘tish, tizimga kirish va parol amallari")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @Operation(summary = "Hisob yaratish", description = "Unikal @gmail.com emaili va telefon raqami bilan oddiy USER hisobini yaratadi. Nickname ixtiyoriy, parol kamida 6 belgidan iborat.")
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) { return auth.register(request); }
    @Operation(summary = "Tizimga kirish", description = "Email yoki avvalgi telefon akkaunti bilan kiradi va Bearer JWT token qaytaradi.")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) { return auth.login(request); }
    @Operation(summary = "Tizimdan chiqish", description = "Joriy JWT tokenni bekor qiladi.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public MessageView logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.logout(authorization); return new MessageView("Session ended");
    }
    @Operation(summary = "Parolni almashtirish", description = "Joriy parolni almashtiradi va oldingi tokenlarni bekor qiladi.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/password/change")
    public MessageView changePassword(@AuthenticationPrincipal AuthPrincipal principal,
                                      @Valid @RequestBody ChangePasswordRequest request,
                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.changePassword(principal.userId(), request, authorization);
        return new MessageView("Password changed; sign in again with the new password");
    }
    @Operation(summary = "Parolni tiklash so‘rovi", description = "SMS yoki email provayderi sozlanmagan bo‘lsa 503 qaytaradi.")
    @ApiResponses(@ApiResponse(responseCode = "503", description = "Parol tiklash provayderi sozlanmagan"))
    @PostMapping("/password/forgot")
    public MessageView forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        throw ApiException.unavailable("PASSWORD_RESET_PROVIDER_NOT_CONFIGURED",
                "Password reset is unavailable because no SMS or email provider is configured");
    }
    @Operation(summary = "Parolni tiklash", description = "SMS yoki email provayderi sozlanmagan bo‘lsa 503 qaytaradi.")
    @ApiResponses(@ApiResponse(responseCode = "503", description = "Parol tiklash provayderi sozlanmagan"))
    @PostMapping("/password/reset")
    public MessageView resetPassword() {
        throw ApiException.unavailable("PASSWORD_RESET_PROVIDER_NOT_CONFIGURED",
                "Password reset is unavailable because no SMS or email provider is configured");
    }
}
