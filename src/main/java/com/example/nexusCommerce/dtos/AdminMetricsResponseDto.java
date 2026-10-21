package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;
import java.util.Map;

import com.example.nexusCommerce.schema.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminMetricsResponseDto {
    private long totalUsers;
    private long totalProducts;
    private long totalCategories;
    private long totalOrders;
    private long totalReviews;
    private BigDecimal totalRevenue;
    private Map<OrderStatus, Long> ordersByStatus;
    private long outOfStockProducts;
}
