-- Foreign keys do not index the referencing side in PostgreSQL, and MySQL's automatic
-- FK indexes do not exist here. Every order lookup below was therefore a sequential scan:
--
--   orders(user_id)          GET /orders (shopper history, paged by user)
--   order_products(order_id) total per order, checkout stock deltas, soft-delete by order,
--                            existsByOrderIdAndProduct_Id (composite leftmost prefix)
--
-- deleted_at stays out of the index predicate: the @SQLRestriction form is
-- "deleted_datetime is NULL", and a plain b-tree still serves it while also supporting
-- admin/audit queries that look at every row.
CREATE INDEX IF NOT EXISTS idx_orders_user ON orders (user_id);

CREATE INDEX IF NOT EXISTS idx_order_products_order_product
    ON order_products (order_id, product_id);
