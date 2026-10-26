package com.example.nexusCommerce.controllers;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.AdminMetricsResponseDto;
import com.example.nexusCommerce.dtos.GetProductImageResponseDto;
import com.example.nexusCommerce.dtos.GetProductResponseDto;
import com.example.nexusCommerce.dtos.GetProductWithDetailsResponseDto;
import com.example.nexusCommerce.dtos.GetUserResponseDto;
import com.example.nexusCommerce.dtos.ReorderProductImagesRequestDto;
import com.example.nexusCommerce.dtos.SetStockRequestDto;
import com.example.nexusCommerce.dtos.UpdateUserRequestDto;
import com.example.nexusCommerce.services.AdminService;
import com.example.nexusCommerce.services.ProductImageUploadService;
import com.example.nexusCommerce.services.ProductService;
import com.example.nexusCommerce.utils.ApiResponse;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin", description = "Administrative operations - dashboard metrics, catalogue, user management, and stock/gallery control (ROLE_ADMIN)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final ProductService productService;
    private final ProductImageUploadService imageUploadService;

    @Operation(summary = "Dashboard metrics",
               description = "Aggregate counts, revenue, order-status distribution and out-of-stock catalogue. Requires ROLE_ADMIN.")
    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<AdminMetricsResponseDto>> metrics() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getMetrics(), "Metrics fetched successfully"));
    }

    @Operation(summary = "List products (admin)",
               description = "Paged catalogue for the admin table, newest first. Unlike the public listing "
                             + "this includes unpublished drafts, because a draft is only reachable from the "
                             + "screen that publishes it. Optional q matches title, brand, material or category; "
                             + "outOfStock=true keeps only pieces whose aggregate stock is zero. Requires ROLE_ADMIN.")
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PageResponse<GetProductResponseDto>>> listProducts(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "outOfStock", defaultValue = "false") boolean outOfStock) {
        PageResponse<GetProductResponseDto> products = adminService.listProducts(page, size, q, outOfStock);
        return ResponseEntity.ok(ApiResponse.success(products, "Products fetched successfully"));
    }

    @Operation(summary = "Get a product (admin)",
               description = "Full product detail including variants and gallery, drafts included. The "
                             + "public detail reads report an unpublished draft as 404, so this is the "
                             + "editor's read. Requires ROLE_ADMIN.")
    @GetMapping("/products/{id}")
    public ResponseEntity<ApiResponse<GetProductWithDetailsResponseDto>> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                productService.getProductWithDetailsForAdmin(id), "Product fetched successfully"));
    }

    @Operation(summary = "List users",
               description = "Paged list of all user accounts, newest first. Optional q matches name or email. Requires ROLE_ADMIN.")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<GetUserResponseDto>>> listUsers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "q", required = false) String q) {
        PageResponse<GetUserResponseDto> users = adminService.listUsers(page, Pagination.clampSize(size), q);
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
            @PathVariable Long id, @Valid @RequestBody UpdateUserRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success(adminService.updateUser(id, requestDto), "User updated successfully"));
    }

    @Operation(summary = "Set product stock",
               description = "Overwrite a product's absolute stock level. Requires ROLE_ADMIN. "
                             + "Only valid for a style with no variants or exactly one: a product with a "
                             + "size+colour grid answers 400 here, because the variants are the source of "
                             + "truth and an aggregate-only write is undone by the next resync. Restock those "
                             + "per variant via PUT /api/v1/products/variants/{variantId}.")
    @PutMapping("/products/{id}/stock")
    public ResponseEntity<ApiResponse<GetProductResponseDto>> setStock(
            @PathVariable Long id, @Valid @RequestBody SetStockRequestDto requestDto) {
        GetProductResponseDto product = productService.setStock(id, requestDto.getStock());
        return ResponseEntity.ok(ApiResponse.success(product, "Stock updated successfully"));
    }

    @Operation(summary = "Upload a product image",
               description = "Accepts a PNG/JPEG/WebP file up to 5 MB, stores it locally under "
                             + "${app.upload.dir} and rewrites the product's image URL. Requires ROLE_ADMIN.")
    @PostMapping(value = "/products/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> uploadImage(
            @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        String url = imageUploadService.upload(id, file);
        return ResponseEntity.ok(ApiResponse.success(url, "Image uploaded"));
    }

    @Operation(summary = "Append a gallery image",
               description = "Stores a PNG/JPEG/WebP up to 5 MB and appends it to the product's gallery "
                             + "after the current last position. Requires ROLE_ADMIN.")
    @PostMapping(value = "/products/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<GetProductImageResponseDto>> appendGalleryImage(
            @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                imageUploadService.appendGalleryImage(id, file), "Image added to gallery"));
    }

    @Operation(summary = "Delete a gallery image",
               description = "Soft-deletes the row, closes the position gap and returns the remaining "
                             + "gallery in order. The file stays on disk so order snapshots keep resolving. Requires ROLE_ADMIN.")
    @DeleteMapping("/products/{id}/images/{imageId}")
    public ResponseEntity<ApiResponse<List<GetProductImageResponseDto>>> deleteGalleryImage(
            @PathVariable Long id, @PathVariable Long imageId) {
        return ResponseEntity.ok(ApiResponse.success(
                imageUploadService.deleteGalleryImage(id, imageId), "Image removed from gallery"));
    }

    @Operation(summary = "Reorder the gallery",
               description = "Body lists every live image id exactly once, in the new display order. "
                             + "Position 0 becomes the thumbnail. Requires ROLE_ADMIN.")
    @PutMapping("/products/{id}/images/order")
    public ResponseEntity<ApiResponse<List<GetProductImageResponseDto>>> reorderGallery(
            @PathVariable Long id, @Valid @RequestBody ReorderProductImagesRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success(
                imageUploadService.reorderGallery(id, requestDto.getImageIds()), "Gallery reordered"));
    }
}
