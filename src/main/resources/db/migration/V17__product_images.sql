-- Ordered product gallery. Every product has at least one image, position 0 is the
-- default/thumbnail. V18's `products.image` column will remain in place for backward
-- compatibility with existing order snapshots; this table is the source of truth for
-- the storefront gallery going forward.

CREATE TABLE product_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    url VARCHAR(255) NOT NULL,
    position INT NOT NULL DEFAULT 0,
    alt VARCHAR(255) NULL,
    created_datetime DATETIME(6) NOT NULL,
    updated_datetime DATETIME(6) NULL,
    deleted_datetime DATETIME(6) NULL,
    live_position VARCHAR(80) GENERATED ALWAYS AS
        (IF(deleted_datetime IS NULL, CONCAT(product_id, '|', position), NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pi_live_position (live_position),
    KEY idx_pi_product_position (product_id, position),
    CONSTRAINT fk_pi_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Backfill: any product that already has products.image gets a position-0 gallery row.
INSERT INTO product_images (product_id, url, position, created_datetime)
SELECT p.id, p.image, 0, NOW(6)
FROM products p
WHERE p.image IS NOT NULL AND p.image <> '';
