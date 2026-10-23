-- Phase 5 moved reviews to the 1-5 scale the report form speaks. Rows written before it were
-- scored out of ten, so halving them once (at the same precision the service rounds to) is the
-- conversion; without it a product can show a "8.5 out of 5" average.
UPDATE reviews
SET rating = ROUND(rating / 2.0, 1)
WHERE rating > 5;

-- review_count arrived in V25 with a zero default, and the service only refreshes it when a
-- review is written. Live reviews that were already on disk are counted here, and their
-- product's rating becomes the live average, exactly as a new review would have made it.
UPDATE products p
JOIN (
    SELECT product_id,
           COUNT(*) AS live_reviews,
           ROUND(AVG(rating), 1) AS average_rating
    FROM reviews
    WHERE deleted_datetime IS NULL
    GROUP BY product_id
) live ON live.product_id = p.id
SET p.review_count = live.live_reviews,
    p.rating = live.average_rating;
