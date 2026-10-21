package com.example.nexusCommerce.services;

import static com.example.nexusCommerce.configs.CacheConfig.ORDER_ALL;
import static com.example.nexusCommerce.configs.CacheConfig.ORDER_BY_ID;
import static com.example.nexusCommerce.configs.CacheConfig.ORDER_SUMMARY;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_ALL;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_CATEGORY;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_ID;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_DETAILS;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.example.nexusCommerce.schema.OrderProducts;
import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.User;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderProductsRepository orderProductsRepository;
    private final OrderAdapter orderAdapter;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = ORDER_ALL, key = "#page + ':' + #size", sync = true)
    @Override
    public PageResponse<GetOrderResponseDto> getAllOrders(int page, int size){
        Page<Order> orders = orderRepository.findAll(Pagination.of(page, size));
        return pageOfOrders(orders);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<GetOrderResponseDto> getOrdersByUserId(Long userId, int page, int size){
        Page<Order> orders = orderRepository.findByUser_Id(userId, Pagination.of(page, size));
        return pageOfOrders(orders);
    }

    private PageResponse<GetOrderResponseDto> pageOfOrders(Page<Order> orders) {
        List<GetOrderResponseDto> content = orderAdapter.mapToGetOrderResponseDtoList(
                orders.getContent(), loadItemsByOrderId(orders.getContent()));
        return PageResponse.ofContent(orders, content);
    }

    /**
     * One fetch-join for the whole page, grouped in memory, instead of a per-order
     * lookup plus a lazy product load per line item.
     */
    private Map<Long, List<OrderProducts>> loadItemsByOrderId(List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
        return orderProductsRepository.findByOrderIdsWithProduct(orderIds).stream()
                .collect(Collectors.groupingBy(op -> op.getOrder().getId()));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = ORDER_BY_ID, key = "#id")
    @Override
    public GetOrderResponseDto getOrderById(Long id){
        Order order = orderRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Order with id: " + id + " not found"));
        return orderAdapter.mapToGetOrderResponseDto(
                order, orderProductsRepository.findByOrderWithProduct(order));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = ORDER_ALL, allEntries = true),
            @CacheEvict(cacheNames = { ORDER_BY_ID, ORDER_SUMMARY }, key = "#id"),
            @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_DETAILS, PRODUCT_BY_CATEGORY }, allEntries = true)
    })
    @Override
    public void deleteOrder(Long id){
        Order order = orderRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        assertModifiable(order);
        // Scalar projection instead of managed line items: an OrderProducts still in the
        // persistence context when the Order is soft-deleted makes the commit cascade
        // validation see the removed Order as transient and throw.
        for (Object[] row : orderProductsRepository.findStockDeltasByOrderId(id)) {
            returnStock((Long) row[0], (Integer) row[1]);
        }
        orderProductsRepository.softDeleteByOrderId(id);
        orderRepository.delete(order);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = { ORDER_ALL, ORDER_BY_ID, ORDER_SUMMARY }, allEntries = true),
            // Stock changed: the product read model must not serve a stale count.
            @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_DETAILS, PRODUCT_BY_CATEGORY }, allEntries = true)
    })
    @Override
    public GetOrderResponseDto createOrder(CreateOrderRequestDto createOrderRequestDto, Long authenticatedUserId){
        if (authenticatedUserId == null) {
            throw new InvalidRequestException("An authenticated user is required to create an order");
        }
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + authenticatedUserId));

        Order order = Order.builder()
                        .status(OrderStatus.PENDING)
                        .user(user)
                        .build();

        orderRepository.save(order);

        List<OrderProducts> savedItems = List.of();

        if (createOrderRequestDto.getOrderItems() != null && !createOrderRequestDto.getOrderItems().isEmpty()) {
            List<Long> productIds = createOrderRequestDto.getOrderItems().stream()
                    .map(OrderItemRequestDto::getProductId).collect(Collectors.toList());
            List<Product> products = productRepository.findAllById(productIds);
            Map<Long, Product> productMap = products.stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity()));

            List<OrderProducts> orderProducts = new ArrayList<>();
            for (OrderItemRequestDto itemDto : createOrderRequestDto.getOrderItems()) {
                Product product = productMap.get(itemDto.getProductId());
                if (product == null) {
                    throw new ResourceNotFoundException("Product not found with id: " + itemDto.getProductId());
                }
                int qty = resolveQuantity(itemDto.getQuantity());
                // Atomic conditional decrement: 0 affected rows means the stock was
                // insufficient, and the surrounding transaction rolls back every other
                // line's decrement too, so a partial oversell can never be committed.
                decrementOrThrow(product, qty);
                orderProducts.add(OrderProducts.builder()
                        .order(order)
                        .product(product)
                        .quantity(qty)
                        .unitPrice(product.getPrice())
                        .productName(product.getTitle())
                        .productImage(product.getImage())
                        .build());
            }
            savedItems = orderProductsRepository.saveAll(orderProducts);
        }

        // The line items are already in the persistence context with products resolved,
        // so the response costs no extra query.
        events.publishEvent(new com.example.nexusCommerce.events.OrderPlacedEvent(
                order.getId(), order.getUser().getId(), java.time.Instant.now()));
        return orderAdapter.mapToGetOrderResponseDto(order, savedItems);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = ORDER_ALL, allEntries = true),
            @CacheEvict(cacheNames = { ORDER_BY_ID, ORDER_SUMMARY }, key = "#id"),
            @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_DETAILS, PRODUCT_BY_CATEGORY }, allEntries = true)
    })
    @Override
    public GetOrderResponseDto updateOrder(Long id, UpdateOrderRequestDto updateOrderRequestDto){
        Order order = orderRepository.findById(id).orElseThrow(()-> new ResourceNotFoundException("Order not found with id " + id));
        assertModifiable(order);

        if(updateOrderRequestDto.getStatus()!=null){
            order.setStatus(updateOrderRequestDto.getStatus());
            orderRepository.save(order);
        }
        Map<Long, OrderProducts> existingItems = null;
        if(updateOrderRequestDto.getOrderItems()!= null){
            List<Long> productIds = updateOrderRequestDto.getOrderItems().stream()
                                                            .map(OrderItemActionDto::getProductId)
                                                            .collect(Collectors.toList());
            List<Product> products = productRepository.findAllById(productIds);

            Map<Long, Product> productMap = products.stream()
                                                .collect(Collectors.toMap(Product::getId, Function.identity()));

            for(var pid : productIds){
                if(!productMap.containsKey(pid)){
                    throw new ResourceNotFoundException("Product not found with id: " + pid);
                }
            }

            List<OrderProducts> toSave = new ArrayList<>();
            List<OrderProducts> toDelete = new ArrayList<>();

            existingItems = orderProductsRepository.findByOrderWithProduct(order).stream()
                                                        .collect(Collectors.toMap(OrderProducts::getProductId, Function.identity()));

            for(OrderItemActionDto itemAction : updateOrderRequestDto.getOrderItems()){
                Product product = productMap.get(itemAction.getProductId());
                Long productId = product.getId();
                OrderProducts existing = existingItems.get(productId);

                switch(itemAction.getAction()){
                    case ADD -> {
                        int addQty = resolveQuantity(itemAction.getQuantity());
                        if(existing != null){
                            decrementOrThrow(product, addQty);
                            existing.setQuantity(existing.getQuantity() + addQty);
                            toSave.add(existing);
                        }
                        else{
                            decrementOrThrow(product, addQty);
                            OrderProducts newItem = OrderProducts.builder()
                                                        .order(order)
                                                        .product(product)
                                                        .quantity(addQty)
                                                        .unitPrice(product.getPrice())
                                                        .productName(product.getTitle())
                                                        .productImage(product.getImage())
                                                        .build();
                            existingItems.put(productId, newItem);
                            toSave.add(newItem);
                        }
                    }
                    case REMOVE ->{
                        if(existing == null){
                            throw new ResourceNotFoundException("Product not found with id: " + productId);
                        }
                        returnStock(productId, existing.getQuantity());
                        toDelete.add(existing);
                        existingItems.remove(productId);
                    }
                    case INCREMENT ->{
                        if(existing == null){
                            throw new ResourceNotFoundException("Product not found with id: " + productId);
                        }
                        decrementOrThrow(product, 1);
                        existing.setQuantity(existing.getQuantity() + 1);
                        toSave.add(existing);
                    }
                    case DECREMENT -> {
                        if(existing == null){
                            throw new ResourceNotFoundException("Product not found with id: " + productId);
                        }
                        returnStock(productId, 1);
                        if(existing.getQuantity() <= 1){
                            toDelete.add(existing);
                            existingItems.remove(productId);
                        }
                        else{
                            existing.setQuantity(existing.getQuantity() - 1);
                            toSave.add(existing);
                        }
                    }
                }
            }

            if(!toSave.isEmpty()){
                orderProductsRepository.saveAll(toSave);
            }
            if(!toDelete.isEmpty()){
                orderProductsRepository.deleteAll(toDelete);
            }
        }

        // existingItems already reflects the post-mutation state with products resolved,
        // so responding costs no extra query; only a status-only update needs to read.
        List<OrderProducts> currentItems = updateOrderRequestDto.getOrderItems() != null
                ? new ArrayList<>(existingItems.values())
                : orderProductsRepository.findByOrderWithProduct(order);

        return orderAdapter.mapToGetOrderResponseDto(order, currentItems);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = ORDER_SUMMARY, key = "#id")
    @Override
    public GetOrderSummaryResponseDto getOrderSummary(Long id){
        Order order = orderRepository.findById(id).orElseThrow(()-> new ResourceNotFoundException("Order not found with id: " + id));

        List<OrderProducts> orderProducts = orderProductsRepository.findByOrderWithProduct(order);

        List<OrderItemResponseDto> items = orderAdapter.mapToOrderItemResponseDto(orderProducts);

        int totalItems = orderProducts.stream().mapToInt(OrderProducts::getQuantity).sum();

        BigDecimal totalPrice = orderProducts.stream()
                                .map(op->op.getUnitPrice().multiply(BigDecimal.valueOf(op.getQuantity())))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return GetOrderSummaryResponseDto.builder()
                .id(order.getId())
                .status(order.getStatus())
                .items(items)
                .totalItems(totalItems)
                .totalPrice(totalPrice)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private void decrementOrThrow(Product product, int qty) {
        if (productRepository.decrementStock(product.getId(), qty) == 0) {
            throw new InvalidRequestException(
                    "Insufficient stock for product " + product.getId() + " (" + product.getTitle()
                            + "): requested " + qty + " but not enough available");
        }
    }

    /**
     * Returns reserved stock. Atomic bulk UPDATE so two concurrent returns of the same
     * product cannot both read the old count and clobber each other (lost update). The
     * @SQLDelete version-mismatch that used to break the order soft-delete has been fixed,
     * so a bulk statement can now safely share the transaction with the versioned delete.
     */
    private void returnStock(Long productId, int qty) {
        productRepository.increaseStock(productId, qty);
    }

    /**
     * A delivered or cancelled order is a record of what happened, not a live cart: letting
     * it be mutated or deleted would desync stock and revenue from reality.
     */
    private void assertModifiable(Order order) {
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new InvalidRequestException(
                    "Order " + order.getId() + " is " + order.getStatus() + " and can no longer be modified");
        }
    }

    /**
     * A missing quantity means one item; zero or negative quantities were previously
     * persisted and made order totals negative.
     */
    private static int resolveQuantity(Integer quantity) {
        if (quantity == null) {
            return 1;
        }
        if (quantity < 1) {
            throw new InvalidRequestException("Order item quantity must be at least 1, got " + quantity);
        }
        return quantity;
    }
}
