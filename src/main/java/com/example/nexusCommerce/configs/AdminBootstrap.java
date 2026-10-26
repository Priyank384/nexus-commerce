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
 * Creates one ADMIN account at startup so the admin surface can be exercised without editing
 * rows by hand. Inert unless both {@code app.admin.bootstrap.email} and
 * {@code app.admin.bootstrap.password} are set, which is exactly how V14's promote-by-email seed
 * failed: the migration runs before the account exists, so on a fresh database it is a no-op and
 * nobody can log in as ADMIN.
 *
 * <p>Create-only: an existing account is never promoted, re-enabled or have its password reset.
 * Leaving the bootstrap credentials in a deployment must therefore not become a way to take over
 * an account once the real password has been changed.
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
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);

        if (userRepository.findByEmail(normalized).isPresent()) {
            log.info("ADMIN bootstrap account {} already exists; leaving it untouched. "
                    + "Remove ADMIN_BOOTSTRAP_* once the account is in use.", normalized);
            return;
        }

        userRepository.save(User.builder()
                .name(name)
                .email(normalized)
                .passwordHash(passwordEncoder.encode(password))
                .role(Role.ADMIN)
                .enabled(true)
                .build());
        log.info("Bootstrapped ADMIN account {}", normalized);
    }
}
