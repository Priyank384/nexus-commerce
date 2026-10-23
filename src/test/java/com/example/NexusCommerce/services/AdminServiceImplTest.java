package com.example.nexusCommerce.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import com.example.nexusCommerce.adapters.UserAdapter;
import com.example.nexusCommerce.dtos.GetUserResponseDto;
import com.example.nexusCommerce.repositories.CategoryRepository;
import com.example.nexusCommerce.repositories.OrderProductsRepository;
import com.example.nexusCommerce.repositories.OrderRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.repositories.ReviewRepository;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.User;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderProductsRepository orderProductsRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Spy
    private UserAdapter userAdapter = new UserAdapter();

    @InjectMocks
    private AdminServiceImpl adminService;

    private static User user(Long id, String name, String email) {
        User user = User.builder().name(name).email(email).build();
        user.setId(id);
        return user;
    }

    @Test
    void listUsers_withoutQueryPagesTheWholeTable() {
        var pageable = Pagination.of(0, 20);
        when(userRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(user(1L, "Ada", "ada@example.com")), pageable, 1));

        PageResponse<GetUserResponseDto> result = adminService.listUsers(0, 20, null);

        assertEquals(1, result.getContent().size());
        assertEquals("ada@example.com", result.getContent().get(0).getEmail());
        verify(userRepository, never())
                .findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(any(), any(), any());
    }

    @Test
    void listUsers_withQueryTrimsAndSearchesNameOrEmail() {
        var pageable = Pagination.of(1, 20);
        when(userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase("ada", "ada", pageable))
                .thenReturn(new PageImpl<>(List.of(user(1L, "Ada", "ada@example.com")), pageable, 21));

        PageResponse<GetUserResponseDto> result = adminService.listUsers(1, 20, "  ada  ");

        assertEquals(1, result.getContent().size());
        assertEquals(21, result.getTotalElements());
        verify(userRepository, never()).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void listUsers_withBlankQueryIsTheSameAsNoQuery() {
        var pageable = Pagination.of(1, 20);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<GetUserResponseDto> result = adminService.listUsers(1, 20, "   ");

        assertEquals(0, result.getContent().size());
        verify(userRepository, never())
                .findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(any(), any(), any());
    }
}
