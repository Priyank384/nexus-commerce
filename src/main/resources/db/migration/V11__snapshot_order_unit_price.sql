-- Revenue and past order totals were computed from the product's LIVE price, so any admin
-- price edit silently rewrote history (and a deleted product could break old order reads).
-- Snapshot the paid price onto each line item, then backfill existing rows from the catalogue.
ALTER TABLE order_products
    ADD COLUMN unit_price DECIMAL(38, 2) NOT NULL DEFAULT 0 AFTER quantity;

UPDATE order_products op
    JOIN products p ON op.product_id = p.id
    SET op.unit_price = p.price;
