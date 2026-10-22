-- createReview did "exists-check then insert", so two concurrent submissions for the same
-- (order, product) could both pass the service check and both persist a duplicate review.
-- The DB is the only place that can close that race. The index is built on a generated
-- column so it covers only live rows, which keeps soft-delete/recreate working.
--
-- MySQL's CONCAT(a, '-', b) has no direct equivalent here: PostgreSQL's concat() is only
-- STABLE, and a generated column requires an IMMUTABLE expression. The || operator on
-- textcast operands is immutable, so the keys are built with it instead.
ALTER TABLE reviews
    ADD COLUMN active_order_product VARCHAR(64)
        GENERATED ALWAYS AS
            (CASE WHEN deleted_datetime IS NULL THEN (order_id::text || ':' || product_id::text) ELSE NULL END) STORED;

ALTER TABLE reviews
    ADD CONSTRAINT uk_reviews_active_order_product UNIQUE (active_order_product);
