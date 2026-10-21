-- Order line items rendered the product's name/image from the LIVE product row, so an admin
-- rename rewrote history and a soft-delete (INNER JOIN FETCH + Product's @SQLRestriction) made
-- the line item vanish from the order. Snapshot name and image at purchase time.
ALTER TABLE order_products
    ADD COLUMN product_name VARCHAR(255) NOT NULL DEFAULT '' AFTER unit_price,
    ADD COLUMN product_image VARCHAR(255) NULL AFTER product_name;

UPDATE order_products op
    JOIN products p ON op.product_id = p.id
    SET op.product_name = p.title,
        op.product_image = p.image;
