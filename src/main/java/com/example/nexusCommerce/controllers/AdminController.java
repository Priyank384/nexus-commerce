package com.example.nexusCommerce.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.AdminMetricsResponseDto;
import com.example.nexusCommerce.dtos.GetProductResponseDto;
import com.example.nexusCommerce.dtos.GetUserResponseDto;
import com.example.nexusCommerce.dtos.SetStockRequestDto;
import com.example.nexusCommerce.dtos.UpdateUserRequestDto;
import com.example.nexusCommerce.services.AdminService;
import com.example.nexusCommerce.services.ProductService;
import com.example.nexusCommerce.utils.ApiResponse;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin", description = "Administrative operations - dashboard metrics, user management, and stock control (ROLE_ADMIN)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final ProductService productService;

    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<AdminMetricsResponseDto>> metrics() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getMetrics(), "Metrics fetched successfully"));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<GetUserResponseDto>>> listUsers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        PageResponse<GetUserResponseDto> users = adminService.listUsers(page, Pagination.clampSize(size));
        return ResponseEntity.ok(ApiResponse.success(users, "Users fetched successfully"));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<GetUserResponseDto>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getUser(id), "User fetched successfully"));
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity<ApiResponse<GetUserResponseDto>> updateUser(
            @PathVariable Long id, @RequestBody UpdateUserRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success(adminService.updateUser(id, requestDto), "User updated successfully"));
    }

    @PutMapping("/products/{id}/stock")
    public ResponseEntity<ApiResponse<GetProductResponseDto>> setStock(
            @PathVariable Long id, @Valid @RequestBody SetStockRequestDto requestDto) {
        GetProductResponseDto product = productService.setStock(id, requestDto.getStock());
        return ResponseEntity.ok(ApiResponse.success(product, "Stock updated successfully"));
    }
}
