-- V2: products.rating becomes a 3,1 numeric. PostgreSQL needs an explicit USING cast because
-- varchar -> numeric is not an implicit assignment cast. On a fresh database the table is
-- empty so the cast never runs against real rows.
ALTER TABLE products
    ALTER COLUMN rating TYPE NUMERIC(3, 1) USING rating::numeric,
    ALTER COLUMN rating SET NOT NULL;
