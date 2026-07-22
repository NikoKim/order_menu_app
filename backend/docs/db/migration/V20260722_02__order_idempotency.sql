ALTER TABLE orders
    ADD COLUMN client_request_id VARCHAR(64) NULL
    COMMENT '客户端订单请求幂等ID';

CREATE UNIQUE INDEX uk_orders_customer_request
    ON orders (customer_user_id, client_request_id);
