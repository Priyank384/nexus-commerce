-- Wishlists: one default per user, plus optional named lists (e.g. "Wedding gifts").
-- Live rows enforce (user, name) uniqueness via a generated column so a soft-deleted
-- list frees the name slot.

CREATE TABLE wishlists (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL DEFAULT 'Wishlist',
    created_datetime DATETIME(6) NOT NULL,
    updated_datetime DATETIME(6) NULL,
    deleted_datetime DATETIME(6) NULL,
    live_user_name VARCHAR(160) GENERATED ALWAYS AS
        (IF(deleted_datetime IS NULL, CONCAT(user_id, '|', LOWER(name)), NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wl_live_user_name (live_user_name),
    KEY idx_wl_user (user_id),
    CONSTRAINT fk_wl_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE wishlist_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    wishlist_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    variant_id BIGINT NULL,
    created_datetime DATETIME(6) NOT NULL,
    updated_datetime DATETIME(6) NULL,
    deleted_datetime DATETIME(6) NULL,
    live_pair VARCHAR(80) GENERATED ALWAYS AS
        (IF(deleted_datetime IS NULL, CONCAT(wishlist_id, '-', product_id), NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wli_live_pair (live_pair),
    KEY idx_wli_wishlist (wishlist_id),
    KEY idx_wli_product (product_id),
    KEY idx_wli_variant (variant_id),
    CONSTRAINT fk_wli_wishlist FOREIGN KEY (wishlist_id) REFERENCES wishlists (id),
    CONSTRAINT fk_wli_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_wli_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
