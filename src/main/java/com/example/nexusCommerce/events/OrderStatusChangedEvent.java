package com.example.nexusCommerce.events;

import com.example.nexusCommerce.schema.OrderStatus;

public record OrderStatusChangedEvent(Long orderId, Long userId, OrderStatus previous, OrderStatus current) {}
