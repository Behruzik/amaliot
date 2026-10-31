package com.example.amaliyot.security;

import com.example.amaliyot.domain.Entities.UserAccount;
import com.example.amaliyot.repository.UserAccountRepository;
import com.example.amaliyot.service.AuthService;
import com.example.amaliyot.service.PasswordStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SecurityComponentsTests {
    private final BCryptPasswordEncoder legacy = new BCryptPasswordEncoder(4);
    private final PasswordStorageService passwords = new PasswordStorageService(mock(UserAccountRepository.class), legacy);

    @Test
    void newPasswordsAreSaltedAndSupportUnicodeWithoutRandomRejection() {
        UserAccount first = new UserAccount();
        UserAccount second = new UserAccount();
        String raw = "ü".repeat(40);
        passwords.setPassword(first, raw);
        passwords.setPassword(second, raw);
        assertTrue(first.getPasswordHash().matches("\\$pbkdf2-sha256\\$600000\\$[A-Za-z0-9_-]{43}"));
        assertNotEquals(first.getPasswordHash(), second.getPasswordHash());
        assertNotEquals(first.getPasswordSalt(), second.getPasswordSalt());
        assertTrue(passwords.matchesAndUpgrade(raw, first));
        String before = first.getPasswordHash();
        assertFalse(passwords.matchesAndUpgrade(raw + "wrong", first));
        assertEquals(before, first.getPasswordHash());
    }

    @Test
    void oldShortPbkdf2PasswordsRemainUsableAndUpgradeAfterSuccessfulLogin() throws Exception {
        String salt = "LegacySaltValue123";
        String raw = "ExistingPassword123";
        byte[] oldVerifier = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec(raw.toCharArray(), salt.getBytes(StandardCharsets.US_ASCII), 600_000, 108)).getEncoded();
        UserAccount user = new UserAccount();
        user.setPasswordSalt(salt);
        user.setPasswordHash(Base64.getUrlEncoder().withoutPadding().encodeToString(oldVerifier));
        assertEquals(18, user.getPasswordHash().length());
        assertFalse(passwords.matchesAndUpgrade("incorrect", user));
        assertEquals(18, user.getPasswordHash().length());
        assertTrue(passwords.matchesAndUpgrade(raw, user));
        assertTrue(user.getPasswordHash().startsWith("$pbkdf2-sha256$600000$"));
        assertTrue(passwords.matchesAndUpgrade(raw, user));
    }

    @Test
    void legacyBcryptUpgradeNeverAuthenticatesByTruncatingLongInput() {
        UserAccount user = new UserAccount();
        String raw = "a".repeat(72);
        user.setPasswordHash(legacy.encode(raw));
        assertFalse(passwords.matchesAndUpgrade(raw + "suffix", user));
        assertNull(user.getPasswordSalt());
        assertTrue(passwords.matchesAndUpgrade(raw, user));
        assertTrue(user.getPasswordHash().startsWith("$pbkdf2-sha256$600000$"));
    }

    @Test
    void equivalentPhoneSpellingsShareTheSameLoginIdentifier() {
        assertEquals("+998901234567", AuthService.normalizeLoginIdentifier("998 90 123-45-67"));
        assertEquals("+998901234567", AuthService.normalizeLoginIdentifier("+998 (90) 123-45-67"));
    }

    @Test
    void productionJwtModeRefusesMissingKeyAndAcceptsConfiguredKey() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("", 60, true));
        assertDoesNotThrow(() -> new JwtService(Base64.getEncoder().encodeToString(new byte[32]), 60, true));
        assertDoesNotThrow(() -> new JwtService("", 60, false));
    }

    @Test
    void clientLimitReturnsJsonAndRetryAfterWithoutTrustingForwardedHeaders() throws Exception {
        MutableClock clock = new MutableClock();
        AuthRateLimitFilter filter = new AuthRateLimitFilter(2, 60, 10, clock);
        AtomicInteger allowed = new AtomicInteger();
        for (int attempt = 0; attempt < 3; attempt++) {
            MockHttpServletRequest request = authRequest("203.0.113.1");
            request.addHeader("X-Forwarded-For", "198.51.100." + attempt);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> allowed.incrementAndGet());
            if (attempt == 2) {
                assertEquals(429, response.getStatus());
                assertEquals("60", response.getHeader("Retry-After"));
                assertEquals("no-store", response.getHeader("Cache-Control"));
                assertTrue(response.getContentAsString().contains("AUTH_RATE_LIMITED"));
                assertTrue(response.getContentType().startsWith("application/json"));
            }
        }
        assertEquals(2, allowed.get());
        clock.advanceSeconds(60);
        filter.doFilter(authRequest("203.0.113.1"), new MockHttpServletResponse(), (req, res) -> allowed.incrementAndGet());
        assertEquals(3, allowed.get());
    }

    @Test
    void fullClientTableCannotEvictAnExistingLimitAndExpiredEntriesAreReclaimed() throws Exception {
        MutableClock clock = new MutableClock();
        AuthRateLimitFilter filter = new AuthRateLimitFilter(1, 60, 1, clock);
        filter.doFilter(authRequest("203.0.113.1"), new MockHttpServletResponse(), (req, res) -> {});
        MockHttpServletResponse overflow = new MockHttpServletResponse();
        filter.doFilter(authRequest("203.0.113.2"), overflow, (req, res) -> fail("Table must stay bounded"));
        assertEquals(429, overflow.getStatus());
        MockHttpServletResponse original = new MockHttpServletResponse();
        filter.doFilter(authRequest("203.0.113.1"), original, (req, res) -> fail("Original limit must remain"));
        assertEquals(429, original.getStatus());
        clock.advanceSeconds(60);
        AtomicInteger accepted = new AtomicInteger();
        filter.doFilter(authRequest("203.0.113.2"), new MockHttpServletResponse(), (req, res) -> accepted.incrementAndGet());
        assertEquals(1, accepted.get());
    }

    private static MockHttpServletRequest authRequest(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(address);
        return request;
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advanceSeconds(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
