package com.example.nexusCommerce.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.CreateUserRequestDto;
import com.example.nexusCommerce.dtos.GetUserResponseDto;
import com.example.nexusCommerce.security.AuthenticatedUser;
import com.example.nexusCommerce.services.UserService;
import com.example.nexusCommerce.utils.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Users", description = "User account operations")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Register a new account",
               description = "Public signup. Stores the password BCrypt-hashed and grants the USER role.")
    @PostMapping
    public ResponseEntity<ApiResponse<GetUserResponseDto>> createUser(@Valid @RequestBody CreateUserRequestDto requestDto) {
        GetUserResponseDto user = userService.createUser(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(user, "User Created Successfully!"));
    }

    @Operation(summary = "Get my profile",
               description = "Name, email, phone and role for the authenticated caller. The account page "
                           + "reads this because the JWT only carries id, email and role.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<GetUserResponseDto>> getMyProfile(
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.getProfile(caller.id()), "Profile fetched"));
    }

}
