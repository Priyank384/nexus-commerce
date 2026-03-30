CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    rating DECIMAL(3,1) NOT NULL,
    comment TEXT,
    created_datetime DATETIME(6) NOT NULL,
    updated_datetime DATETIME(6),
    deleted_datetime DATETIME(6),
    PRIMARY KEY(id),
    CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_review_order FOREIGN KEY (order_id) REFERENCES orders (id)
);