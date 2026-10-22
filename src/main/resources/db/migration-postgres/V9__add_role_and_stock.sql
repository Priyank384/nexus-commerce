-- Role-based access: the application now enforces JWT authentication and an ADMIN role
-- for catalogue/order/user management. Existing users default to USER; id 1 is promoted to
-- ADMIN so there is a bootstrap administrator (reassign later via the admin API).
ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- Soft enable/disable for user management without deleting the row.
ALTER TABLE users
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE users SET role = 'ADMIN' WHERE id = 1;

-- Inventory: products carry a stock count, decremented atomically when an order is placed.
-- Existing rows are backfilled to 100 so the demo catalogue stays orderable.
ALTER TABLE products
    ADD COLUMN stock INT NOT NULL DEFAULT 0;

UPDATE products SET stock = 100 WHERE stock = 0;
