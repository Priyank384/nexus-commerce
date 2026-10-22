package com.example.nexusCommerce.configs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.Role;
import com.example.nexusCommerce.schema.User;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminBootstrap bootstrap;

    private void configure(String email, String password) {
        ReflectionTestUtils.setField(bootstrap, "email", email);
        ReflectionTestUtils.setField(bootstrap, "password", password);
        ReflectionTestUtils.setField(bootstrap, "name", "Platform Admin");
    }

    @Test
    void run_doesNothingWhenTheBootstrapIsNotConfigured() {
        configure("", "");

        bootstrap.run(null);

        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void run_createsTheAdminAccountWhenItIsMissing() {
        configure("Root-Admin@NexusCommerce.test", "dev-admin-pass");
        when(userRepository.findByEmail("root-admin@nexuscommerce.test")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("dev-admin-pass")).thenReturn("$2a$10$hash");

        bootstrap.run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(Role.ADMIN, saved.getValue().getRole());
        assertEquals("root-admin@nexuscommerce.test", saved.getValue().getEmail());
        assertEquals("$2a$10$hash", saved.getValue().getPasswordHash());
    }

    @Test
    void run_promotesAnExistingUserWithoutRewritingAWorkingPassword() {
        User existing = User.builder().name("Root").email("root-admin@nexuscommerce.test")
                .passwordHash("$2a$10$existing").role(Role.USER).enabled(true).build();
        configure("root-admin@nexuscommerce.test", "dev-admin-pass");
        when(userRepository.findByEmail("root-admin@nexuscommerce.test")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("dev-admin-pass", "$2a$10$existing")).thenReturn(true);

        bootstrap.run(null);

        assertEquals(Role.ADMIN, existing.getRole());
        verify(userRepository).save(existing);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void run_replacesThePasswordWhenTheStoredOneDoesNotMatch() {
        User existing = User.builder().name("Root").email("root-admin@nexuscommerce.test")
                .passwordHash("$2a$10$unknown").role(Role.ADMIN).enabled(true).build();
        configure("root-admin@nexuscommerce.test", "dev-admin-pass");
        when(userRepository.findByEmail("root-admin@nexuscommerce.test")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("dev-admin-pass", "$2a$10$unknown")).thenReturn(false);
        when(passwordEncoder.encode("dev-admin-pass")).thenReturn("$2a$10$fresh");

        bootstrap.run(null);

        assertEquals("$2a$10$fresh", existing.getPasswordHash());
        verify(userRepository).save(existing);
    }

    @Test
    void run_leavesTheDatabaseAloneWhenTheAccountIsAlreadyCorrect() {
        User existing = User.builder().name("Root").email("root-admin@nexuscommerce.test")
                .passwordHash("$2a$10$good").role(Role.ADMIN).enabled(true).build();
        configure("root-admin@nexuscommerce.test", "dev-admin-pass");
        when(userRepository.findByEmail("root-admin@nexuscommerce.test")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("dev-admin-pass", "$2a$10$good")).thenReturn(true);

        bootstrap.run(null);

        verify(userRepository, never()).save(any());
    }
}
