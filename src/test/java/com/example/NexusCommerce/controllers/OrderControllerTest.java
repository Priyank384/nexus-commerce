package com.example.nexusCommerce.controllers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.UpdateOrderRequestDto;
import com.example.nexusCommerce.exceptions.ForbiddenException;
import com.example.nexusCommerce.exceptions.GlobalExceptionHandler;
import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.Role;
import com.example.nexusCommerce.security.AuthenticatedUser;
import com.example.nexusCommerce.security.IdempotencyService;
import com.example.nexusCommerce.services.OrderService;
import com.example.nexusCommerce.utils.ApiResponse;

/**
 * Ownership denial and the delete envelope are part of the HTTP contract, so they are pinned
 * here rather than only in the service tests. The caller is passed explicitly instead of via
 * MockMvc: a @WebMvcTest slice does not resolve @AuthenticationPrincipal, and a path variable
 * silently bound onto the "principal" made every guard look satisfied for the wrong reason.
 */
@ExtendWith(MockitoExtension.class)
public class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private IdempotencyService idempotency;

    @InjectMocks
    private OrderController controller;

    private static AuthenticatedUser user(Long id) {
        return new AuthenticatedUser(id, id + "@example.test", Role.USER);
    }

    private static AuthenticatedUser admin() {
        return new AuthenticatedUser(1L, "admin@example.test", Role.ADMIN);
    }

    private static GetOrderResponseDto orderOwnedBy(Long userId) {
        return GetOrderResponseDto.builder()
                .id(50L).userId(userId).orderStatus(OrderStatus.PENDING)
                .totalPrice(BigDecimal.valueOf(499)).build();
    }

    @Test
    void getOrderById_forAnotherUsersOrder_isForbidden() {
        when(orderService.getOrderById(50L)).thenReturn(orderOwnedBy(8L));

        assertThrows(ForbiddenException.class, () -> controller.getOrderById(50L, user(7L)));
    }

    @Test
    void getOrderById_forOwnOrder_returns200() {
        when(orderService.getOrderById(50L)).thenReturn(orderOwnedBy(7L));

        ResponseEntity<ApiResponse<GetOrderResponseDto>> response = controller.getOrderById(50L, user(7L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(50L, response.getBody().getData().getId());
    }

    @Test
    void getOrderById_asAdmin_returns200() {
        when(orderService.getOrderById(50L)).thenReturn(orderOwnedBy(8L));

        assertEquals(HttpStatus.OK, controller.getOrderById(50L, admin()).getStatusCode());
    }

    @Test
    void getOrdersByUser_forAnotherUser_isForbiddenWithoutQuerying() {
        assertThrows(ForbiddenException.class, () -> controller.getOrdersByUser(8L, 0, 20, user(7L)));

        verify(orderService, never()).getOrdersByUserId(anyLong(), anyInt(), anyInt());
    }

    @Test
    void getOrdersByUser_forSelfOrAsAdmin_isAllowed() {
        when(orderService.getOrdersByUserId(anyLong(), anyInt(), anyInt())).thenReturn(null);

        assertEquals(HttpStatus.OK, controller.getOrdersByUser(7L, 0, 20, user(7L)).getStatusCode());
        assertEquals(HttpStatus.OK, controller.getOrdersByUser(8L, 0, 20, admin()).getStatusCode());
    }

    @Test
    void updateOrder_forAnotherUsersOrder_isForbidden() {
        when(orderService.getOrderById(50L)).thenReturn(orderOwnedBy(8L));
        UpdateOrderRequestDto request = UpdateOrderRequestDto.builder().status(OrderStatus.SHIPPED).build();

        assertThrows(ForbiddenException.class, () -> controller.updateOrder(50L, request, user(7L)));
        verify(orderService, never()).updateOrder(anyLong(), any());
    }

    @Test
    void deleteOrder_returns200WithEnvelope() {
        ResponseEntity<ApiResponse<Void>> response = controller.deleteOrder(50L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Order Deleted Successfully", response.getBody().getMessage());
        verify(orderService).deleteOrder(50L);
    }

    /**
     * The advice is what turns a guard into a 403 with the same body shape as the security
     * filter's role denial, so the mapping itself has to stay pinned.
     */
    @Test
    void forbiddenException_mapsTo403WithForbiddenEnvelope() {
        ResponseEntity<ApiResponse<Void>> response =
                new GlobalExceptionHandler().handleForbidden(new ForbiddenException("You can only access your own orders"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Forbidden!", response.getBody().getMessage());
        assertEquals("You can only access your own orders", response.getBody().getError());
        assertEquals(false, response.getBody().isSuccess());
    }
}
