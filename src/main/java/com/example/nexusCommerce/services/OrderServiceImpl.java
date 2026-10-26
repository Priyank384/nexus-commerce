package com.example.nexusCommerce.services;

import static com.example.nexusCommerce.configs.CacheConfig.ORDER_ALL;
import static com.example.nexusCommerce.configs.CacheConfig.ORDER_BY_ID;
import static com.example.nexusCommerce.configs.CacheConfig.ORDER_SUMMARY;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_ALL;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_CATEGORY;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_ID;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_SLUG;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_DETAILS;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_SEARCH;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import com.example.nexusCommerce.schema.OrderProducts;
import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.PaymentStatus;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductVariant;
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
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final VariantResolver variantResolver;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = ORDER_ALL, key = "#page + ':' + #size", sync = true)
    @Override
    public PageResponse<GetOrderResponseDto> getAllOrders(int page, int size){
        // The admin queue and the shopper's history both read newest first; Pagination.of
        // pins id ascending, so every order list overrides it here.
        Page<Order> orders = orderRepository.findAll(newestFirst(page, size));
        return pageOfOrders(orders);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<GetOrderResponseDto> getOrdersByUserId(Long userId, int page, int size){
        Page<Order> orders = orderRepository.findByUser_Id(userId, newestFirst(page, size));
        return pageOfOrders(orders);
    }

    private PageRequest newestFirst(int page, int size) {
        return Pagination.of(page, size).withSort(Sort.by(Sort.Direction.DESC, "id"));
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
            @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_BY_SLUG, PRODUCT_DETAILS, PRODUCT_BY_CATEGORY, PRODUCT_SEARCH }, allEntries = true)
    })
    @Override
    public void deleteOrder(Long id){
        Order order = orderRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        assertDeletable(order);
        restockOrder(id);
        orderProductsRepository.softDeleteByOrderId(id);
        orderRepository.delete(order);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = { ORDER_ALL, ORDER_BY_ID, ORDER_SUMMARY }, allEntries = true),
            // Stock changed: the product read model must not serve a stale count.
            @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_BY_SLUG, PRODUCT_DETAILS, PRODUCT_BY_CATEGORY, PRODUCT_SEARCH }, allEntries = true)
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
                        .addressSnapshot(snapshotOf(createOrderRequestDto.getAddressId(), authenticatedUserId))
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
            Set<Long> touchedProducts = new HashSet<>();
            for (OrderItemRequestDto itemDto : createOrderRequestDto.getOrderItems()) {
                Product product = productMap.get(itemDto.getProductId());
                if (product == null) {
                    throw new ResourceNotFoundException("Product not found with id: " + itemDto.getProductId());
                }
                ProductVariant variant = variantResolver.resolve(product, itemDto.getVariantId());
                int qty = resolveQuantity(itemDto.getQuantity());
                // Atomic conditional decrement on the variant, which is the unit that is
                // actually picked off a shelf: 0 affected rows means that size+colour ran out
                // and the surrounding transaction rolls back every other line too, so a
                // partial oversell can never be committed.
                decrementOrThrow(product, variant, qty);
                touchedProducts.add(product.getId());
                orderProducts.add(OrderProducts.builder()
                        .order(order)
                        .product(product)
                        .variant(variant)
                        .quantity(qty)
                        .unitPrice(variantResolver.unitPrice(product, variant))
                        .productName(product.getTitle())
                        .productImage(variant.getImageUrl() != null ? variant.getImageUrl() : product.getImage())
                        .variantSize(variant.getSize())
                        .variantColorName(variant.getColorName())
                        .variantColorHex(variant.getColorHex())
                        .build());
            }
            savedItems = orderProductsRepository.saveAll(orderProducts);
            resyncProductStock(touchedProducts);
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
            @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_BY_SLUG, PRODUCT_DETAILS, PRODUCT_BY_CATEGORY, PRODUCT_SEARCH }, allEntries = true)
    })
    @Override
    public GetOrderResponseDto updateOrder(Long id, UpdateOrderRequestDto updateOrderRequestDto,
            Long callerId, boolean callerIsAdmin){
        Order order = orderRepository.findByIdForUpdate(id).orElseThrow(()-> new ResourceNotFoundException("Order not found with id " + id));
        assertModifiable(order);
        assertMayMutate(order, updateOrderRequestDto, callerId, callerIsAdmin);
        if (order.getPaymentStatus() == PaymentStatus.PAID
                && updateOrderRequestDto.getOrderItems() != null
                && !updateOrderRequestDto.getOrderItems().isEmpty()) {
            // The captured amount bought these exact lines. An administrator can still move
            // fulfilment forward, but changing what was sold is a refund, not an edit.
            throw new InvalidRequestException(
                    "Order " + id + " has been paid; its line items can no longer be edited");
        }

        OrderStatus requestedStatus = updateOrderRequestDto.getStatus();
        if (requestedStatus == OrderStatus.CANCELLED
                && updateOrderRequestDto.getOrderItems() != null
                && !updateOrderRequestDto.getOrderItems().isEmpty()) {
            throw new InvalidRequestException("Cancel the order in its own request; line edits cannot ride along");
        }

        Map<String, OrderProducts> existingItems = null;
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
            Set<Long> touchedProducts = new HashSet<>();

            // Keyed by (product, variant): since V22 a shirt in Medium and a shirt in Large
            // are two separate lines of the same order, so a product-keyed map would merge
            // them and let an edit silently drop one of the sizes.
            existingItems = orderProductsRepository.findByOrderWithProduct(order).stream()
                    .collect(Collectors.toMap(OrderServiceImpl::lineKey, Function.identity()));

            for (OrderItemActionDto itemAction : updateOrderRequestDto.getOrderItems()) {
                Product product = productMap.get(itemAction.getProductId());
                Long productId = product.getId();
                ProductVariant variant = variantResolver.resolve(product, itemAction.getVariantId());
                String key = lineKey(productId, variant.getId());
                OrderProducts existing = existingItems.get(key);

                switch(itemAction.getAction()){
                    case ADD -> {
                        int addQty = resolveQuantity(itemAction.getQuantity());
                        if(existing != null){
                            decrementOrThrow(product, variant, addQty);
                            touchedProducts.add(productId);
                            existing.setQuantity(existing.getQuantity() + addQty);
                            toSave.add(existing);
                        }
                        else{
                            decrementOrThrow(product, variant, addQty);
                            touchedProducts.add(productId);
                            OrderProducts newItem = OrderProducts.builder()
                                                        .order(order)
                                                        .product(product)
                                                        .variant(variant)
                                                        .quantity(addQty)
                                                        .unitPrice(variantResolver.unitPrice(product, variant))
                                                        .productName(product.getTitle())
                                                        .productImage(variant.getImageUrl() != null
                                                                ? variant.getImageUrl() : product.getImage())
                                                        .variantSize(variant.getSize())
                                                        .variantColorName(variant.getColorName())
                                                        .variantColorHex(variant.getColorHex())
                                                        .build();
                            existingItems.put(key, newItem);
                            toSave.add(newItem);
                        }
                    }
                    case REMOVE ->{
                        if(existing == null){
                            throw new ResourceNotFoundException("Order line not found for product "
                                    + productId + " variant " + variant.getId());
                        }
                        returnStock(variant.getId(), productId, existing.getQuantity(), touchedProducts);
                        toDelete.add(existing);
                        existingItems.remove(key);
                    }
                    case INCREMENT ->{
                        if(existing == null){
                            throw new ResourceNotFoundException("Order line not found for product "
                                    + productId + " variant " + variant.getId());
                        }
                        decrementOrThrow(product, variant, 1);
                        touchedProducts.add(productId);
                        existing.setQuantity(existing.getQuantity() + 1);
                        toSave.add(existing);
                    }
                    case DECREMENT -> {
                        if(existing == null){
                            throw new ResourceNotFoundException("Order line not found for product "
                                    + productId + " variant " + variant.getId());
                        }
                        returnStock(variant.getId(), productId, 1, touchedProducts);
                        if(existing.getQuantity() <= 1){
                            toDelete.add(existing);
                            existingItems.remove(key);
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
            resyncProductStock(touchedProducts);
        }

        if (requestedStatus != null) {
            applyStatusChange(order, requestedStatus);
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

    private void decrementOrThrow(Product product, ProductVariant variant, int qty) {
        if (variantRepository.decrementStock(variant.getId(), qty) == 0) {
            throw new InvalidRequestException(
                    "Insufficient stock for " + product.getTitle() + " in " + variant.getSize()
                            + " / " + variant.getColorName() + ": requested " + qty + " but not enough available");
        }
    }

    /**
     * Returns reserved stock. Atomic bulk UPDATE so two concurrent returns of the same line
     * cannot both read the old count and clobber each other (lost update). The
     * @SQLDelete version-mismatch that used to break the order soft-delete has been fixed,
     * so a bulk statement can now safely share the transaction with the versioned delete.
     *
     * <p>{@code variantId} is null only on order lines written before V20 backfilled them;
     * those still hold stock at product level, so the legacy counter is what gets returned.
     */
    private void returnStock(Long variantId, Long productId, int qty, Set<Long> touchedProducts) {
        if (variantId == null) {
            productRepository.increaseStock(productId, qty);
            touchedProducts.add(productId);
            return;
        }
        variantRepository.increaseStock(variantId, qty);
        touchedProducts.add(productId);
    }

    /**
     * Variants are the source of truth for stock; {@code products.stock} is a legacy
     * aggregate that the admin low-stock report and old readers still use. Recomputed once
     * per touched product instead of per line, and after every stock mutation in the
     * transaction, so it can never be left holding a half-applied delta.
     */
    private void resyncProductStock(Set<Long> productIds) {
        for (Long productId : productIds) {
            productRepository.setStock(productId, (int) variantRepository.sumStockByProductId(productId));
        }
    }

    private static String lineKey(OrderProducts line) {
        return lineKey(line.getProductId(), line.getVariantId());
    }

    private static String lineKey(Long productId, Long variantId) {
        return productId + ":" + variantId;
    }

    /**
     * A shopper owns only the pre-payment phase: status is fulfilment state, and a paid
     * order's lines are what the money bought. Both are left to an administrator, otherwise
     * a buyer could mark their own parcel delivered or add goods after paying for less.
     */
    private void assertMayMutate(Order order, UpdateOrderRequestDto requestDto,
            Long callerId, boolean callerIsAdmin) {
        if (callerId == null) {
            throw new ForbiddenException("You can only modify your own orders");
        }
        boolean owner = order.getUser() != null && callerId.equals(order.getUser().getId());
        if (!callerIsAdmin && !owner) {
            throw new ForbiddenException("You can only modify your own orders");
        }
        if (callerIsAdmin) {
            return;
        }
        if (requestDto.getStatus() != null && requestDto.getStatus() != order.getStatus()) {
            throw new ForbiddenException("Only an administrator can change an order's status");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new ForbiddenException("A paid order can no longer be edited");
        }
    }

    /**
     * Fulfilment moves forward only. Cancellation returns the reserved stock exactly once —
     * the order then becomes terminal, so a repeated cancel cannot restock it twice.
     */
    private void applyStatusChange(Order order, OrderStatus next) {
        OrderStatus current = order.getStatus();
        if (current == next) {
            return;
        }
        boolean allowed = (current == OrderStatus.PENDING
                    && (next == OrderStatus.SHIPPED || next == OrderStatus.CANCELLED))
                || (current == OrderStatus.SHIPPED && next == OrderStatus.DELIVERED);
        if (!allowed) {
            throw new InvalidRequestException(
                    "Order " + order.getId() + " cannot move from " + current + " to " + next);
        }
        if (next == OrderStatus.CANCELLED) {
            if (order.getPaymentStatus() == PaymentStatus.PAID) {
                // Cancelling would restock goods the customer has already paid for. Until a
                // refund flow exists, the money must be returned first.
                throw new InvalidRequestException(
                        "Order " + order.getId() + " has been paid; refund it before cancelling");
            }
            restockOrder(order.getId());
        }
        order.setStatus(next);
        orderRepository.save(order);
    }

    /**
     * Scalar projection instead of managed line items: an OrderProducts still in the
     * persistence context when the Order is soft-deleted makes the commit cascade
     * validation see the removed Order as transient and throw.
     */
    private void restockOrder(Long orderId) {
        Set<Long> touchedProducts = new HashSet<>();
        for (Object[] row : orderProductsRepository.findStockDeltasByOrderId(orderId)) {
            returnStock((Long) row[0], (Long) row[1], (Integer) row[2], touchedProducts);
        }
        resyncProductStock(touchedProducts);
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
     * Deleting restocks and hides the order, so a captured payment must have been refunded
     * first - otherwise the money is gone from every report while the goods went back on the
     * shelf.
     */
    private void assertDeletable(Order order) {
        assertModifiable(order);
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new InvalidRequestException(
                    "Order " + order.getId() + " has been paid; refund it before deleting");
        }
    }

    /**
     * The order keeps its own copy: the address book entry it pointed at may be edited or
     * deleted later, and neither may change where a placed order was sent.
     */
    private AddressSnapshot snapshotOf(Long addressId, Long userId) {
        if (addressId == null) {
            return null;
        }
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address with id: " + addressId + " Not Found!"));
        if (!userId.equals(address.getUserId())) {
            throw new ForbiddenException("Address " + addressId + " does not belong to user " + userId);
        }
        return AddressSnapshot.from(address);
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
