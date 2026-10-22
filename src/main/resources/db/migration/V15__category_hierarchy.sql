-- Add parent/self-reference for a two-level category tree (Men -> Tees, Women -> Denim, etc).
-- Existing flat categories become top-level rows (parent_id = NULL).
-- Description and slug round out what the storefront nav needs.
--
-- V8 enforced a globally-unique lowercase category name. That was correct while the tree was
-- flat; with hierarchy, two parents legitimately need "Denim" or "Knitwear" underneath them.
-- Replace with a parent-scoped live uniqueness.

ALTER TABLE categories DROP INDEX uk_categories_active_name;
ALTER TABLE categories DROP COLUMN active_name;

ALTER TABLE categories
    ADD COLUMN parent_id BIGINT NULL AFTER name,
    ADD COLUMN description VARCHAR(500) NULL AFTER parent_id,
    ADD COLUMN slug VARCHAR(80) NULL AFTER description;

ALTER TABLE categories
    ADD CONSTRAINT fk_categories_parent
        FOREIGN KEY (parent_id) REFERENCES categories (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_categories_parent ON categories (parent_id);
CREATE INDEX idx_categories_slug ON categories (slug);

-- Backfill slugs from existing names so every live row has one before we enforce
-- uniqueness. Appending the id guarantees collisions are impossible without
-- hand-editing legacy rows.
UPDATE categories
SET slug = CONCAT(
    LOWER(REGEXP_REPLACE(name, '[^a-zA-Z0-9]+', '-')),
    '-', id
)
WHERE slug IS NULL;

-- Live-row unique slug via a generated column (mirrors the V8 pattern for names).
ALTER TABLE categories
    ADD COLUMN live_slug VARCHAR(80) GENERATED ALWAYS AS
        (CASE WHEN deleted_datetime IS NULL THEN slug ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_categories_live_slug (live_slug);

-- Parent-scoped name uniqueness across live rows. Top-level categories use parent 0
-- so two subcategories under different parents never collide, and 'Men' at top level
-- doesn't clash with 'Men/Denim' either.
ALTER TABLE categories
    ADD COLUMN live_name_in_parent VARCHAR(160) GENERATED ALWAYS AS
        (CASE WHEN deleted_datetime IS NULL
              THEN CONCAT(COALESCE(parent_id, 0), '|', LOWER(name))
              ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_categories_live_name_in_parent (live_name_in_parent);
