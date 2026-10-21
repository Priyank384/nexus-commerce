-- Hibernate renders *IgnoreCase derived queries as LOWER(col) = LOWER(?), which a plain
-- b-tree index on the column cannot serve, so every one of these lookups was a full scan.
-- MySQL functional indexes on the same expression let the optimizer use them instead,
-- and keeps the case-insensitive behaviour intact on both MySQL and the H2 test schema.

CREATE INDEX idx_categories_name_lower ON categories ((LOWER(name)));

CREATE INDEX idx_products_title_lower ON products ((LOWER(title)));

-- Serves existsByOrder_IdAndProduct_Id and findByOrderId; its leftmost prefix replaces
-- the single-column FK index below.
CREATE INDEX idx_reviews_order_product ON reviews (order_id, product_id);
DROP INDEX fk_review_order ON reviews;

-- No query filters or sorts on price; it only cost writes on a hot insert path.
DROP INDEX idx_product_price ON products;
