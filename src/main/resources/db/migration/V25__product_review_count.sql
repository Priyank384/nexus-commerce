-- The catalogue stores one rating per product, but the storefront now needs the number of
-- reviews that rating rests on. Counting them per row would add an aggregate to every listing
-- query, so the count is denormalised beside the rating and both are recomputed when a review
-- is written or removed. Seeded styles start at zero: their curated rating is shown without a
-- count until a real review lands.
ALTER TABLE products
    ADD COLUMN review_count INT NOT NULL DEFAULT 0;
