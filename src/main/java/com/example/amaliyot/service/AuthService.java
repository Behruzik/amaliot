package com.example.amaliyot.service;

import com.example.amaliyot.domain.Entities.RevokedToken;
import com.example.amaliyot.domain.Entities.UserAccount;
import com.example.amaliyot.domain.Role;
import com.example.amaliyot.repository.RevokedTokenRepository;
import com.example.amaliyot.repository.UserAccountRepository;
import com.example.amaliyot.security.JwtService;
import com.example.amaliyot.web.ApiDtos.*;
import com.example.amaliyot.web.ApiException;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

@Service
public class AuthService {
    private final UserAccountRepository users;
    private final RevokedTokenRepository revokedTokens;
    private final PasswordStorageService passwords;
    private final JwtService jwt;
    private final LocationCatalogService locations;
    private final LoginGuard loginGuard = new LoginGuard();

    public AuthService(UserAccountRepository users, RevokedTokenRepository revokedTokens,
                       PasswordStorageService passwords, JwtService jwt, LocationCatalogService locations) {
        this.users = users; this.revokedTokens = revokedTokens; this.passwords = passwords; this.jwt = jwt;
        this.locations = locations;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeGmail(request.email());
        validateNewPassword(request.password());
        if (request.confirmPassword() != null && !request.password().equals(request.confirmPassword()))
            throw ApiException.badRequest("PASSWORD_CONFIRMATION_MISMATCH", "Password confirmation does not match");
        if (!request.offerAccepted()) throw ApiException.badRequest("OFFER_REQUIRED", "The public offer must be accepted");
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "This Gmail address is already registered");
        String phone = normalizePhone(request.phone());
        if (phoneTaken(users, phone, null)) throw ApiException.conflict("PHONE_ALREADY_REGISTERED", "This phone number is already registered");
        UserAccount user = new UserAccount(null, "",
                clean(request.firstName()), clean(request.lastName()), true);
        passwords.setPassword(user, request.password());
        user.setEmail(email);
        user.setPhone(phone);
        user.setNickname(clean(request.nickname()));
        user.setGender(clean(request.gender()));
        users.save(user);
        return issue(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identifier = normalizeLoginIdentifier(request.email());
        loginGuard.check(identifier);
        UserAccount user = identifier.indexOf('@') >= 0
                ? users.findLockedByEmailIgnoreCase(identifier).orElse(null)
                : users.findLockedByPhone(identifier).or(() -> users.findLockedByPhone(identifier.startsWith("+")
                        ? identifier.substring(1) : "+" + identifier)).orElse(null);
        if (user == null || !user.isActive() || !passwords.matchesAndUpgrade(request.password(), user)) {
            loginGuard.failed(identifier);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Phone/email or password is incorrect");
        }
        loginGuard.succeeded(identifier);
        revokedTokens.deleteByExpiresAtBefore(Instant.now());
        return issue(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request, String authorization) {
        UserAccount user = users.findByIdForUpdate(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User was not found"));
        if (!passwords.matchesAndUpgrade(request.currentPassword(), user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_PASSWORD", "Current password is incorrect");
        }
        validateNewPassword(request.newPassword());
        passwords.setPassword(user, request.newPassword());
        user.setTokenVersion(user.getTokenVersion() + 1);
        revoke(authorization);
    }

    @Transactional
    public void logout(String authorization) {
        revoke(authorization);
        revokedTokens.deleteByExpiresAtBefore(Instant.now());
    }

    private void revoke(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return;
        JwtService.Claims claims;
        try {
            claims = jwt.parse(authorization.substring(7).trim());
        } catch (JwtException | IllegalArgumentException ignored) {
            // An invalid token is already unusable.
            return;
        }
        // Database errors must propagate: never report a successful logout when
        // persisting the token revocation actually failed.
        if (claims.expiresAt().isAfter(Instant.now()) && !revokedTokens.existsById(claims.tokenId())) {
            revokedTokens.save(new RevokedToken(claims.tokenId(), claims.expiresAt()));
        }
    }

    private AuthResponse issue(UserAccount user) {
        JwtService.IssuedToken token = jwt.issue(user);
        return new AuthResponse(token.value(), "Bearer", token.expiresAt(), toView(user));
    }

    public static UserView toView(UserAccount user) {
        return new UserView(user.getId(), user.getPhone(), user.getEmail(), user.getNickname(), user.getFirstName(), user.getLastName(), user.getRole(),
                user.getGender(), user.isOfferAccepted(), user.getBirthDate(), user.getRegion(), user.getDistrict(),
                user.getAddress(), user.getAvatarUrl(), user.isActive(),
                user.getProfessionCategory() == null ? null : user.getProfessionCategory().getId(),
                user.getProfessionCategory() == null ? null : user.getProfessionCategory().getName());
    }

    public static String normalizeLoginIdentifier(String value) {
        if (value == null || value.isBlank()) throw ApiException.badRequest("INVALID_LOGIN_IDENTIFIER", "A phone number or email is required");
        String identifier = value.trim();
        if (identifier.indexOf('@') >= 0) {
            String email = identifier.toLowerCase(Locale.ROOT);
            if (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
                throw ApiException.badRequest("INVALID_EMAIL", "A valid email address is required");
            return email;
        }
        return normalizePhone(identifier);
    }

    public static String normalizeGmail(String value) {
        if (value == null || value.isBlank()) throw ApiException.badRequest("INVALID_EMAIL", "A Gmail address is required");
        String email = value.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches("^[^\\s@]+@gmail\\.com$"))
            throw ApiException.badRequest("GMAIL_REQUIRED", "Email must end with @gmail.com");
        return email;
    }

    public static String normalizePhone(String value) {
        if (value == null) throw ApiException.badRequest("INVALID_PHONE", "A valid phone number is required");
        String phone = value.trim().replaceAll("[\\s()\\-]", "");
        if (!phone.matches("\\+?[1-9][0-9]{6,14}")) {
            throw ApiException.badRequest("INVALID_PHONE", "Phone number must contain 7 to 15 digits and an optional leading +");
        }
        // One canonical spelling also makes the login failure counter effective
        // for requests alternating +998... and 998... . Legacy lookup remains.
        return phone.startsWith("+") ? phone : "+" + phone;
    }

    public static boolean phoneTaken(UserAccountRepository users, String phone, Long exceptUserId) {
        String alternate = phone.startsWith("+") ? phone.substring(1) : "+" + phone;
        if (exceptUserId == null) return users.existsByPhone(phone) || users.existsByPhone(alternate);
        return users.existsByPhoneAndIdNot(phone, exceptUserId) || users.existsByPhoneAndIdNot(alternate, exceptUserId);
    }

    public static void validateNewPassword(String password) {
        if (password == null || password.length() < 6)
            throw ApiException.badRequest("PASSWORD_TOO_SHORT", "Password must contain at least 6 characters");
        if (password.length() > 72)
            throw ApiException.badRequest("PASSWORD_TOO_LONG", "Password must contain at most 72 characters");
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static final class LoginGuard {
        private static final int MAX_FAILURES = 5;
        private static final long LOCK_MINUTES = 15;
        private static final int MAX_TRACKED_PHONES = 10000;
        private static final long RETAIN_MINUTES = 60;
        private final Map<String, FailureState> states = new LinkedHashMap<>(16, 0.75f, true);

        synchronized void check(String key) {
            prune(Instant.now());
            FailureState state = states.get(key);
            if (state == null) return;
            if (state.lockedUntil != null && state.lockedUntil.isAfter(Instant.now())) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_TEMPORARILY_LOCKED", "Try again later");
            }
            if (state.lockedUntil != null) states.remove(key);
        }
        synchronized void failed(String key) {
            Instant now = Instant.now();
            prune(now);
            FailureState state = states.computeIfAbsent(key, ignored -> new FailureState());
            state.failures++;
            state.lastFailureAt = now;
            if (state.failures >= MAX_FAILURES) state.lockedUntil = now.plus(LOCK_MINUTES, ChronoUnit.MINUTES);
        }
        synchronized void succeeded(String key) { states.remove(key); }
        private void prune(Instant now) {
            Instant staleBefore = now.minus(RETAIN_MINUTES, ChronoUnit.MINUTES);
            Iterator<Map.Entry<String, FailureState>> iterator = states.entrySet().iterator();
            while (iterator.hasNext()) {
                FailureState state = iterator.next().getValue();
                if (state.lastFailureAt.isBefore(staleBefore) && (state.lockedUntil == null || !state.lockedUntil.isAfter(now))) iterator.remove();
            }
            while (states.size() >= MAX_TRACKED_PHONES && !states.isEmpty()) states.remove(states.keySet().iterator().next());
        }
        private static final class FailureState { int failures; Instant lockedUntil; Instant lastFailureAt = Instant.now(); }
    }
}
