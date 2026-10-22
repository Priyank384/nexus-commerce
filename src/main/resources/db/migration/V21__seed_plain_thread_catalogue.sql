-- Plain Thread catalogue seed.
--
-- Retires the pre-Phase-2 demo catalogue by soft-deleting it (historic orders, reviews
-- and cart rows keep their FK targets), then inserts a two-level apparel category tree,
-- 30 products, a per-product gallery, and a full size/color variant grid.
--
-- IDs are pinned so the variant/gallery INSERT-SELECTs below can reference them without
-- LAST_INSERT_ID() gymnastics. The auto_increment counters naturally resume above 2000.
--
-- Delete prelude makes this migration idempotent across retries: if a previous attempt
-- half-applied it (Flyway's repair removes the failed history row but leaves the
-- committed DML in place), the next attempt starts from a clean slate.
--
-- The order matters because of FK chains: V19/V20 add references from wishlist_items,
-- cart_items, and order_products to product_variants, and V16's fk_pv_product points
-- back at products. Null out / delete dependents bottom-up before dropping the seed rows.

UPDATE order_products
SET variant_id = NULL, variant_size = NULL, variant_color_name = NULL, variant_color_hex = NULL
WHERE product_id BETWEEN 1101 AND 1130;

UPDATE cart_items
SET variant_id = NULL
WHERE product_id BETWEEN 1101 AND 1130;

UPDATE wishlist_items
SET variant_id = NULL
WHERE product_id BETWEEN 1101 AND 1130;

DELETE FROM order_products WHERE product_id IN (SELECT id FROM products WHERE category_id BETWEEN 101 AND 127 OR id BETWEEN 1101 AND 1130);
DELETE FROM cart_items     WHERE product_id IN (SELECT id FROM products WHERE category_id BETWEEN 101 AND 127 OR id BETWEEN 1101 AND 1130);
DELETE FROM reviews        WHERE product_id IN (SELECT id FROM products WHERE category_id BETWEEN 101 AND 127 OR id BETWEEN 1101 AND 1130);
DELETE FROM wishlist_items WHERE product_id IN (SELECT id FROM products WHERE category_id BETWEEN 101 AND 127 OR id BETWEEN 1101 AND 1130);
DELETE FROM product_variants WHERE product_id IN (SELECT id FROM products WHERE category_id BETWEEN 101 AND 127 OR id BETWEEN 1101 AND 1130);
DELETE FROM product_images     WHERE product_id IN (SELECT id FROM products WHERE category_id BETWEEN 101 AND 127 OR id BETWEEN 1101 AND 1130);
DELETE FROM products           WHERE category_id BETWEEN 101 AND 127 OR id BETWEEN 1101 AND 1130;
DELETE FROM categories         WHERE id BETWEEN 101 AND 127;

-- 1. Retire legacy catalogue -------------------------------------------------------
UPDATE products
SET deleted_datetime = NOW(6)
WHERE deleted_datetime IS NULL AND id < 1001;

UPDATE categories
SET deleted_datetime = NOW(6)
WHERE deleted_datetime IS NULL AND id < 101;

-- 2. Category tree -----------------------------------------------------------------
-- Top level: id 101 Men, 102 Women. Subcategories: 111-117 under Men, 121-127 under Women.
INSERT INTO categories (id, name, parent_id, description, slug, created_datetime) VALUES
    (101, 'Men',      NULL, 'Essentials, tailoring, denim and knitwear built to outlast the season.', 'men', NOW(6)),
    (102, 'Women',    NULL, 'Considered wardrobe staples in natural fibres and quiet colours.',        'women', NOW(6)),
    (111, 'T-Shirts', 101, 'Organic cotton crews, tees and polos.',                                     'mens-t-shirts', NOW(6)),
    (112, 'Shirts',   101, 'Oxfords, poplins and camp-collar linens.',                                  'mens-shirts', NOW(6)),
    (113, 'Denim',    101, 'Selvedge, raw and stretch in straight and slim fits.',                      'mens-denim', NOW(6)),
    (114, 'Trousers', 101, 'Chinos, dress trousers and utility cuts.',                                  'mens-trousers', NOW(6)),
    (115, 'Knitwear', 101, 'Merino, lambswool and cashmere.',                                           'mens-knitwear', NOW(6)),
    (116, 'Outerwear',101, 'Blazers, jackets and coats for every temperature.',                         'mens-outerwear', NOW(6)),
    (117, 'Activewear',101,'Performance joggers, hoodies and technical tees.',                          'mens-activewear', NOW(6)),
    (121, 'Dresses',  102, 'Slips, shirtdresses and column cuts.',                                      'womens-dresses', NOW(6)),
    (122, 'Tops',     102, 'Tees, tanks, camisoles and blouses.',                                       'womens-tops', NOW(6)),
    (123, 'Denim',    102, 'Straight, high-rise and wide-leg.',                                         'womens-denim', NOW(6)),
    (124, 'Trousers', 102, 'Wide-leg, ankle and tailored.',                                             'womens-trousers', NOW(6)),
    (125, 'Knitwear', 102, 'Cardigans, sweaters and ribbed knits.',                                     'womens-knitwear', NOW(6)),
    (126, 'Outerwear',102, 'Coats, blazers and chore jackets.',                                         'womens-outerwear', NOW(6)),
    (127, 'Skirts',   102, 'Pleated, midi and A-line.',                                                 'womens-skirts', NOW(6));

-- 3. Products ---------------------------------------------------------------------
-- 15 Men (1101-1115), 15 Women (1116-1130). Every product gets a plain, descriptive
-- title (V8's active_title enforces lowercase uniqueness), a slug for the storefront URL,
-- and INR pricing at the level Plain Thread actually sells at.
INSERT INTO products (id, title, description, price, compare_at_price, category_id, brand, material, care_instructions, gender, is_active, slug, image, rating, stock, created_datetime) VALUES
    (1101, 'Organic Cotton Crew Tee',
     'Midweight 180gsm organic cotton, cut for a straight fit that sits clean at the hip. Pre-shrunk and garment-dyed.',
     890.00, 1290.00, 111, 'Plain Thread', '100% GOTS-certified organic cotton', 'Machine wash cold, tumble dry low.', 'MEN', TRUE, 'organic-cotton-crew-tee', '/uploads/seed/organic-cotton-crew-tee-0.jpg', 4.6, 0, NOW(6)),
    (1102, 'Pima Cotton Oxford Shirt',
     'Long-staple pima woven into a proper oxford with a soft roll at the collar. Slightly relaxed through the body.',
     2490.00, 3290.00, 112, 'Plain Thread', '100% Peru pima cotton', 'Machine wash cold, hang dry.', 'MEN', TRUE, 'pima-oxford-shirt', '/uploads/seed/pima-oxford-shirt-0.jpg', 4.7, 0, NOW(6)),
    (1103, 'Selvedge Straight Jean',
     '14.5oz Japanese selvedge denim from Kojima, cut straight through the thigh with a mid rise. Fade-forward indigo.',
     4990.00, 6490.00, 113, 'Plain Thread', '100% cotton selvedge denim', 'Wash cold inside out, hang dry.', 'MEN', TRUE, 'selvedge-straight-jean', '/uploads/seed/selvedge-straight-jean-0.jpg', 4.8, 0, NOW(6)),
    (1104, 'Italian Wool Blazer',
     'Half-canvas blazer in Loro Piana wool with patch pockets and a soft Neapolitan shoulder.',
     8490.00, 11990.00, 116, 'Plain Thread', 'Virgin wool lining, cotton canvas', 'Dry clean only.', 'MEN', TRUE, 'italian-wool-blazer', '/uploads/seed/italian-wool-blazer-0.jpg', 4.5, 0, NOW(6)),
    (1105, 'Merino Crew Knit',
     'Extra-fine 19.5 micron merino knitted at 12 gauge. Layer it or wear it alone.',
     3290.00, 4290.00, 115, 'Plain Thread', '100% extra-fine merino wool', 'Hand wash cold, dry flat.', 'MEN', TRUE, 'merino-crew-knit', '/uploads/seed/merino-crew-knit-0.jpg', 4.7, 0, NOW(6)),
    (1106, 'Linen Camp Shirt',
     'Airy European linen with a notched camp collar. Cut generously so it moves with heat.',
     2190.00, 2790.00, 112, 'Plain Thread', '100% European linen', 'Machine wash cold, hang dry.', 'MEN', TRUE, 'linen-camp-shirt', '/uploads/seed/linen-camp-shirt-0.jpg', 4.4, 0, NOW(6)),
    (1107, 'Flat-Front Chino',
     'Peached Japanese twill, mid-weight, cut flat-front with a clean taper from knee to hem.',
     2690.00, 3490.00, 114, 'Plain Thread', '98% cotton, 2% elastane', 'Machine wash cold.', 'MEN', TRUE, 'flat-front-chino', '/uploads/seed/flat-front-chino-0.jpg', 4.5, 0, NOW(6)),
    (1108, 'Heavyweight Fleece Hoodie',
     '420gsm loopback fleece brushed inside for warmth without weight. Raglan sleeves, tonal drawcord.',
     3190.00, 3990.00, 117, 'Plain Thread', '80% cotton, 20% recycled polyester', 'Machine wash cold, tumble dry low.', 'MEN', TRUE, 'heavyweight-fleece-hoodie', '/uploads/seed/heavyweight-fleece-hoodie-0.jpg', 4.8, 0, NOW(6)),
    (1109, 'Cotton Popover',
     'A collarless pullover in a cotton-canvas twill that softens with wear. Two chest pockets.',
     1790.00, 2290.00, 112, 'Plain Thread', '100% cotton canvas', 'Machine wash cold.', 'MEN', TRUE, 'cotton-popover', '/uploads/seed/cotton-popover-0.jpg', 4.3, 0, NOW(6)),
    (1110, 'Wool Overcoat',
     'Double-faced Italian wool, unlined, cut long and quiet. A coat that ends every winter argument.',
     12990.00, 17990.00, 116, 'Plain Thread', 'Virgin wool, horn buttons', 'Dry clean only.', 'MEN', TRUE, 'wool-overcoat', '/uploads/seed/wool-overcoat-0.jpg', 4.9, 0, NOW(6)),
    (1111, 'Stretch Slim Jean',
     'Comfort-stretch selvedge with 2% elastane. Slim through the thigh without pinching.',
     3790.00, 4790.00, 113, 'Plain Thread', '98% cotton, 2% elastane denim', 'Wash cold inside out.', 'MEN', TRUE, 'stretch-slim-jean', '/uploads/seed/stretch-slim-jean-0.jpg', 4.6, 0, NOW(6)),
    (1112, 'Grade-A Cashmere V-Neck',
     'Inner Mongolia grade-A cashmere, 2-ply, knitted at 7 gauge. Deep V, refined roll at the neck.',
     8990.00, 11990.00, 115, 'Plain Thread', '100% grade-A cashmere', 'Dry clean or hand wash cold.', 'MEN', TRUE, 'cashmere-v-neck', '/uploads/seed/cashmere-v-neck-0.jpg', 4.8, 0, NOW(6)),
    (1113, 'Cotton Utility Jacket',
     'Water-repellent cotton twill with four flap pockets. Cut for commuting and cold mornings.',
     4590.00, 5990.00, 116, 'Plain Thread', '100% cotton, corozo buttons', 'Machine wash cold, hang dry.', 'MEN', TRUE, 'cotton-utility-jacket', '/uploads/seed/cotton-utility-jacket-0.jpg', 4.4, 0, NOW(6)),
    (1114, 'Performance Jogger',
     'Double-knit technical jersey with a four-way stretch and a matte finish. Tapered ankle.',
     2290.00, 2890.00, 117, 'Plain Thread', '78% nylon, 22% elastane', 'Machine wash cold.', 'MEN', TRUE, 'performance-jogger', '/uploads/seed/performance-jogger-0.jpg', 4.5, 0, NOW(6)),
    (1115, 'Silk-Touch Polo',
     'Mercerised cotton knit with a subtle sheen. Collar holds shape without starch.',
     2890.00, 3590.00, 111, 'Plain Thread', '100% mercerised cotton', 'Machine wash cold.', 'MEN', TRUE, 'silk-touch-polo', '/uploads/seed/silk-touch-polo-0.jpg', 4.3, 0, NOW(6)),
    (1116, 'Cotton Slip Dress',
     'Bias-cut cotton sateen with adjustable straps and a soft cowl. Falls mid-calf.',
     3290.00, 4290.00, 121, 'Plain Thread', '100% cotton sateen', 'Machine wash cold, hang dry.', 'WOMEN', TRUE, 'cotton-slip-dress', '/uploads/seed/cotton-slip-dress-0.jpg', 4.7, 0, NOW(6)),
    (1117, 'Merino Ribbed Tee',
     'Fine-gauge merino with a slim rib. Reads like a tee, wears like a knit.',
     1490.00, 1890.00, 122, 'Plain Thread', '100% merino wool', 'Hand wash cold, dry flat.', 'WOMEN', TRUE, 'merino-ribbed-tee', '/uploads/seed/merino-ribbed-tee-0.jpg', 4.6, 0, NOW(6)),
    (1118, 'Wide-Leg Trouser',
     'Wool-blend suiting fabric with a high rise and a wide leg that moves when you do.',
     2890.00, 3790.00, 124, 'Plain Thread', '78% wool, 22% polyester', 'Dry clean only.', 'WOMEN', TRUE, 'wide-leg-trouser', '/uploads/seed/wide-leg-trouser-0.jpg', 4.5, 0, NOW(6)),
    (1119, 'Poplin Shirt Dress',
     'Crisp cotton poplin, cut long with a self-tie belt and mother-of-pearl buttons.',
     3790.00, 4790.00, 121, 'Plain Thread', '100% cotton poplin', 'Machine wash cold.', 'WOMEN', TRUE, 'poplin-shirt-dress', '/uploads/seed/poplin-shirt-dress-0.jpg', 4.7, 0, NOW(6)),
    (1120, 'Cashmere Cardigan',
     'Grade-A Mongolian cashmere in a boxy silhouette with deep rib at the cuffs.',
     7490.00, 9490.00, 125, 'Plain Thread', '100% cashmere', 'Dry clean or hand wash cold.', 'WOMEN', TRUE, 'cashmere-cardigan', '/uploads/seed/cashmere-cardigan-0.jpg', 4.9, 0, NOW(6)),
    (1121, 'Linen Column Dress',
     'Fluid European linen with a column cut and covered placket. Belt sold separately.',
     4290.00, 5490.00, 121, 'Plain Thread', '100% linen', 'Machine wash cold, hang dry.', 'WOMEN', TRUE, 'linen-column-dress', '/uploads/seed/linen-column-dress-0.jpg', 4.6, 0, NOW(6)),
    (1122, 'High-Rise Straight Jean',
     'Non-stretch rigid denim, high-rise, straight-leg. Sits at the natural waist.',
     3990.00, 4990.00, 123, 'Plain Thread', '100% cotton rigid denim', 'Wash cold inside out.', 'WOMEN', TRUE, 'high-rise-straight-jean', '/uploads/seed/high-rise-straight-jean-0.jpg', 4.8, 0, NOW(6)),
    (1123, 'Silk Camisole',
     '19-momme mulberry silk with adjustable straps and a French seam finish.',
     2490.00, 3290.00, 122, 'Plain Thread', '100% mulberry silk', 'Hand wash cold.', 'WOMEN', TRUE, 'silk-camisole', '/uploads/seed/silk-camisole-0.jpg', 4.5, 0, NOW(6)),
    (1124, 'Wool Wrap Coat',
     'Double-faced wool with a self-tie belt and no buttons. Understated and warm.',
     10990.00, 14990.00, 126, 'Plain Thread', '80% virgin wool, 20% cashmere', 'Dry clean only.', 'WOMEN', TRUE, 'wool-wrap-coat', '/uploads/seed/wool-wrap-coat-0.jpg', 4.9, 0, NOW(6)),
    (1125, 'Cotton Chore Jacket',
     'Cotton twill chore with a roomy cut, three patch pockets and a soft collar stand.',
     4190.00, 5290.00, 126, 'Plain Thread', '100% cotton twill', 'Machine wash cold.', 'WOMEN', TRUE, 'cotton-chore-jacket', '/uploads/seed/cotton-chore-jacket-0.jpg', 4.4, 0, NOW(6)),
    (1126, 'Ribbed Knit Tank',
     'Stretch-cotton rib with a high neck and slim straps. Layered or alone.',
     1290.00, 1690.00, 122, 'Plain Thread', '95% cotton, 5% elastane', 'Machine wash cold.', 'WOMEN', TRUE, 'ribbed-knit-tank', '/uploads/seed/ribbed-knit-tank-0.jpg', 4.3, 0, NOW(6)),
    (1127, 'Pleated Midi Skirt',
     'Knife-pleated crepe that falls mid-calf with a hidden side zip.',
     3190.00, 3990.00, 127, 'Plain Thread', '100% viscose crepe', 'Dry clean recommended.', 'WOMEN', TRUE, 'pleated-midi-skirt', '/uploads/seed/pleated-midi-skirt-0.jpg', 4.6, 0, NOW(6)),
    (1128, 'Boxy Cotton Blazer',
     'Structured cotton canvas with a boxy cut, single vent and covered buttons.',
     6490.00, 8290.00, 126, 'Plain Thread', '100% cotton canvas, viscose lining', 'Dry clean only.', 'WOMEN', TRUE, 'boxy-cotton-blazer', '/uploads/seed/boxy-cotton-blazer-0.jpg', 4.5, 0, NOW(6)),
    (1129, 'Everyday Ankle Trouser',
     'Stretch twill trouser cut just above the ankle with a pressed crease.',
     2790.00, 3490.00, 124, 'Plain Thread', '96% cotton, 4% elastane', 'Machine wash cold.', 'WOMEN', TRUE, 'everyday-ankle-trouser', '/uploads/seed/everyday-ankle-trouser-0.jpg', 4.4, 0, NOW(6)),
    (1130, 'Merino Ribbed Beanie',
     'Extra-fine merino in a chunky 3x3 rib with a fold-over cuff.',
     1490.00, 1990.00, 125, 'Plain Thread', '100% extra-fine merino', 'Hand wash cold, dry flat.', 'WOMEN', TRUE, 'merino-ribbed-beanie', '/uploads/seed/merino-ribbed-beanie-0.jpg', 4.7, 0, NOW(6));

-- 4. Gallery ----------------------------------------------------------------------
-- Positions 0-3 for every seed product. Picsum gives deterministic editorial-looking
-- photos so the storefront renders without any real asset uploads during Phase 2a.
-- Position 0 also mirrors products.image below so legacy read paths still see a URL.
INSERT INTO product_images (product_id, url, position, alt, created_datetime)
SELECT
    p.id,
    CONCAT('https://picsum.photos/seed/pt-', p.id, '-', g.pos, '/900/1200?grayscale'),
    g.pos,
    CONCAT(p.title, ' - view ', g.pos + 1),
    NOW(6)
FROM products p
CROSS JOIN (
    SELECT 0 AS pos UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
) g
WHERE p.id BETWEEN 1101 AND 1130;

-- Swap products.image off the placeholder /uploads/seed/... path so anything reading
-- that column (order snapshots, legacy DTOs) gets a real, rendering URL too.
UPDATE products
SET image = CONCAT('https://picsum.photos/seed/pt-', id, '-0/900/1200?grayscale')
WHERE id BETWEEN 1101 AND 1130;

-- 5. Variants ---------------------------------------------------------------------
-- 4 sizes (S/M/L/XL) x 3 colours (Black/Bone/Olive) = 12 SKUs per product, 360 total.
-- Colour hex values are the Plain Thread brand neutrals; the front uses them for swatches.
INSERT INTO product_variants (
    product_id, sku, size, color_name, color_hex,
    price_delta, image_url, stock, created_datetime
)
SELECT
    p.id,
    CONCAT('PT-', LPAD(p.id, 4, '0'), '-', UPPER(SUBSTRING(v.size, 1, 2)), '-', UPPER(SUBSTRING(c.color_name, 1, 3))),
    v.size,
    c.color_name,
    c.color_hex,
    0.00,
    CONCAT('https://picsum.photos/seed/pt-', p.id, '-c-', LOWER(SUBSTRING(c.color_name, 1, 3)), '/900/1200?grayscale'),
    18,
    NOW(6)
FROM products p
CROSS JOIN (
    SELECT 'S' AS size UNION ALL SELECT 'M' UNION ALL SELECT 'L' UNION ALL SELECT 'XL'
) v
CROSS JOIN (
    SELECT 'Black' AS color_name, '#0F0F0F' AS color_hex
    UNION ALL SELECT 'Bone',  '#F1EDE4'
    UNION ALL SELECT 'Olive', '#4A5238'
) c
WHERE p.id BETWEEN 1101 AND 1130;

-- Beanies and scarves should really be ONE size, but for the demo seed we let the S/M/L/XL
-- grid apply uniformly. The storefront filters on variant availability, not on size name.

-- Sync products.stock to the sum of live variant stock so any legacy code path still reads
-- a plausible number even though the truth lives at the variant level now.
UPDATE products p
SET p.stock = (
    SELECT COALESCE(SUM(pv.stock), 0)
    FROM product_variants pv
    WHERE pv.product_id = p.id AND pv.deleted_datetime IS NULL
)
WHERE p.id BETWEEN 1101 AND 1130;

-- Push the categories' auto_increment past the seeded block so admin creates don't collide.
ALTER TABLE categories AUTO_INCREMENT = 200;
ALTER TABLE products     AUTO_INCREMENT = 2000;
