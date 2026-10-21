package com.example.nexusCommerce.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.nexusCommerce.adapters.UserAdapter;
import com.example.nexusCommerce.dtos.CreateUserRequestDto;
import com.example.nexusCommerce.dtos.GetUserResponseDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.User;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserAdapter userAdapter;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void createUser_hashesPasswordAndReturnsDto() {
        CreateUserRequestDto dto = CreateUserRequestDto.builder()
                .name("Test User")
                .email("test@example.com")
                .password("secret123")
                .build();
        User savedUser = User.builder()
                .name("Test User")
                .email("test@example.com")
                .passwordHash("$2a$10$hashed")
                .build();
        savedUser.setId(1L);
        GetUserResponseDto response = GetUserResponseDto.builder()
                .id(1L)
                .email("test@example.com")
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userAdapter.mapToGetUserResponseDto(savedUser)).thenReturn(response);

        GetUserResponseDto result = userService.createUser(dto);

        assertEquals(1L, result.getId());
    }

    @Test
    void createUser_rejectsDuplicateEmail() {
        CreateUserRequestDto dto = CreateUserRequestDto.builder()
                .name("Test User")
                .email("test@example.com")
                .password("secret123")
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> userService.createUser(dto));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_storesEmailLowercased() {
        CreateUserRequestDto dto = CreateUserRequestDto.builder()
                .name("Test User")
                .email("  Test@Example.COM ")
                .password("secret123")
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        userService.createUser(dto);

        verify(userRepository).save(captor.capture());
        assertEquals("test@example.com", captor.getValue().getEmail());
    }
}
