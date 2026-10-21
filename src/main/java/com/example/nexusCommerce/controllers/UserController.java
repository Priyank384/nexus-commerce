package com.example.nexusCommerce.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.CreateUserRequestDto;
import com.example.nexusCommerce.dtos.GetUserResponseDto;
import com.example.nexusCommerce.services.UserService;
import com.example.nexusCommerce.utils.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Users", description = "User account operations")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<GetUserResponseDto>> createUser(@Valid @RequestBody CreateUserRequestDto requestDto) {
        GetUserResponseDto user = userService.createUser(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(user, "User Created Successfully!"));
    }

}
