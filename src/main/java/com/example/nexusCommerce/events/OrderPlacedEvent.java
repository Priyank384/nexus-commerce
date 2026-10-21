package com.example.nexusCommerce.events;

import java.time.Instant;

/**
 * Emitted inside OrderServiceImpl AFTER a new order has been committed. Consumed by
 * AFTER_COMMIT listeners for cache eviction, notification and integration fan-out.
 */
public record OrderPlacedEvent(Long orderId, Long userId, Instant placedAt) {}
