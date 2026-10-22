-- Apparel product metadata: brand, material, care, gender targeting, compare-at price,
-- URL slug, and an explicit isActive flag so admin can hide a product without soft-deleting
-- its order history or reviews. Legacy rows are backfilled to safe defaults.

ALTER TABLE products
    ADD COLUMN brand VARCHAR(120) NULL,
    ADD COLUMN material VARCHAR(255) NULL,
    ADD COLUMN care_instructions VARCHAR(500) NULL,
    ADD COLUMN gender VARCHAR(10) NULL,
    ADD COLUMN compare_at_price NUMERIC(38, 2) NULL,
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN slug VARCHAR(120) NULL;

CREATE INDEX idx_products_brand ON products (brand);
CREATE INDEX idx_products_gender ON products (gender);
CREATE INDEX idx_products_is_active ON products (is_active);

-- Backfill slugs from titles. The id suffix guarantees no collision against V8's
-- active_title uniqueness even if two products share a title.
-- 'g' flag: replace every run of non-alphanumerics, not just the first.
UPDATE products
SET slug = CONCAT(
    LOWER(REGEXP_REPLACE(title, '[^a-zA-Z0-9]+', '-', 'g')),
    '-', id
)
WHERE slug IS NULL;

ALTER TABLE products
    ADD COLUMN live_slug VARCHAR(120) GENERATED ALWAYS AS
        (CASE WHEN deleted_datetime IS NULL THEN slug ELSE NULL END) STORED;

CREATE UNIQUE INDEX uk_products_live_slug ON products (live_slug);
CREATE INDEX idx_products_slug ON products (slug);
