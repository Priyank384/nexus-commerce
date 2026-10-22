-- Add parent/self-reference for a two-level category tree (Men -> Tees, Women -> Denim, etc).
-- Existing flat categories become top-level rows (parent_id = NULL).
-- Description and slug round out what the storefront nav needs.
--
-- V8 enforced a globally-unique lowercase category name. That was correct while the tree was
-- flat; with hierarchy, two parents legitimately need "Denim" or "Knitwear" underneath them.
-- Replace with a parent-scoped live uniqueness.
--
-- PostgreSQL has no AFTER-position clause and cannot add a column and constrain it in the
-- same ALTER TABLE, so each generated column is followed by its own unique index.

ALTER TABLE categories DROP CONSTRAINT uk_categories_active_name;
ALTER TABLE categories DROP COLUMN active_name;

ALTER TABLE categories
    ADD COLUMN parent_id BIGINT NULL,
    ADD COLUMN description VARCHAR(500) NULL,
    ADD COLUMN slug VARCHAR(80) NULL;

ALTER TABLE categories
    ADD CONSTRAINT fk_categories_parent
        FOREIGN KEY (parent_id) REFERENCES categories (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_categories_parent ON categories (parent_id);
CREATE INDEX idx_categories_slug ON categories (slug);

-- Backfill slugs from existing names so every live row has one before we enforce
-- uniqueness. Appending the id guarantees collisions are impossible without
-- hand-editing legacy rows.
--
-- The 'g' flag is deliberate: MySQL's REGEXP_REPLACE defaults to a single replacement, so
-- names with two runs of punctuation would keep the second one here but not there.
UPDATE categories
SET slug = CONCAT(
    LOWER(REGEXP_REPLACE(name, '[^a-zA-Z0-9]+', '-', 'g')),
    '-', id
)
WHERE slug IS NULL;

-- Live-row unique slug via a generated column (mirrors the V8 pattern for names).
ALTER TABLE categories
    ADD COLUMN live_slug VARCHAR(80) GENERATED ALWAYS AS
        (CASE WHEN deleted_datetime IS NULL THEN slug ELSE NULL END) STORED;

CREATE UNIQUE INDEX uk_categories_live_slug ON categories (live_slug);

-- Parent-scoped name uniqueness across live rows. Top-level categories use parent 0
-- so two subcategories under different parents never collide, and 'Men' at top level
-- doesn't clash with 'Men/Denim' either.
-- || over a textcast instead of concat(): concat() is STABLE, which a generated column
-- rejects.
ALTER TABLE categories
    ADD COLUMN live_name_in_parent VARCHAR(160) GENERATED ALWAYS AS
        (CASE WHEN deleted_datetime IS NULL
              THEN (COALESCE(parent_id, 0)::text || '|' || LOWER(name))
              ELSE NULL END) STORED;

CREATE UNIQUE INDEX uk_categories_live_name_in_parent ON categories (live_name_in_parent);
