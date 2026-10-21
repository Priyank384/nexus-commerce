CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(32),
    created_datetime DATETIME(6) NOT NULL,
    updated_datetime DATETIME(6),
    deleted_datetime DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
);

ALTER TABLE orders
    ADD COLUMN user_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE reviews
    ADD COLUMN user_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES users (id);