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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin", description = "Administrative operations - dashboard metrics, user management, and stock control (ROLE_ADMIN)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final ProductService productService;

    @Operation(summary = "Dashboard metrics",
               description = "Aggregate counts, revenue, order-status distribution and out-of-stock catalogue. Requires ROLE_ADMIN.")
    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<AdminMetricsResponseDto>> metrics() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getMetrics(), "Metrics fetched successfully"));
    }

    @Operation(summary = "List users",
               description = "Paged list of all user accounts. Requires ROLE_ADMIN.")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<GetUserResponseDto>>> listUsers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        PageResponse<GetUserResponseDto> users = adminService.listUsers(page, Pagination.clampSize(size));
        return ResponseEntity.ok(ApiResponse.success(users, "Users fetched successfully"));
    }

    @Operation(summary = "Get a user",
               description = "Single user lookup by id. Requires ROLE_ADMIN.")
    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<GetUserResponseDto>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getUser(id), "User fetched successfully"));
    }

    @Operation(summary = "Update a user's role or enabled flag",
               description = "Partial merge; either or both fields may be sent. The last active admin cannot be disabled. Requires ROLE_ADMIN.")
    @PatchMapping("/users/{id}")
    public ResponseEntity<ApiResponse<GetUserResponseDto>> updateUser(
            @PathVariable Long id, @RequestBody UpdateUserRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success(adminService.updateUser(id, requestDto), "User updated successfully"));
    }

    @Operation(summary = "Set product stock",
               description = "Overwrite a product's absolute stock level. Requires ROLE_ADMIN.")
    @PutMapping("/products/{id}/stock")
    public ResponseEntity<ApiResponse<GetProductResponseDto>> setStock(
            @PathVariable Long id, @Valid @RequestBody SetStockRequestDto requestDto) {
        GetProductResponseDto product = productService.setStock(id, requestDto.getStock());
        return ResponseEntity.ok(ApiResponse.success(product, "Stock updated successfully"));
    }
}
