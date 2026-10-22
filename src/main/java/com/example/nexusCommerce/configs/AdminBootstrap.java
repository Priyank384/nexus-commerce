package com.example.nexusCommerce.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.Role;
import com.example.nexusCommerce.schema.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates (or promotes) one ADMIN account at startup so the admin surface can be exercised
 * without editing rows by hand. Inert unless both {@code app.admin.bootstrap.email} and
 * {@code app.admin.bootstrap.password} are set, which is exactly how V14's promote-by-email seed
 * failed: the migration runs before the account exists, so on a fresh database it is a no-op and
 * nobody can log in as ADMIN.
 *
 * <p>Never set these properties in a real deployment — the configured account is forced to ADMIN
 * with that password on every boot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.bootstrap.email:}")
    private String email;

    @Value("${app.admin.bootstrap.password:}")
    private String password;

    @Value("${app.admin.bootstrap.name:Platform Admin}")
    private String name;

    @Transactional
    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        String normalized = email.trim().toLowerCase();

        User admin = userRepository.findByEmail(normalized).orElse(null);
        if (admin == null) {
            userRepository.save(User.builder()
                    .name(name)
                    .email(normalized)
                    .passwordHash(passwordEncoder.encode(password))
                    .role(Role.ADMIN)
                    .enabled(true)
                    .build());
            log.info("Bootstrapped ADMIN account {}", normalized);
            return;
        }

        // The account usually already exists (V14 promoted it by email), and its password is
        // whatever whoever registered it chose. Re-hash only when it does not already match, so a
        // restart neither writes a new bcrypt salt every time nor silently keeps an old password.
        boolean changed = false;
        if (admin.getRole() != Role.ADMIN) {
            admin.setRole(Role.ADMIN);
            changed = true;
        }
        if (!admin.isEnabled()) {
            admin.setEnabled(true);
            changed = true;
        }
        if (admin.getPasswordHash() == null || !passwordEncoder.matches(password, admin.getPasswordHash())) {
            admin.setPasswordHash(passwordEncoder.encode(password));
            changed = true;
        }
        if (changed) {
            userRepository.save(admin);
            log.info("Promoted existing account {} to ADMIN", normalized);
        }
    }
}
