ALTER TABLE orders
    ADD COLUMN payment_method text CHECK (payment_method IN ('PAY_ON_PICKUP')),
    DROP CONSTRAINT orders_status_check,
    ADD CONSTRAINT orders_status_check CHECK (
        status IN ('PENDING', 'CONFIRMED', 'REJECTED')
        OR (status = 'COMPLETED' AND payment_method IS NOT DISTINCT FROM 'PAY_ON_PICKUP')
    );
