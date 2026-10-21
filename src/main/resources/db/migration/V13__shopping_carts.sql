-- Persistent shopping carts, one live cart per user. Checkout converts the cart into an
-- Order (reusing all stock/snapshot logic) and clears it. Line items are the cart-only
-- state, not order snapshots; they always reflect the current catalogue at checkout time.
CREATE TABLE carts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    created_datetime DATETIME(6) NOT NULL,
    updated_datetime DATETIME(6) NULL,
    deleted_datetime DATETIME(6) NULL,
    -- Uniqueness applies only to live rows so a soft-deleted cart frees the slot.
    live_user_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN deleted_datetime IS NULL THEN user_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_carts_live_user (live_user_id),
    KEY idx_carts_user (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cart_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    created_datetime DATETIME(6) NOT NULL,
    updated_datetime DATETIME(6) NULL,
    deleted_datetime DATETIME(6) NULL,
    -- Live (cart_id, product_id) pairs are unique; deleted rows don't consume the slot.
    live_pair VARCHAR(64) GENERATED ALWAYS AS
        (CASE WHEN deleted_datetime IS NULL THEN CONCAT(cart_id, '-', product_id) ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_items_live_pair (live_pair),
    KEY idx_cart_items_cart (cart_id),
    KEY idx_cart_items_product (product_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id),
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
