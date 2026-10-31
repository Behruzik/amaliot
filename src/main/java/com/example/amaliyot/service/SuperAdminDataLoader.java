package com.example.amaliyot.service;

import com.example.amaliyot.domain.Entities.UserAccount;
import com.example.amaliyot.domain.Role;
import com.example.amaliyot.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** Initial account provisioning uses Spring configuration, never ancestor .env files. */
@Component
@Profile("!demo & !test")
public class SuperAdminDataLoader implements ApplicationRunner {
    private final UserAccountRepository users;
    private final PasswordStorageService passwords;
    private final String email;
    private final String password;
    private final String configuredPhone;

    public SuperAdminDataLoader(UserAccountRepository users, PasswordStorageService passwords,
                               @Value("${SUPER_ADMIN_EMAIL:}") String email,
                               @Value("${SUPER_ADMIN_PASSWORD:}") String password,
                               @Value("${SUPER_ADMIN_PHONE:}") String phone) {
        this.users = users;
        this.passwords = passwords;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.password = password;
        this.configuredPhone = phone.trim();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Bootstrap credentials can be removed after the first admin is provisioned.
        // Never reset an existing account's password during application startup.
        if (users.existsByRole(Role.SUPER_ADMIN)) {
            if (!email.isBlank() && users.findByEmailIgnoreCase(email)
                    .filter(user -> user.getRole() == Role.SUPER_ADMIN).isEmpty()) {
                throw new IllegalStateException("SUPER_ADMIN_EMAIL does not match the existing super administrator");
            }
            return;
        }
        if (email.isBlank() || email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalStateException("Set a valid SUPER_ADMIN_EMAIL in .env or the environment, or use the demo profile");
        }
        if (password.isBlank() || password.length() < 6 || password.length() > 72) {
            throw new IllegalStateException("Set SUPER_ADMIN_PASSWORD (6-72 characters) for initial provisioning");
        }
        if (configuredPhone.isBlank()) {
            throw new IllegalStateException("Set SUPER_ADMIN_PHONE for initial provisioning");
        }
        String phone = AuthService.normalizePhone(configuredPhone);
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalStateException("SUPER_ADMIN_EMAIL is already assigned to another account");
        }
        if (AuthService.phoneTaken(users, phone, null)) {
            throw new IllegalStateException("SUPER_ADMIN_PHONE is already assigned to another account");
        }
        UserAccount admin = new UserAccount(phone, "", "Super Admin", "", true);
        passwords.setPassword(admin, password);
        admin.setEmail(email);
        admin.setRole(Role.SUPER_ADMIN);
        users.save(admin);
    }
}
