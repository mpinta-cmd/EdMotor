CREATE TABLE IF NOT EXISTS product_images (
    id uuid PRIMARY KEY,
    uploaded_by bigint NOT NULL REFERENCES users(id),
    content_digest bytea NOT NULL CHECK (octet_length(content_digest) = 32),
    data bytea NOT NULL CHECK (octet_length(data) BETWEEN 1 AND 5242880),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (uploaded_by, content_digest)
);

CREATE TABLE IF NOT EXISTS products (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    name text NOT NULL CHECK (char_length(name) BETWEEN 3 AND 180),
    description text NOT NULL CHECK (char_length(description) BETWEEN 1 AND 4000),
    category text NOT NULL CHECK (char_length(category) BETWEEN 2 AND 100),
    price_cop bigint NOT NULL CHECK (price_cop > 0),
    promotion_price_cop bigint CHECK (promotion_price_cop > 0 AND promotion_price_cop < price_cop),
    stock integer NOT NULL CHECK (stock >= 0),
    image_id uuid REFERENCES product_images(id) ON DELETE RESTRICT,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS products_active_name_category
    ON products (lower(name), lower(category)) WHERE is_active;
CREATE INDEX IF NOT EXISTS products_image_idx ON products (image_id);

CREATE TABLE IF NOT EXISTS orders (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_user_id bigint NOT NULL REFERENCES users(id),
    request_id uuid NOT NULL,
    client_name text NOT NULL,
    status text NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    total_cop bigint NOT NULL CHECK (total_cop > 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    reviewed_by bigint REFERENCES users(id),
    reviewed_at timestamptz,
    UNIQUE (client_user_id, request_id),
    CHECK (
        (status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (status <> 'PENDING' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS orders_client_created_idx
    ON orders (client_user_id, created_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS order_items (
    order_id bigint NOT NULL REFERENCES orders(id),
    product_id bigint NOT NULL REFERENCES products(id),
    product_name text NOT NULL,
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price_cop bigint NOT NULL CHECK (unit_price_cop > 0),
    PRIMARY KEY (order_id, product_id)
);
