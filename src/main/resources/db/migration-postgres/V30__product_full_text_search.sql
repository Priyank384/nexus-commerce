-- The LIKE path is what a query with an explicit sort still runs on PostgreSQL, and a pattern
-- with a leading wildcard is exactly what a b-tree cannot serve. A trigram index can, because
-- '%tee%' contains the trigram 'tee' and the index can answer on that alone.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Relevance-ranked catalogue search.
--
-- Every storefront keyword query until now was a chain of LOWER(col) LIKE '%word%' tests,
-- which cannot rank (every match scores the same, so "shirt" returns the lowest-id shirt,
-- not the most shirt-like one), cannot stem ('tees' misses 'tee'), and cannot use an index
-- for a pattern with a leading wildcard. A stored tsvector gives the query planner
-- something to match against with a GIN index and something to score with ts_rank.
--
-- The column is a generation expression rather than a trigger because the text it indexes is
-- exactly the text the row already holds: title, brand, material, care and description are
-- written by the ordinary product UPDATE, and the index follows without any code that could
-- forget to keep it in step. Category name is deliberately absent -- a generation expression
-- can only read its own row -- so the query keeps one LIKE branch against the joined category
-- to preserve "search the collection by its name".
--
-- Weights are the storefront's intuition about a product card: a word in the title says more
-- than a word buried in the care instructions.
ALTER TABLE products ADD COLUMN search_text tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(brand, '')), 'B') ||
        setweight(to_tsvector('english',
            trim(coalesce(material, '') || ' ' || coalesce(care_instructions, ''))), 'C') ||
        setweight(to_tsvector('english', coalesce(description, '')), 'D')
    ) STORED;

-- Partial, matching how the catalogue is actually read: a draft or a retired product is never
-- a search result, so indexing its text is write cost for nothing.
CREATE INDEX idx_products_search_text_gin ON products USING gin (search_text)
    WHERE deleted_datetime IS NULL AND is_active = TRUE;

CREATE INDEX idx_products_title_trgm ON products USING gin (lower(title) gin_trgm_ops)
    WHERE deleted_datetime IS NULL AND is_active = TRUE;
