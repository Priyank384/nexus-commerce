-- V20 added cart_items.variant_id but left it nullable and kept the product-scoped live
-- uniqueness, because a cart that still adds by product id could not survive either change.
-- CartService now resolves a variant on every add, so this completes the swap:
--   1. every live line gets a variant,
--   2. variant_id becomes NOT NULL,
--   3. live uniqueness moves from (cart, product) to (cart, variant), which is what lets a
--      shopper hold a Medium and a Large of the same shirt at the same time.
--
-- Lines that cannot be resolved are removed rather than left ambiguous. A cart is live state,
-- not purchase history, so there is nothing to preserve: keeping a line with no size+colour
-- would let it reach checkout and be ordered as an unspecified garment.

-- Products with exactly one variant (every row V16 backfilled to LEGACY-*) resolve for free.
UPDATE cart_items ci
SET variant_id = pv.id
FROM product_variants pv
WHERE pv.product_id = ci.product_id
  AND pv.deleted_datetime IS NULL
  AND ci.variant_id IS NULL
  AND ci.deleted_datetime IS NULL
  AND (SELECT COUNT(*) FROM product_variants pv2
           WHERE pv2.product_id = ci.product_id AND pv2.deleted_datetime IS NULL) = 1;

DELETE FROM cart_items WHERE variant_id IS NULL;

ALTER TABLE cart_items
    ALTER COLUMN variant_id SET NOT NULL;

-- The generated column and its unique index are dropped before the replacement is added:
-- Postgres requires a generated column and its index to live in separate statements, and
-- the old index would otherwise keep referencing a column that no longer exists.
DROP INDEX uk_cart_items_live_pair;
ALTER TABLE cart_items DROP COLUMN live_pair;

-- || over textcasts rather than concat(): concat() is STABLE and cannot back a
-- generated column. NULLs stay distinct, so soft-deleted lines never consume the slot.
ALTER TABLE cart_items
    ADD COLUMN live_variant_pair VARCHAR(64) GENERATED ALWAYS AS
        (CASE WHEN deleted_datetime IS NULL THEN (cart_id::text || '-' || variant_id::text) ELSE NULL END) STORED;

CREATE UNIQUE INDEX uk_cart_items_live_variant ON cart_items (live_variant_pair);

-- No index is built on variant_id here: V20 already created idx_cart_items_variant.
