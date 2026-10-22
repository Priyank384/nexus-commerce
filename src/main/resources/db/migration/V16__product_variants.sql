-- Apparel variants: every product becomes a family of size+color SKUs with independent
-- stock and price delta. Historic products without variants get a synthetic 'ONE / Default'
-- row so their existing orders, cart lines, and wishlist entries can point at a real variant.
--
-- Soft-delete-safe uniqueness uses the same generated-column trick as V8/V13.

CREATE TABLE product_variants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    sku VARCHAR(64) NOT NULL,
    size VARCHAR(20) NOT NULL,
    color_name VARCHAR(60) NOT NULL,
    color_hex VARCHAR(9) NOT NULL,
    price_delta DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    image_url VARCHAR(255) NULL,
    stock INT NOT NULL DEFAULT 0,
    created_datetime DATETIME(6) NOT NULL,
    updated_datetime DATETIME(6) NULL,
    deleted_datetime DATETIME(6) NULL,
    live_sku VARCHAR(64) GENERATED ALWAYS AS
        (IF(deleted_datetime IS NULL, sku, NULL)) STORED,
    live_product_option VARCHAR(120) GENERATED ALWAYS AS
        (IF(deleted_datetime IS NULL,
            CONCAT(product_id, '|', LOWER(size), '|', LOWER(color_name)),
            NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pv_live_sku (live_sku),
    UNIQUE KEY uk_pv_live_product_option (live_product_option),
    KEY idx_pv_product (product_id),
    KEY idx_pv_size (size),
    KEY idx_pv_color (color_name),
    CONSTRAINT fk_pv_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Backfill: one 'ONE / Default' variant per existing product, sku = LEGACY-<6-digit id>.
-- Stock mirrors products.stock so V9's inventory semantics carry over untouched.
INSERT INTO product_variants (
    product_id, sku, size, color_name, color_hex,
    price_delta, image_url, stock, created_datetime
)
SELECT
    p.id,
    CONCAT('LEGACY-', LPAD(p.id, 6, '0')),
    'ONE',
    'Default',
    '#0F0F0F',
    0.00,
    p.image,
    COALESCE(p.stock, 0),
    NOW(6)
FROM products p;
