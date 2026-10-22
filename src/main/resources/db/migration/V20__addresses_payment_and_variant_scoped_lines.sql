-- Checkout needs shipping/billing addresses and a payment reference. Historic orders
-- keep their existing snapshot columns; new orders also snapshot the address as JSON
-- so it never mutates when the user later edits their saved addresses.
--
-- Also: cart items and order line items move from product-scoped to variant-scoped,
-- which is the whole reason variants exist for apparel.

CREATE TABLE addresses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    label VARCHAR(60) NOT NULL DEFAULT 'Home',
    recipient_name VARCHAR(120) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    line1 VARCHAR(255) NOT NULL,
    line2 VARCHAR(255) NULL,
    city VARCHAR(80) NOT NULL,
    state VARCHAR(80) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(2) NOT NULL DEFAULT 'IN',
    is_default_shipping BOOLEAN NOT NULL DEFAULT FALSE,
    is_default_billing BOOLEAN NOT NULL DEFAULT FALSE,
    created_datetime DATETIME(6) NOT NULL,
    updated_datetime DATETIME(6) NULL,
    deleted_datetime DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_addr_user (user_id),
    KEY idx_addr_user_default (user_id, is_default_shipping),
    CONSTRAINT fk_addr_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Order-level payment + address snapshot. Kept NULL-able so historic orders (V1-V12 era)
-- remain readable through the same DTO.
ALTER TABLE orders
    ADD COLUMN address_snapshot JSON NULL,
    ADD COLUMN payment_provider VARCHAR(30) NULL,
    ADD COLUMN payment_ref VARCHAR(120) NULL,
    ADD COLUMN payment_status VARCHAR(20) NULL;

CREATE INDEX idx_orders_payment_ref ON orders (payment_ref);
CREATE INDEX idx_orders_payment_status ON orders (payment_status);

-- Line items snapshot the variant identity at purchase time, matching V11/V12's
-- "immutable after sale" philosophy. variant_id itself is a nullable FK to allow
-- historic rows to keep working before the backfill below runs.
ALTER TABLE order_products
    ADD COLUMN variant_id BIGINT NULL AFTER product_id,
    ADD COLUMN variant_size VARCHAR(20) NULL AFTER variant_id,
    ADD COLUMN variant_color_name VARCHAR(60) NULL AFTER variant_size,
    ADD COLUMN variant_color_hex VARCHAR(9) NULL AFTER variant_color_name;

-- Cart items also become variant-aware. Add nullable variant_id and backfill from the
-- LEGACY-* variants V16 created. Full NOT NULL enforcement and the (cart, variant) live
-- uniqueness swap are deferred to V22 once Phase 4's variant-aware CartService ships --
-- leaving the old (cart, product) uniqueness intact keeps the current service working
-- against these columns today.
ALTER TABLE cart_items
    ADD COLUMN variant_id BIGINT NULL AFTER product_id;

UPDATE cart_items ci
    JOIN product_variants pv
        ON pv.product_id = ci.product_id
       AND pv.size = 'ONE'
       AND pv.color_name = 'Default'
SET ci.variant_id = pv.id
WHERE ci.variant_id IS NULL;

ALTER TABLE cart_items
    ADD CONSTRAINT fk_cart_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    ADD KEY idx_cart_items_variant (variant_id);

-- Backfill variant columns on historic order line items so they render cleanly.
UPDATE order_products op
    JOIN product_variants pv
        ON pv.product_id = op.product_id
       AND pv.size = 'ONE'
       AND pv.color_name = 'Default'
SET op.variant_id = pv.id,
    op.variant_size = pv.size,
    op.variant_color_name = pv.color_name,
    op.variant_color_hex = pv.color_hex
WHERE op.variant_id IS NULL;

ALTER TABLE order_products
    ADD CONSTRAINT fk_op_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    ADD KEY idx_op_variant (variant_id);
