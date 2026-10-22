-- Hibernate renders *IgnoreCase derived queries as LOWER(col) = LOWER(?), which a plain
-- b-tree index on the column cannot serve, so every one of these lookups was a full scan.
-- Expression indexes on the same expression let the planner use them instead.

CREATE INDEX idx_categories_name_lower ON categories ((LOWER(name)));

CREATE INDEX idx_products_title_lower ON products ((LOWER(title)));

CREATE INDEX idx_reviews_order_product ON reviews (order_id, product_id);

-- No query filters or sorts on price; it only cost writes on a hot insert path.
DROP INDEX IF EXISTS idx_product_price;

-- V7 on MySQL needed a stored-procedure guard to drop the FK-supporting index MySQL
-- auto-creates on reviews(order_id). PostgreSQL does not index the referencing side of an
-- foreign key at all, so there is nothing to drop here -- the composite above is the only
-- index reviews(order_id) gets. That is intentional and matches what MySQL ended up with.
