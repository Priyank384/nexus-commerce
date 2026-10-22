-- Revenue and past order totals were computed from the product's LIVE price, so any admin
-- price edit silently rewrote history (and a deleted product could break old order reads).
-- Snapshot the paid price onto each line item, then backfill existing rows from the catalogue.
--
-- PostgreSQL has no column-position syntax, so the MySQL `AFTER quantity` clause is dropped;
-- the value is what matters, not the ordinal position.
ALTER TABLE order_products
    ADD COLUMN unit_price NUMERIC(38, 2) NOT NULL DEFAULT 0;

-- Multi-table UPDATE translates from MySQL's `UPDATE a JOIN b ON ... SET ...` to the
-- `UPDATE a SET ... FROM b WHERE ...` form.
UPDATE order_products op
    SET unit_price = p.price
    FROM products p
    WHERE op.product_id = p.id;
