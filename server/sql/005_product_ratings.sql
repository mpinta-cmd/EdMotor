CREATE TABLE product_ratings (
    product_id bigint NOT NULL REFERENCES products(id),
    client_user_id bigint NOT NULL REFERENCES users(id),
    stars smallint NOT NULL CHECK (stars BETWEEN 1 AND 5),
    PRIMARY KEY (product_id, client_user_id)
);

CREATE INDEX orders_delivered_pickup_client_idx
    ON orders (client_user_id, reviewed_at DESC, id DESC)
    WHERE status = 'COMPLETED' AND payment_method = 'PAY_ON_PICKUP';
