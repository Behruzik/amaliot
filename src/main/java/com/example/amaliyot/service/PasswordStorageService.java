package com.example.amaliyot.service;

import com.example.amaliyot.domain.Entities.UserAccount;
import com.example.amaliyot.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Versioned password verifiers, with transparent upgrades of existing accounts. */
@Service
public class PasswordStorageService {
    private static final int SALT_LENGTH = 18;
    private static final int PBKDF2_ITERATIONS = 600_000;
    private static final int DERIVED_BITS = 256;
    private static final String PREFIX = "$pbkdf2-sha256$600000$";
    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

    private final PasswordEncoder legacyPasswordEncoder;
    private final SecureRandom random = new SecureRandom();

    public PasswordStorageService(UserAccountRepository users, PasswordEncoder legacyPasswordEncoder) {
        this.legacyPasswordEncoder = legacyPasswordEncoder;
    }

    public void setPassword(UserAccount user, String rawPassword) {
        if (rawPassword == null) throw new IllegalArgumentException("Password is required");
        String salt = randomText(SALT_LENGTH);
        user.setPasswordSalt(salt);
        user.setPasswordHash(PREFIX + derive(rawPassword, salt, DERIVED_BITS));
    }

    /** Verifies legacy BCrypt / short PBKDF2 hashes and upgrades on success. */
    public boolean matchesAndUpgrade(String rawPassword, UserAccount user) {
        if (rawPassword == null || user.getPasswordHash() == null) return false;
        String salt = user.getPasswordSalt();
        String storedHash = user.getPasswordHash();
        if (salt == null || salt.isBlank()) {
            // BCrypt only consumes the first 72 UTF-8 bytes; preserve the old
            // login guard so a longer input cannot authenticate by truncation.
            if (rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) return false;
            if (!legacyPasswordEncoder.matches(rawPassword, storedHash)) return false;
            setPassword(user, rawPassword);
            return true;
        }
        if (salt.length() != SALT_LENGTH) return false;
        if (storedHash.startsWith(PREFIX)) {
            return constantTimeEquals(storedHash, PREFIX + derive(rawPassword, salt, DERIVED_BITS));
        }
        if (storedHash.length() != 18) return false;
        // Original project: 108 requested bits (13 complete bytes in the JDK).
        if (!constantTimeEquals(storedHash, derive(rawPassword, salt, 108))) return false;
        setPassword(user, rawPassword);
        return true;
    }

    private static boolean constantTimeEquals(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.US_ASCII), second.getBytes(StandardCharsets.US_ASCII));
    }

    private String randomText(int length) {
        char[] result = new char[length];
        for (int i = 0; i < length; i++) result[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        return new String(result);
    }

    private static String derive(String password, String salt, int bits) {
        char[] passwordChars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(passwordChars, salt.getBytes(StandardCharsets.US_ASCII),
                PBKDF2_ITERATIONS, bits);
        byte[] derived = null;
        try {
            derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(derived);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable", exception);
        } finally {
            spec.clearPassword();
            java.util.Arrays.fill(passwordChars, '\0');
            if (derived != null) java.util.Arrays.fill(derived, (byte) 0);
        }
    }
}
