package com.example.nexusCommerce.services;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nexusCommerce.adapters.UserAdapter;
import com.example.nexusCommerce.dtos.AdminMetricsResponseDto;
import com.example.nexusCommerce.dtos.GetUserResponseDto;
import com.example.nexusCommerce.dtos.UpdateUserRequestDto;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.CategoryRepository;
import com.example.nexusCommerce.repositories.OrderProductsRepository;
import com.example.nexusCommerce.repositories.OrderRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.repositories.ReviewRepository;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.Role;
import com.example.nexusCommerce.schema.User;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final OrderProductsRepository orderProductsRepository;
    private final ReviewRepository reviewRepository;
    private final UserAdapter userAdapter;

    @Transactional(readOnly = true)
    @Override
    public AdminMetricsResponseDto getMetrics() {
        Map<OrderStatus, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : orderRepository.countGroupedByStatus()) {
            byStatus.put((OrderStatus) row[0], ((Number) row[1]).longValue());
        }

        BigDecimal revenue = orderProductsRepository.totalRevenue();

        return AdminMetricsResponseDto.builder()
                .totalUsers(userRepository.count())
                .totalProducts(productRepository.count())
                .totalCategories(categoryRepository.count())
                .totalOrders(orderRepository.count())
                .totalReviews(reviewRepository.count())
                .totalRevenue(revenue == null ? BigDecimal.ZERO : revenue)
                .ordersByStatus(byStatus)
                .outOfStockProducts(productRepository.countByStockLessThanEqual(0))
                .build();
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<GetUserResponseDto> listUsers(int page, int size, String q) {
        var pageable = Pagination.of(page, size);
        var users = q == null || q.isBlank()
                ? userRepository.findAll(pageable)
                : userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        q.trim(), q.trim(), pageable);
        return PageResponse.of(users, userAdapter::mapToGetUserResponseDto);
    }

    @Transactional(readOnly = true)
    @Override
    public GetUserResponseDto getUser(Long id) {
        return userAdapter.mapToGetUserResponseDto(loadUser(id));
    }

    @Transactional
    @Override
    public GetUserResponseDto updateUser(Long id, UpdateUserRequestDto requestDto) {
        User user = loadUser(id);

        if (requestDto.getEnabled() != null) {
            // Disabling the last active admin would lock everyone out of the admin API with
            // no way back in, so block it just like the demotion path below.
            if (!requestDto.getEnabled() && user.getRole() == Role.ADMIN && user.isEnabled()
                    && userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
                throw new InvalidRequestException("Cannot disable the last remaining active admin");
            }
            user.setEnabled(requestDto.getEnabled());
        }
        if (requestDto.getRole() != null) {
            // Never let the last administrator be demoted, or the app locks itself out of
            // every admin endpoint.
            if (user.getRole() == Role.ADMIN && requestDto.getRole() != Role.ADMIN
                    && userRepository.countByRole(Role.ADMIN) <= 1) {
                throw new InvalidRequestException("Cannot demote the last remaining admin");
            }
            user.setRole(requestDto.getRole());
        }
        return userAdapter.mapToGetUserResponseDto(userRepository.save(user));
    }

    private User loadUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }
}
