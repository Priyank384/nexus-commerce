package com.example.nexusCommerce.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.example.nexusCommerce.adapters.OrderAdapter;
import com.example.nexusCommerce.dtos.CreateOrderRequestDto;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.GetOrderSummaryResponseDto;
import com.example.nexusCommerce.dtos.OrderItemActionDto;
import com.example.nexusCommerce.dtos.OrderItemRequestDto;
import com.example.nexusCommerce.dtos.OrderItemResponseDto;
import com.example.nexusCommerce.dtos.UpdateOrderRequestDto;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.OrderProductsRepository;
import com.example.nexusCommerce.repositories.OrderRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.OrderItemAction;
import com.example.nexusCommerce.schema.OrderProducts;
import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.User;
import com.example.nexusCommerce.utils.PageResponse;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderProductsRepository orderProductsRepository;

    @Mock
    private OrderAdapter orderAdapter;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Product sampleProduct() {
        Product product = Product.builder()
                .title("iPhone")
                .price(BigDecimal.valueOf(80000))
                .image("image.png")
                .rating(BigDecimal.valueOf(4.5))
                .build();
        product.setId(1L);
        return product;
    }

    private Order sampleOrder() {
        Order order = Order.builder().status(OrderStatus.PENDING).build();
        order.setId(1L);
        return order;
    }

    private User sampleUser() {
        User user = User.builder().name("Test User").email("test@example.com").build();
        user.setId(1L);
        return user;
    }

    @Test
    void getAllOrders_mapsPageOfOrders() {
        Order order = sampleOrder();
        GetOrderResponseDto dto = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();
        when(orderRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));
        when(orderProductsRepository.findByOrderIdsWithProduct(List.of(1L))).thenReturn(List.of());
        when(orderAdapter.mapToGetOrderResponseDtoList(List.of(order), Map.of())).thenReturn(List.of(dto));

        PageResponse<GetOrderResponseDto> result = orderService.getAllOrders(0, 20);

        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getContent().get(0).getId());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getAllOrders_whenNoOrders_skipsLineItemQuery() {
        when(orderRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(orderAdapter.mapToGetOrderResponseDtoList(List.of(), Map.of())).thenReturn(List.of());

        assertEquals(0, orderService.getAllOrders(0, 20).getContent().size());
        verify(orderProductsRepository, never()).findByOrderIdsWithProduct(any());
    }

    @Test
    void getAllOrders_loadsEveryLineItemInOneQueryInsteadOfOnePerOrder() {
        Order first = sampleOrder();
        Order second = sampleOrder();
        second.setId(2L);
        Product product = sampleProduct();
        OrderProducts firstItem = OrderProducts.builder().order(first).product(product).quantity(1).build();
        OrderProducts secondItem = OrderProducts.builder().order(second).product(product).quantity(3).build();

        when(orderRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 20), 2));
        when(orderProductsRepository.findByOrderIdsWithProduct(List.of(1L, 2L)))
                .thenReturn(List.of(firstItem, secondItem));

        orderService.getAllOrders(0, 20);

        verify(orderProductsRepository).findByOrderIdsWithProduct(List.of(1L, 2L));
        verify(orderProductsRepository, never()).findByOrderWithProduct(any());
    }

    @Test
    void getAllOrders_groupsFetchedLineItemsByOrder() {
        Order first = sampleOrder();
        Order second = sampleOrder();
        second.setId(2L);
        Product product = sampleProduct();
        OrderProducts firstItem = OrderProducts.builder().order(first).product(product).quantity(1).build();
        OrderProducts secondItem = OrderProducts.builder().order(second).product(product).quantity(3).build();
        Map<Long, List<OrderProducts>> expected = Map.of(1L, List.of(firstItem), 2L, List.of(secondItem));

        when(orderRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 20), 2));
        when(orderProductsRepository.findByOrderIdsWithProduct(List.of(1L, 2L)))
                .thenReturn(List.of(firstItem, secondItem));
        when(orderAdapter.mapToGetOrderResponseDtoList(List.of(first, second), expected)).thenReturn(List.of());

        orderService.getAllOrders(0, 20);

        verify(orderAdapter).mapToGetOrderResponseDtoList(List.of(first, second), expected);
    }

    @Test
    void getOrderById_mapsOrderWithItsLineItems() {
        Order order = sampleOrder();
        GetOrderResponseDto dto = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(List.of());
        when(orderAdapter.mapToGetOrderResponseDto(eq(order), anyList())).thenReturn(dto);

        GetOrderResponseDto result = orderService.getOrderById(1L);

        assertEquals(1L, result.getId());
    }

    @Test
    void getOrderById_whenNotFound_throwsResourceNotFoundException() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderById(1L));
    }

    @Test
    void deleteOrder_returnsReservedStockSoftDeletesLineItemsThenDeletes() {
        Order order = sampleOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        // Scalar (productId, quantity) pairs -- no managed OrderProducts are loaded, so the
        // commit never sees a line item referencing the soft-deleted Order.
        List<Object[]> deltas = List.of(new Object[]{1L, 2}, new Object[]{5L, 1});
        when(orderProductsRepository.findStockDeltasByOrderId(1L)).thenReturn(deltas);

        orderService.deleteOrder(1L);

        verify(productRepository).increaseStock(1L, 2);
        verify(productRepository).increaseStock(5L, 1);
        verify(orderProductsRepository).softDeleteByOrderId(1L);
        verify(orderRepository).delete(order);
    }

    @Test
    void deleteOrder_whenNotFound_throwsResourceNotFoundException() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.deleteOrder(1L));
    }

    @Test
    void deleteOrder_whenDelivered_throwsInvalidRequestAndTouchesNothing() {
        Order order = Order.builder().status(OrderStatus.DELIVERED).build();
        order.setId(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(InvalidRequestException.class, () -> orderService.deleteOrder(1L));
        verify(orderRepository, never()).delete(any());
        verify(orderProductsRepository, never()).softDeleteByOrderId(any());
    }

    @Test
    void createOrder_withItems_savesOrderAndLineItems() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        CreateOrderRequestDto dto = CreateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemRequestDto.builder().productId(1L).quantity(2).build()))
                .build();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser()));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(productRepository.decrementStock(1L, 2)).thenReturn(1);
        when(orderAdapter.mapToGetOrderResponseDto(any(Order.class), anyList())).thenReturn(response);

        GetOrderResponseDto result = orderService.createOrder(dto, 1L);

        assertEquals(OrderStatus.PENDING, result.getOrderStatus());
        verify(orderProductsRepository).saveAll(anyList());
        verify(productRepository).decrementStock(1L, 2);
    }

    @Test
    void createOrder_attachesAuthenticatedUserToOrder() {
        User user = sampleUser();
        CreateOrderRequestDto dto = CreateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemRequestDto.builder().productId(1L).quantity(1).build()))
                .build();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder());
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(sampleProduct()));
        when(productRepository.decrementStock(1L, 1)).thenReturn(1);
        when(orderAdapter.mapToGetOrderResponseDto(any(Order.class), anyList())).thenReturn(response);

        orderService.createOrder(dto, 1L);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertEquals(user, captor.getValue().getUser());
    }

    @Test
    void createOrder_whenNoAuthenticatedUser_throwsInvalidRequestException() {
        CreateOrderRequestDto dto = CreateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemRequestDto.builder().productId(1L).quantity(1).build()))
                .build();

        assertThrows(InvalidRequestException.class, () -> orderService.createOrder(dto, null));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_whenUserNotFound_throwsResourceNotFoundException() {
        CreateOrderRequestDto dto = CreateOrderRequestDto.builder().build();

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.createOrder(dto, 99L));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_whenProductNotFound_throwsResourceNotFoundException() {
        Order order = sampleOrder();
        CreateOrderRequestDto dto = CreateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemRequestDto.builder().productId(99L).quantity(1).build()))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser()));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(productRepository.findAllById(List.of(99L))).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> orderService.createOrder(dto, 1L));
    }

    @Test
    void createOrder_whenQuantityBelowOne_throwsInvalidRequestException() {
        CreateOrderRequestDto dto = CreateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemRequestDto.builder().productId(1L).quantity(-5).build()))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser()));
        when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder());
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(sampleProduct()));

        assertThrows(InvalidRequestException.class, () -> orderService.createOrder(dto, 1L));
        verify(orderProductsRepository, never()).saveAll(anyList());
    }

    @Test
    void createOrder_whenInsufficientStock_throwsInvalidRequestExceptionAndSavesNothing() {
        CreateOrderRequestDto dto = CreateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemRequestDto.builder().productId(1L).quantity(5).build()))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser()));
        when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder());
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(sampleProduct()));
        when(productRepository.decrementStock(1L, 5)).thenReturn(0);

        assertThrows(InvalidRequestException.class, () -> orderService.createOrder(dto, 1L));
        verify(orderProductsRepository, never()).saveAll(anyList());
    }

    @Test
    void createOrder_withEmptyOrderItems_createsPendingOrderOnly() {
        Order order = sampleOrder();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser()));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(orderAdapter.mapToGetOrderResponseDto(any(Order.class), anyList())).thenReturn(response);

        GetOrderResponseDto result = orderService.createOrder(CreateOrderRequestDto.builder().build(), 1L);

        assertEquals(OrderStatus.PENDING, result.getOrderStatus());
        verify(orderProductsRepository, never()).saveAll(anyList());
    }

    @Test
    void updateOrder_updatesStatusWhenProvided() {
        Order order = sampleOrder();
        UpdateOrderRequestDto dto = UpdateOrderRequestDto.builder().status(OrderStatus.SHIPPED).build();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.SHIPPED).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderAdapter.mapToGetOrderResponseDto(eq(order), anyList())).thenReturn(response);

        GetOrderResponseDto result = orderService.updateOrder(1L, dto);

        assertEquals(OrderStatus.SHIPPED, result.getOrderStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void updateOrder_addAction_addsNewLineItem() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        UpdateOrderRequestDto dto = UpdateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemActionDto.builder()
                        .productId(1L)
                        .quantity(2)
                        .action(OrderItemAction.ADD)
                        .build()))
                .build();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(productRepository.decrementStock(1L, 2)).thenReturn(1);
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(new ArrayList<>());
        when(orderAdapter.mapToGetOrderResponseDto(eq(order), anyList())).thenReturn(response);

        orderService.updateOrder(1L, dto);

        verify(orderProductsRepository).saveAll(anyList());
    }

    @Test
    void updateOrder_removeAction_deletesExistingLineItem() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        OrderProducts existing = OrderProducts.builder().order(order).product(product).productId(1L).quantity(1).build();
        existing.setId(10L);
        UpdateOrderRequestDto dto = UpdateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemActionDto.builder()
                        .productId(1L)
                        .action(OrderItemAction.REMOVE)
                        .build()))
                .build();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(List.of(existing));
        when(orderAdapter.mapToGetOrderResponseDto(eq(order), anyList())).thenReturn(response);

        orderService.updateOrder(1L, dto);

        verify(orderProductsRepository).deleteAll(List.of(existing));
    }

    @Test
    void updateOrder_incrementAction_increasesQuantity() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        OrderProducts existing = OrderProducts.builder().order(order).product(product).productId(1L).quantity(1).build();
        existing.setId(10L);
        UpdateOrderRequestDto dto = UpdateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemActionDto.builder()
                        .productId(1L)
                        .action(OrderItemAction.INCREMENT)
                        .build()))
                .build();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(productRepository.decrementStock(1L, 1)).thenReturn(1);
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(List.of(existing));
        when(orderAdapter.mapToGetOrderResponseDto(eq(order), anyList())).thenReturn(response);

        orderService.updateOrder(1L, dto);

        assertEquals(2, existing.getQuantity());
        verify(orderProductsRepository).saveAll(List.of(existing));
    }

    @Test
    void updateOrder_decrementAction_whenQuantityGreaterThanOne_reducesQuantity() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        OrderProducts existing = OrderProducts.builder().order(order).product(product).productId(1L).quantity(2).build();
        existing.setId(10L);
        UpdateOrderRequestDto dto = UpdateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemActionDto.builder()
                        .productId(1L)
                        .action(OrderItemAction.DECREMENT)
                        .build()))
                .build();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(List.of(existing));
        when(orderAdapter.mapToGetOrderResponseDto(eq(order), anyList())).thenReturn(response);

        orderService.updateOrder(1L, dto);

        assertEquals(1, existing.getQuantity());
        verify(orderProductsRepository).saveAll(List.of(existing));
    }

    @Test
    void updateOrder_decrementAction_whenQuantityIsOne_removesLineItem() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        OrderProducts existing = OrderProducts.builder().order(order).product(product).productId(1L).quantity(1).build();
        existing.setId(10L);
        UpdateOrderRequestDto dto = UpdateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemActionDto.builder()
                        .productId(1L)
                        .action(OrderItemAction.DECREMENT)
                        .build()))
                .build();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(List.of(existing));
        when(orderAdapter.mapToGetOrderResponseDto(eq(order), anyList())).thenReturn(response);

        orderService.updateOrder(1L, dto);

        verify(orderProductsRepository).deleteAll(List.of(existing));
    }

    @Test
    void updateOrder_removeAction_whenItemMissing_throwsResourceNotFoundException() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        UpdateOrderRequestDto dto = UpdateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemActionDto.builder()
                        .productId(1L)
                        .action(OrderItemAction.REMOVE)
                        .build()))
                .build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(new ArrayList<>());

        assertThrows(ResourceNotFoundException.class, () -> orderService.updateOrder(1L, dto));
    }

    @Test
    void getOrderSummary_computesTotalsFromLineItems() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        OrderProducts lineItem = OrderProducts.builder().order(order).product(product).quantity(2)
                .unitPrice(BigDecimal.valueOf(80000)).build();
        lineItem.setId(10L);
        OrderItemResponseDto itemDto = OrderItemResponseDto.builder()
                .productId(1L)
                .quantity(2)
                .productPrice(BigDecimal.valueOf(80000))
                .subTotal(BigDecimal.valueOf(160000))
                .build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(List.of(lineItem));
        when(orderAdapter.mapToOrderItemResponseDto(List.of(lineItem))).thenReturn(List.of(itemDto));

        GetOrderSummaryResponseDto result = orderService.getOrderSummary(1L);

        assertEquals(1L, result.getId());
        assertEquals(OrderStatus.PENDING, result.getStatus());
        assertEquals(2, result.getTotalItems());
        assertEquals(1, result.getItems().size());
        assertEquals(BigDecimal.valueOf(160000), result.getTotalPrice());
    }

    @Test
    void getOrderSummary_whenNotFound_throwsResourceNotFoundException() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderSummary(1L));
    }
}
