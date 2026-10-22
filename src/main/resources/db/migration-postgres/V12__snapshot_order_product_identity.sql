-- Order line items rendered the product's name/image from the LIVE product row, so an admin
-- rename rewrote history and a soft-delete (INNER JOIN FETCH + Product's @SQLRestriction) made
-- the line item vanish from the order. Snapshot name and image at purchase time.
ALTER TABLE order_products
    ADD COLUMN product_name VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN product_image VARCHAR(255) NULL;

UPDATE order_products op
    SET product_name = p.title,
        product_image = p.image
    FROM products p
    WHERE op.product_id = p.id;
