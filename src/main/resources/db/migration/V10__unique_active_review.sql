-- createReview did "exists-check then insert", so two concurrent submissions for the same
-- (order, product) could both pass the service check and both persist a duplicate review.
-- The DB is the only place that can close that race. The index is built on a generated
-- column so it covers only live rows, which keeps soft-delete/recreate working.
ALTER TABLE reviews
    ADD COLUMN active_order_product VARCHAR(64)
        GENERATED ALWAYS AS (IF(deleted_datetime IS NULL, CONCAT(order_id, ':', product_id), NULL)) STORED,
    ADD UNIQUE KEY uk_reviews_active_order_product (active_order_product);
