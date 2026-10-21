-- Optimistic locking for orders: concurrent PUT /orders/{id} calls previously did a
-- read-modify-write with no protection, so one update could silently overwrite another.
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- The services checked "exists then save", which races: two concurrent creates could both
-- pass the check. The DB is the only place that can enforce this. The index is built on a
-- generated column so it covers only live rows, which keeps soft-delete/recreate working.
ALTER TABLE products
    ADD COLUMN active_title VARCHAR(255)
        GENERATED ALWAYS AS (IF(deleted_datetime IS NULL, LOWER(title), NULL)) STORED,
    ADD UNIQUE KEY uk_products_active_title (active_title);

ALTER TABLE categories
    ADD COLUMN active_name VARCHAR(255)
        GENERATED ALWAYS AS (IF(deleted_datetime IS NULL, LOWER(name), NULL)) STORED,
    ADD UNIQUE KEY uk_categories_active_name (active_name);
