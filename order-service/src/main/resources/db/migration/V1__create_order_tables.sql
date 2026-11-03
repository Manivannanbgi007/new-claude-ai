-- ---------------------------------------------------------------
-- V1: order-service schema (database order_db, container mysql-order).
-- Never edit this file after it ran; add V2__... for changes.
-- ---------------------------------------------------------------

-- One order = one customer purchase. status is driven by the saga.
CREATE TABLE orders
(
    id                  VARCHAR(36)    NOT NULL,
    -- The user-service user id (no foreign key: users live in another database)
    user_id             VARCHAR(36)    NOT NULL,
    status              VARCHAR(30)    NOT NULL,
    currency            VARCHAR(3)     NOT NULL,
    total_amount        DECIMAL(12, 2) NOT NULL,
    -- Why the saga rejected the order (e.g. insufficient stock)
    failure_reason      VARCHAR(500)   NULL,
    cancellation_reason VARCHAR(30)    NULL,
    -- Optional client-supplied key: a retried POST returns the same order
    idempotency_key     VARCHAR(64)    NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_orders_total CHECK (total_amount >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_orders_user_created ON orders (user_id, created_at);
CREATE INDEX idx_orders_status ON orders (status);

-- Order lines. Name and price are COPIED at order time: a later price change
-- must never change an existing order.
CREATE TABLE order_item
(
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    order_id     VARCHAR(36)    NOT NULL,
    sku          VARCHAR(50)    NOT NULL,
    product_name VARCHAR(200)   NOT NULL,
    unit_price   DECIMAL(12, 2) NOT NULL,
    quantity     INT            NOT NULL,
    line_total   DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT uk_order_item_sku UNIQUE (order_id, sku),
    CONSTRAINT ck_order_item_quantity CHECK (quantity > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Every status change, for order tracking and auditing.
CREATE TABLE order_status_history
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    VARCHAR(36)  NOT NULL,
    from_status VARCHAR(30)  NULL,
    to_status   VARCHAR(30)  NOT NULL,
    reason      VARCHAR(500) NULL,
    changed_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_history_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Local copy of the product catalogue, filled from product.catalog.v1 (ProductSnapshot).
CREATE TABLE catalog_product
(
    sku                VARCHAR(50)    NOT NULL,
    product_id         VARCHAR(36)    NOT NULL,
    name               VARCHAR(200)   NOT NULL,
    brand              VARCHAR(100)   NOT NULL,
    category           VARCHAR(100)   NOT NULL,
    price              DECIMAL(12, 2) NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    -- occurredAt of the snapshot that produced this row (older snapshots are ignored)
    source_occurred_at DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    PRIMARY KEY (sku)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Transactional outbox (events to publish)
CREATE TABLE outbox_event
(
    id           BIGINT           NOT NULL AUTO_INCREMENT,
    event_id     VARCHAR(36)      NOT NULL,
    topic        VARCHAR(200)     NOT NULL,
    message_key  VARCHAR(100)     NOT NULL,
    event_type   VARCHAR(100)     NOT NULL,
    payload      VARBINARY(16000) NULL,
    created_at   DATETIME(6)      NOT NULL,
    published_at DATETIME(6)      NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_outbox_unpublished ON outbox_event (published_at, id);

-- Idempotent consumer: events already processed
CREATE TABLE processed_event
(
    event_id     VARCHAR(36)  NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    processed_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (event_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
