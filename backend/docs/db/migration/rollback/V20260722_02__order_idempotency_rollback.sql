DROP INDEX uk_orders_customer_request ON orders;

ALTER TABLE orders
    DROP COLUMN client_request_id;
