package com.example.NexusCommerce.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.nexusCommerce.adapters.OrderAdapter;
import com.example.nexusCommerce.dtos.CreateOrderRequestDto;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.GetOrderSummaryResponseDto;
import com.example.nexusCommerce.dtos.OrderItemActionDto;
import com.example.nexusCommerce.dtos.OrderItemRequestDto;
import com.example.nexusCommerce.dtos.OrderItemResponseDto;
import com.example.nexusCommerce.dtos.updateOrderRequestDto;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.OrderProductsRepository;
import com.example.nexusCommerce.repositories.OrderRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.OrderItemAction;
import com.example.nexusCommerce.schema.OrderProducts;
import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.services.OrderService;
import com.example.nexusCommerce.services.cache.OrderRedisCache;

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
    private OrderRedisCache orderRedisCache;

    @InjectMocks
    private OrderService orderService;

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

    @Test
    void getAllOrders_whenCacheHit_returnsCachedOrders() {
        GetOrderResponseDto dto = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();
        when(orderRedisCache.getAll()).thenReturn(Optional.of(List.of(dto)));

        List<GetOrderResponseDto> result = orderService.getAllOrders();

        assertEquals(1, result.size());
        verify(orderRepository, never()).findAll();
    }

    @Test
    void getAllOrders_whenCacheMiss_loadsFromRepositoryAndCaches() {
        Order order = sampleOrder();
        GetOrderResponseDto dto = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();
        when(orderRedisCache.getAll()).thenReturn(Optional.empty());
        when(orderRepository.findAll()).thenReturn(List.of(order));
        when(orderAdapter.mapToGetOrderResponseDtoList(List.of(order))).thenReturn(List.of(dto));

        List<GetOrderResponseDto> result = orderService.getAllOrders();

        assertEquals(1, result.size());
        verify(orderRedisCache).putAll(List.of(dto));
    }

    @Test
    void getOrderById_whenCacheMiss_loadsFromRepositoryAndCaches() {
        Order order = sampleOrder();
        GetOrderResponseDto dto = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();
        when(orderRedisCache.getById(1L)).thenReturn(Optional.empty());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderAdapter.mapToGetOrderResponseDto(order)).thenReturn(dto);

        GetOrderResponseDto result = orderService.getOrderById(1L);

        assertEquals(1L, result.getId());
        verify(orderRedisCache).putById(1L, dto);
    }

    @Test
    void getOrderById_whenNotFound_throwsResourceNotFoundException() {
        when(orderRedisCache.getById(1L)).thenReturn(Optional.empty());
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderById(1L));
    }

    @Test
    void deleteOrder_deletesWhenOrderExists() {
        Order order = sampleOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.deleteOrder(1L);

        verify(orderRepository).delete(order);
    }

    @Test
    void deleteOrder_whenNotFound_throwsResourceNotFoundException() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.deleteOrder(1L));
    }

    @Test
    void createOrder_withItems_savesOrderAndLineItems() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        CreateOrderRequestDto dto = CreateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemRequestDto.builder().productId(1L).quantity(2).build()))
                .build();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();

        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(orderAdapter.mapToGetOrderResponseDto(order)).thenReturn(response);

        GetOrderResponseDto result = orderService.createOrder(dto);

        assertEquals(OrderStatus.PENDING, result.getOrderStatus());
        verify(orderProductsRepository).saveAll(anyList());
    }

    @Test
    void createOrder_whenProductNotFound_throwsResourceNotFoundException() {
        Order order = sampleOrder();
        CreateOrderRequestDto dto = CreateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemRequestDto.builder().productId(99L).quantity(1).build()))
                .build();

        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(productRepository.findAllById(List.of(99L))).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> orderService.createOrder(dto));
    }

    @Test
    void createOrder_withNullOrderItems_createsPendingOrderOnly() {
        Order order = sampleOrder();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();

        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(orderAdapter.mapToGetOrderResponseDto(order)).thenReturn(response);

        GetOrderResponseDto result = orderService.createOrder(CreateOrderRequestDto.builder().build());

        assertEquals(OrderStatus.PENDING, result.getOrderStatus());
        verify(orderProductsRepository, never()).saveAll(anyList());
    }

    @Test
    void updateOrder_updatesStatusWhenProvided() {
        Order order = sampleOrder();
        updateOrderRequestDto dto = updateOrderRequestDto.builder().status(OrderStatus.SHIPPED).build();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.SHIPPED).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderAdapter.mapToGetOrderResponseDto(order)).thenReturn(response);

        GetOrderResponseDto result = orderService.updateOrder(1L, dto);

        assertEquals(OrderStatus.SHIPPED, result.getOrderStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void updateOrder_addAction_addsNewLineItem() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        updateOrderRequestDto dto = updateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemActionDto.builder()
                        .productId(1L)
                        .quantity(2)
                        .action(OrderItemAction.ADD)
                        .build()))
                .build();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(new ArrayList<>());
        when(orderAdapter.mapToGetOrderResponseDto(order)).thenReturn(response);

        orderService.updateOrder(1L, dto);

        verify(orderProductsRepository).saveAll(anyList());
    }

    @Test
    void updateOrder_removeAction_deletesExistingLineItem() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        OrderProducts existing = OrderProducts.builder().order(order).product(product).quantity(1).build();
        existing.setId(10L);
        updateOrderRequestDto dto = updateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemActionDto.builder()
                        .productId(1L)
                        .action(OrderItemAction.REMOVE)
                        .build()))
                .build();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(List.of(existing));
        when(orderAdapter.mapToGetOrderResponseDto(order)).thenReturn(response);

        orderService.updateOrder(1L, dto);

        verify(orderProductsRepository).deleteAll(List.of(existing));
    }

    @Test
    void updateOrder_incrementAction_increasesQuantity() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        OrderProducts existing = OrderProducts.builder().order(order).product(product).quantity(1).build();
        existing.setId(10L);
        updateOrderRequestDto dto = updateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemActionDto.builder()
                        .productId(1L)
                        .action(OrderItemAction.INCREMENT)
                        .build()))
                .build();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(List.of(existing));
        when(orderAdapter.mapToGetOrderResponseDto(order)).thenReturn(response);

        orderService.updateOrder(1L, dto);

        assertEquals(2, existing.getQuantity());
        verify(orderProductsRepository).saveAll(List.of(existing));
    }

    @Test
    void updateOrder_decrementAction_whenQuantityGreaterThanOne_reducesQuantity() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        OrderProducts existing = OrderProducts.builder().order(order).product(product).quantity(2).build();
        existing.setId(10L);
        updateOrderRequestDto dto = updateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemActionDto.builder()
                        .productId(1L)
                        .action(OrderItemAction.DECREMENT)
                        .build()))
                .build();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(List.of(existing));
        when(orderAdapter.mapToGetOrderResponseDto(order)).thenReturn(response);

        orderService.updateOrder(1L, dto);

        assertEquals(1, existing.getQuantity());
        verify(orderProductsRepository).saveAll(List.of(existing));
    }

    @Test
    void updateOrder_decrementAction_whenQuantityIsOne_removesLineItem() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        OrderProducts existing = OrderProducts.builder().order(order).product(product).quantity(1).build();
        existing.setId(10L);
        updateOrderRequestDto dto = updateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemActionDto.builder()
                        .productId(1L)
                        .action(OrderItemAction.DECREMENT)
                        .build()))
                .build();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(List.of(existing));
        when(orderAdapter.mapToGetOrderResponseDto(order)).thenReturn(response);

        orderService.updateOrder(1L, dto);

        verify(orderProductsRepository).deleteAll(List.of(existing));
    }

    @Test
    void updateOrder_removeAction_whenItemMissing_throwsResourceNotFoundException() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        updateOrderRequestDto dto = updateOrderRequestDto.builder()
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
    void getOrderSummary_whenCacheMiss_buildsSummaryAndCaches() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        OrderProducts lineItem = OrderProducts.builder().order(order).product(product).quantity(2).build();
        lineItem.setId(10L);
        OrderItemResponseDto itemDto = OrderItemResponseDto.builder()
                .productId(1L)
                .quantity(2)
                .productPrice(BigDecimal.valueOf(80000))
                .subTotal(BigDecimal.valueOf(160000))
                .build();

        when(orderRedisCache.getSummary(1L)).thenReturn(Optional.empty());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(List.of(lineItem));
        when(orderAdapter.mapToOrderItemResponseDto(List.of(lineItem))).thenReturn(List.of(itemDto));

        GetOrderSummaryResponseDto result = orderService.getOrderSummary(1L);

        assertEquals(1L, result.getId());
        assertEquals(OrderStatus.PENDING, result.getStatus());
        assertEquals(2, result.getTotalItems());
        assertEquals(1, result.getItems().size());
        verify(orderRedisCache).putSummary(1L, result);
    }

    @Test
    void getOrderSummary_whenNotFound_throwsResourceNotFoundException() {
        when(orderRedisCache.getSummary(1L)).thenReturn(Optional.empty());
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderSummary(1L));
    }
}
