package com.example.nexusCommerce.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.example.nexusCommerce.adapters.ProductAdapter;
import com.example.nexusCommerce.adapters.UserAdapter;
import com.example.nexusCommerce.dtos.GetProductResponseDto;
import com.example.nexusCommerce.dtos.GetUserResponseDto;
import com.example.nexusCommerce.repositories.CategoryRepository;
import com.example.nexusCommerce.repositories.OrderProductsRepository;
import com.example.nexusCommerce.repositories.OrderRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.repositories.ReviewRepository;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.schema.Product;
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
    @Spy
    private ProductAdapter productAdapter = new ProductAdapter();

    @InjectMocks
    private AdminServiceImpl adminService;

    private static User user(Long id, String name, String email) {
        User user = User.builder().name(name).email(email).build();
        user.setId(id);
        return user;
    }

    private static Product product(Long id, String title, boolean active) {
        Category category = Category.builder().name("Knitwear").build();
        category.setId(7L);
        Product product = Product.builder()
                .title(title)
                .price(BigDecimal.valueOf(1200))
                .category(category)
                .isActive(active)
                .build();
        product.setId(id);
        return product;
    }

    @Test
    void listProducts_includesDraftsAndSortsNewestFirst() {
        Pageable pageable = Pagination.of(0, 20).withSort(Sort.by(Sort.Direction.DESC, "id"));
        when(productRepository.searchForAdmin(isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(product(2L, "Draft Tee", false)), pageable, 1));

        PageResponse<GetProductResponseDto> result = adminService.listProducts(0, 20, null, false);

        assertEquals(1, result.getContent().size());
        assertFalse(result.getContent().get(0).getIsActive());
        assertEquals("Knitwear", result.getContent().get(0).getCategory());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).searchForAdmin(isNull(), isNull(), captor.capture());
        assertEquals(Sort.Direction.DESC,
                captor.getValue().getSort().getOrderFor("id").getDirection());
        verify(productRepository, never()).findByIsActiveTrue(any());
    }

    @Test
    void listProducts_sanitisesTheKeywordIntoAPattern() {
        Pageable pageable = Pagination.of(1, 20).withSort(Sort.by(Sort.Direction.DESC, "id"));
        when(productRepository.searchForAdmin(eq("%linen shirt%"), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(product(3L, "Linen Shirt", true)), pageable, 21));

        PageResponse<GetProductResponseDto> result = adminService.listProducts(1, 20, "  Linen_Shirt%  ", false);

        assertEquals(1, result.getContent().size());
        assertEquals(21, result.getTotalElements());
        verify(productRepository, never()).searchForAdmin(isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void listProducts_blankQueryIsNoFilter() {
        Pageable pageable = Pagination.of(0, 20).withSort(Sort.by(Sort.Direction.DESC, "id"));
        when(productRepository.searchForAdmin(isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<GetProductResponseDto> result = adminService.listProducts(0, 20, "   ", false);

        assertEquals(0, result.getContent().size());
    }

    @Test
    void listProducts_outOfStockFilterAsksTheRepositoryForAZeroCeiling() {
        Pageable pageable = Pagination.of(0, 20).withSort(Sort.by(Sort.Direction.DESC, "id"));
        when(productRepository.searchForAdmin(isNull(), eq(0), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        adminService.listProducts(0, 20, null, true);

        // Same predicate as the dashboard metric (stock <= 0), so the two cannot disagree.
        verify(productRepository).searchForAdmin(isNull(), eq(0), eq(pageable));
    }

    @Test
    void listUsers_withoutQueryPagesTheWholeTable() {
        var pageable = newestFirst(0, 20);
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
        var pageable = newestFirst(1, 20);
        when(userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase("ada", "ada", pageable))
                .thenReturn(new PageImpl<>(List.of(user(1L, "Ada", "ada@example.com")), pageable, 21));

        PageResponse<GetUserResponseDto> result = adminService.listUsers(1, 20, "  ada  ");

        assertEquals(1, result.getContent().size());
        assertEquals(21, result.getTotalElements());
        verify(userRepository, never()).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void listUsers_withBlankQueryIsTheSameAsNoQuery() {
        var pageable = newestFirst(1, 20);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<GetUserResponseDto> result = adminService.listUsers(1, 20, "   ");

        assertEquals(0, result.getContent().size());
        verify(userRepository, never())
                .findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(any(), any(), any());
    }

    @Test
    void listUsers_asksForNewestFirst() {
        var pageable = newestFirst(0, 20);
        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        adminService.listUsers(0, 20, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(captor.capture());
        assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("id").getDirection());
    }

    private static Pageable newestFirst(int page, int size) {
        return Pagination.of(page, size).withSort(Sort.by(Sort.Direction.DESC, "id"));
    }
}
