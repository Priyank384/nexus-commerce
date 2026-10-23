package com.example.nexusCommerce.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import org.springframework.data.domain.Sort;

import com.example.nexusCommerce.adapters.OrderAdapter;
import com.example.nexusCommerce.dtos.CreateOrderRequestDto;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.GetOrderSummaryResponseDto;
import com.example.nexusCommerce.dtos.OrderItemActionDto;
import com.example.nexusCommerce.dtos.OrderItemRequestDto;
import com.example.nexusCommerce.dtos.OrderItemResponseDto;
import com.example.nexusCommerce.dtos.UpdateOrderRequestDto;
import com.example.nexusCommerce.exceptions.ForbiddenException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.AddressRepository;
import com.example.nexusCommerce.repositories.OrderProductsRepository;
import com.example.nexusCommerce.repositories.OrderRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.repositories.ProductVariantRepository;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.Address;
import com.example.nexusCommerce.schema.AddressSnapshot;
import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.OrderItemAction;
import com.example.nexusCommerce.schema.OrderProducts;
import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.PaymentStatus;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductVariant;
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
    private ProductVariantRepository variantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    // Stock reservation now happens on the variant, so the rule that picks one is exercised
    // by VariantResolverTest; here it is stubbed to keep this class about order semantics.
    @Mock
    private VariantResolver variantResolver;

    @Mock
    private org.springframework.context.ApplicationEventPublisher events;

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

    private ProductVariant sampleVariant() {
        ProductVariant variant = ProductVariant.builder()
                .sku("IPHONE-BLACK-M")
                .size("M")
                .colorName("Black")
                .colorHex("#000000")
                .priceDelta(BigDecimal.ZERO)
                .stock(10)
                .build();
        variant.setId(11L);
        return variant;
    }

    /**
     * Stubs the variant pipeline for a one-variant order of {@link #sampleProduct()}. Lenient
     * because not every path reaches all three: a rejected quantity stops before the sum, and
     * an existing line is never repriced.
     */
    private void givenSingleVariantStock(int remainingAfterSale) {
        ProductVariant variant = sampleVariant();
        lenient().when(variantResolver.resolve(any(Product.class), any())).thenReturn(variant);
        lenient().when(variantResolver.unitPrice(any(Product.class), any(ProductVariant.class)))
                .thenAnswer(inv -> inv.getArgument(0, Product.class).getPrice());
        lenient().when(variantRepository.sumStockByProductId(1L)).thenReturn((long) remainingAfterSale);
    }

    private Order sampleOrder() {
        Order order = Order.builder().status(OrderStatus.PENDING).user(sampleUser()).build();
        order.setId(1L);
        return order;
    }

    private User sampleUser() {
        User user = User.builder().name("Test User").email("test@example.com").build();
        user.setId(1L);
        return user;
    }

    private Address sampleAddress(Long userId) {
        Address address = Address.builder()
                .recipientName("Asha Rao")
                .phone("+91 98765 43210")
                .line1("14 Lavelle Road")
                .line2("Flat 3B")
                .city("Bengaluru")
                .state("Karnataka")
                .postalCode("560001")
                .country("IN")
                .build();
        address.setUserId(userId);
        return address;
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
    void getAllOrders_asksForNewestFirst() {
        when(orderRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        orderService.getAllOrders(0, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAll(captor.capture());
        assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("id").getDirection());
    }

    @Test
    void getOrdersByUserId_asksForNewestFirst() {
        when(orderRepository.findByUser_Id(eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        orderService.getOrdersByUserId(7L, 0, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findByUser_Id(eq(7L), captor.capture());
        assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("id").getDirection());
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
        // Scalar (variantId, productId, quantity) triples -- no managed OrderProducts are
        // loaded, so the commit never sees a line item referencing the soft-deleted Order.
        // The null variant is a pre-V20 line, whose stock still lives on the product.
        List<Object[]> deltas = List.of(new Object[]{11L, 1L, 2}, new Object[]{null, 5L, 1});
        when(orderProductsRepository.findStockDeltasByOrderId(1L)).thenReturn(deltas);
        when(variantRepository.sumStockByProductId(1L)).thenReturn(10L);
        when(variantRepository.sumStockByProductId(5L)).thenReturn(4L);

        orderService.deleteOrder(1L);

        verify(variantRepository).increaseStock(11L, 2);
        verify(productRepository).increaseStock(5L, 1);
        verify(productRepository).setStock(1L, 10);
        verify(productRepository).setStock(5L, 4);
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
        when(variantRepository.decrementStock(11L, 2)).thenReturn(1);
        givenSingleVariantStock(8);
        when(orderAdapter.mapToGetOrderResponseDto(any(Order.class), anyList())).thenReturn(response);

        GetOrderResponseDto result = orderService.createOrder(dto, 1L);

        assertEquals(OrderStatus.PENDING, result.getOrderStatus());
        verify(orderProductsRepository).saveAll(anyList());
        verify(variantRepository).decrementStock(11L, 2);
        // The legacy product counter mirrors the variant aggregate instead of being decremented.
        verify(productRepository).setStock(1L, 8);
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
        when(variantRepository.decrementStock(11L, 1)).thenReturn(1);
        givenSingleVariantStock(9);
        when(orderAdapter.mapToGetOrderResponseDto(any(Order.class), anyList())).thenReturn(response);

        orderService.createOrder(dto, 1L);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertEquals(user, captor.getValue().getUser());
        assertNull(captor.getValue().getAddressSnapshot(), "no addressId means no snapshot");
    }

    @Test
    void createOrder_snapshotsTheShippingAddress() {
        Address address = sampleAddress(1L);
        CreateOrderRequestDto dto = CreateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemRequestDto.builder().productId(1L).quantity(1).build()))
                .addressId(7L)
                .build();
        GetOrderResponseDto response = GetOrderResponseDto.builder().id(1L).orderStatus(OrderStatus.PENDING).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser()));
        when(addressRepository.findById(7L)).thenReturn(Optional.of(address));
        when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder());
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(sampleProduct()));
        when(variantRepository.decrementStock(11L, 1)).thenReturn(1);
        givenSingleVariantStock(9);
        when(orderAdapter.mapToGetOrderResponseDto(any(Order.class), anyList())).thenReturn(response);

        orderService.createOrder(dto, 1L);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        AddressSnapshot snapshot = captor.getValue().getAddressSnapshot();
        assertNotNull(snapshot);
        assertEquals("Asha Rao", snapshot.getRecipientName());
        assertEquals("14 Lavelle Road", snapshot.getLine1());
        assertEquals("560001", snapshot.getPostalCode());
        assertEquals("IN", snapshot.getCountry());
    }

    @Test
    void createOrder_rejectsAnAddressOwnedByAnotherUser() {
        CreateOrderRequestDto dto = CreateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemRequestDto.builder().productId(1L).quantity(1).build()))
                .addressId(7L)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser()));
        when(addressRepository.findById(7L)).thenReturn(Optional.of(sampleAddress(2L)));

        assertThrows(ForbiddenException.class, () -> orderService.createOrder(dto, 1L));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_whenAddressNotFound_throwsResourceNotFoundException() {
        CreateOrderRequestDto dto = CreateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemRequestDto.builder().productId(1L).quantity(1).build()))
                .addressId(404L)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser()));
        when(addressRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.createOrder(dto, 1L));
        verify(orderRepository, never()).save(any(Order.class));
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
        givenSingleVariantStock(10);

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
        givenSingleVariantStock(0);
        when(variantRepository.decrementStock(11L, 5)).thenReturn(0);

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

        GetOrderResponseDto result = orderService.updateOrder(1L, dto, 1L, true);

        assertEquals(OrderStatus.SHIPPED, result.getOrderStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void updateOrder_ownerChangingStatus_isForbidden() {
        Order order = sampleOrder();
        UpdateOrderRequestDto dto = UpdateOrderRequestDto.builder().status(OrderStatus.SHIPPED).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(ForbiddenException.class, () -> orderService.updateOrder(1L, dto, 1L, false));
        verify(orderRepository, never()).save(order);
    }

    @Test
    void updateOrder_ownerEditingPaidOrder_isForbidden() {
        Order order = sampleOrder();
        order.setPaymentStatus(PaymentStatus.PAID);
        UpdateOrderRequestDto dto = UpdateOrderRequestDto.builder()
                .orderItems(List.of(OrderItemActionDto.builder()
                        .productId(1L)
                        .action(OrderItemAction.REMOVE)
                        .build()))
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(ForbiddenException.class, () -> orderService.updateOrder(1L, dto, 1L, false));
        verify(productRepository, never()).findAllById(anyList());
    }

    @Test
    void updateOrder_adminEditingPaidOrder_isAllowed() {
        Order order = sampleOrder();
        order.setPaymentStatus(PaymentStatus.PAID);
        UpdateOrderRequestDto dto = UpdateOrderRequestDto.builder().status(OrderStatus.SHIPPED).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.updateOrder(1L, dto, 99L, true);

        assertEquals(OrderStatus.SHIPPED, order.getStatus());
    }

    @Test
    void updateOrder_skippingStraightToDelivered_isRejected() {
        Order order = sampleOrder();
        UpdateOrderRequestDto dto = UpdateOrderRequestDto.builder().status(OrderStatus.DELIVERED).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(InvalidRequestException.class, () -> orderService.updateOrder(1L, dto, 1L, true));
        assertEquals(OrderStatus.PENDING, order.getStatus());
        verify(orderRepository, never()).save(order);
    }

    @Test
    void updateOrder_cancellingRestocksEveryLineOnce() {
        Order order = sampleOrder();
        UpdateOrderRequestDto dto = UpdateOrderRequestDto.builder().status(OrderStatus.CANCELLED).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderProductsRepository.findStockDeltasByOrderId(1L))
                .thenReturn(List.<Object[]>of(new Object[] { 11L, 1L, 2 }));
        givenSingleVariantStock(12);

        orderService.updateOrder(1L, dto, 1L, true);

        verify(variantRepository).increaseStock(11L, 2);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void updateOrder_cancelCombinedWithLineEdits_isRejected() {
        Order order = sampleOrder();
        UpdateOrderRequestDto dto = UpdateOrderRequestDto.builder()
                .status(OrderStatus.CANCELLED)
                .orderItems(List.of(OrderItemActionDto.builder()
                        .productId(1L)
                        .action(OrderItemAction.REMOVE)
                        .build()))
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(InvalidRequestException.class, () -> orderService.updateOrder(1L, dto, 1L, true));
        verify(variantRepository, never()).increaseStock(anyLong(), anyInt());
    }

    @Test
    void updateOrder_forAnotherUsersOrder_isForbiddenAtTheService() {
        Order order = sampleOrder();
        UpdateOrderRequestDto dto = UpdateOrderRequestDto.builder().status(OrderStatus.SHIPPED).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(ForbiddenException.class, () -> orderService.updateOrder(1L, dto, 7L, false));
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
        when(variantRepository.decrementStock(11L, 2)).thenReturn(1);
        givenSingleVariantStock(8);
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(new ArrayList<>());
        when(orderAdapter.mapToGetOrderResponseDto(eq(order), anyList())).thenReturn(response);

        orderService.updateOrder(1L, dto, 1L, false);

        verify(orderProductsRepository).saveAll(anyList());
    }

    @Test
    void updateOrder_removeAction_deletesExistingLineItem() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        OrderProducts existing = OrderProducts.builder().order(order).product(product).productId(1L)
                        .variant(sampleVariant()).variantId(11L).quantity(1).build();
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
        givenSingleVariantStock(10);
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(List.of(existing));
        when(orderAdapter.mapToGetOrderResponseDto(eq(order), anyList())).thenReturn(response);

        orderService.updateOrder(1L, dto, 1L, false);

        verify(variantRepository).increaseStock(11L, 1);
        verify(orderProductsRepository).deleteAll(List.of(existing));
    }

    @Test
    void updateOrder_incrementAction_increasesQuantity() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        OrderProducts existing = OrderProducts.builder().order(order).product(product).productId(1L)
                        .variant(sampleVariant()).variantId(11L).quantity(1).build();
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
        when(variantRepository.decrementStock(11L, 1)).thenReturn(1);
        givenSingleVariantStock(9);
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(List.of(existing));
        when(orderAdapter.mapToGetOrderResponseDto(eq(order), anyList())).thenReturn(response);

        orderService.updateOrder(1L, dto, 1L, false);

        assertEquals(2, existing.getQuantity());
        verify(orderProductsRepository).saveAll(List.of(existing));
    }

    @Test
    void updateOrder_decrementAction_whenQuantityGreaterThanOne_reducesQuantity() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        OrderProducts existing = OrderProducts.builder().order(order).product(product).productId(1L)
                        .variant(sampleVariant()).variantId(11L).quantity(2).build();
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
        givenSingleVariantStock(11);
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(List.of(existing));
        when(orderAdapter.mapToGetOrderResponseDto(eq(order), anyList())).thenReturn(response);

        orderService.updateOrder(1L, dto, 1L, false);

        assertEquals(1, existing.getQuantity());
        verify(orderProductsRepository).saveAll(List.of(existing));
    }

    @Test
    void updateOrder_decrementAction_whenQuantityIsOne_removesLineItem() {
        Order order = sampleOrder();
        Product product = sampleProduct();
        OrderProducts existing = OrderProducts.builder().order(order).product(product).productId(1L)
                        .variant(sampleVariant()).variantId(11L).quantity(1).build();
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
        givenSingleVariantStock(10);
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(List.of(existing));
        when(orderAdapter.mapToGetOrderResponseDto(eq(order), anyList())).thenReturn(response);

        orderService.updateOrder(1L, dto, 1L, false);

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
        givenSingleVariantStock(10);
        when(orderProductsRepository.findByOrderWithProduct(order)).thenReturn(new ArrayList<>());

        assertThrows(ResourceNotFoundException.class, () -> orderService.updateOrder(1L, dto, 1L, false));
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
