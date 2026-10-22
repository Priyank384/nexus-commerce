-- Optimistic locking for orders: concurrent PUT /orders/{id} calls previously did a
-- read-modify-write with no protection, so one update could silently overwrite another.
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- The services checked "exists then save", which races: two concurrent creates could both
-- pass the check. The DB is the only place that can enforce this. The index is built on a
-- generated column so it covers only live rows, which keeps soft-delete/recreate working
-- (PostgreSQL, like MySQL, treats NULLs as distinct in a unique index).
--
-- Split into ADD COLUMN then ADD CONSTRAINT: PostgreSQL will not let a single ALTER TABLE
-- add a column and also reference it from a new table constraint.
ALTER TABLE products
    ADD COLUMN active_title VARCHAR(255)
        GENERATED ALWAYS AS (CASE WHEN deleted_datetime IS NULL THEN LOWER(title) ELSE NULL END) STORED;

ALTER TABLE products
    ADD CONSTRAINT uk_products_active_title UNIQUE (active_title);

ALTER TABLE categories
    ADD COLUMN active_name VARCHAR(255)
        GENERATED ALWAYS AS (CASE WHEN deleted_datetime IS NULL THEN LOWER(name) ELSE NULL END) STORED;

ALTER TABLE categories
    ADD CONSTRAINT uk_categories_active_name UNIQUE (active_name);
