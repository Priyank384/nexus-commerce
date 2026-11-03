-- Guest checkout: a cart or an order may belong to a browser session instead of an account.
--
-- Both identity columns become nullable rather than gaining a sentinel row in users: a guest
-- is not a user, and inventing one would put fake rows into every join, report and admin
-- screen that walks users.
--
-- Cart uniqueness keeps working per-identity because live_user_id evaluates to NULL for a
-- guest cart (a guest row has no user_id), and NULLs never collide in a unique index. The new
-- generated column is its mirror for the other half of the split: only live guest carts get a
-- slot, so a soft-deleted cart frees the session for a fresh cart.
ALTER TABLE carts
    MODIFY COLUMN user_id BIGINT NULL;

ALTER TABLE carts
    ADD COLUMN guest_token VARCHAR(64) NULL AFTER user_id;

ALTER TABLE carts
    ADD COLUMN live_guest_token VARCHAR(64) GENERATED ALWAYS AS
        (CASE WHEN deleted_datetime IS NULL AND user_id IS NULL THEN guest_token ELSE NULL END) STORED;

ALTER TABLE carts
    ADD UNIQUE KEY uk_carts_live_guest_token (live_guest_token);

-- Lookups run against the real column; the generated one only exists to scope uniqueness.
ALTER TABLE carts
    ADD KEY idx_carts_guest_token (guest_token);

ALTER TABLE orders
    MODIFY COLUMN user_id BIGINT NULL;

-- The address the receipt goes to. Kept separate from address_snapshot because the snapshot
-- names a recipient for delivery, and neither it nor the shipping phone is necessarily where
-- confirmation mail should go.
ALTER TABLE orders
    ADD COLUMN guest_email VARCHAR(255) NULL;

-- The buyer's session. Grants the same request that placed the order permission to price,
-- verify and poll its payment, which is what lets a guest finish Razorpay without a token.
ALTER TABLE orders
    ADD COLUMN guest_token VARCHAR(64) NULL;

-- A second, single-order secret, mailed inside the confirmation link. Kept apart from
-- guest_token on purpose: the session token also unlocks that shopper's cart and their other
-- orders, so it must never travel in an email that may be forwarded or read on another device.
ALTER TABLE orders
    ADD COLUMN claim_token VARCHAR(64) NULL;

ALTER TABLE orders
    ADD UNIQUE KEY uk_orders_claim_token (claim_token);

ALTER TABLE orders
    ADD KEY idx_orders_guest_token (guest_token);
