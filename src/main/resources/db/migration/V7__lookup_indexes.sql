-- Hibernate renders *IgnoreCase derived queries as LOWER(col) = LOWER(?), which a plain
-- b-tree index on the column cannot serve, so every one of these lookups was a full scan.
-- MySQL functional indexes on the same expression let the optimizer use them instead,
-- and keeps the case-insensitive behaviour intact on both MySQL and the H2 test schema.

CREATE INDEX idx_categories_name_lower ON categories ((LOWER(name)));

CREATE INDEX idx_products_title_lower ON products ((LOWER(title)));

-- Serves existsByOrder_IdAndProduct_Id and findByOrderId; its leftmost prefix replaces
-- the single-column FK index MySQL auto-creates from V3.
CREATE INDEX idx_reviews_order_product ON reviews (order_id, product_id);

-- No query filters or sorts on price; it only cost writes on a hot insert path.

-- Conditional drops. MySQL 8.4 already consumes the FK-supporting index when a compatible
-- composite index is added earlier in this same migration, so an unconditional
-- `DROP INDEX fk_review_order` fails on a fresh database. Guard every drop against
-- information_schema so the migration is idempotent across both cases: dev databases that
-- pre-date the composite index, and CI databases built from V1 onward in one pass.
DROP PROCEDURE IF EXISTS drop_index_if_present;
DELIMITER //
CREATE PROCEDURE drop_index_if_present(IN p_table VARCHAR(64), IN p_index VARCHAR(64))
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME   = p_table
                 AND INDEX_NAME   = p_index) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` DROP INDEX `', p_index, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;
CALL drop_index_if_present('reviews',  'fk_review_order');
CALL drop_index_if_present('products', 'idx_product_price');
DROP PROCEDURE drop_index_if_present;
