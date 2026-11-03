-- ---------------------------------------------------------------
-- V1: stock tables.
-- Flyway runs this ONCE and records it in the flyway_schema_history table.
-- Never edit an applied migration; add V2__..., V3__... for changes.
-- ---------------------------------------------------------------

-- One row per product (SKU): how many units exist and how many are reserved.
CREATE TABLE stock_item
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    sku               VARCHAR(50) NOT NULL,
    quantity_on_hand  INT         NOT NULL,
    quantity_reserved INT         NOT NULL DEFAULT 0,
    reorder_level     INT         NOT NULL DEFAULT 0,
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_item_sku UNIQUE (sku),
    -- The database itself refuses impossible numbers, even if application code has a bug.
    CONSTRAINT ck_stock_item_on_hand CHECK (quantity_on_hand >= 0),
    CONSTRAINT ck_stock_item_reserved CHECK (quantity_reserved >= 0 AND quantity_reserved <= quantity_on_hand),
    CONSTRAINT ck_stock_item_reorder CHECK (reorder_level >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Units held for an order until it is confirmed (paid/shipped) or released (cancelled).
CREATE TABLE stock_reservation
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    order_id   VARCHAR(64) NOT NULL,
    sku        VARCHAR(50) NOT NULL,
    quantity   INT         NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- One line per product per order; also makes repeated reserve calls safe (idempotent).
    CONSTRAINT uk_reservation_order_sku UNIQUE (order_id, sku),
    CONSTRAINT fk_reservation_stock_item FOREIGN KEY (sku) REFERENCES stock_item (sku),
    CONSTRAINT ck_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT ck_reservation_status CHECK (status IN ('RESERVED', 'CONFIRMED', 'RELEASED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- MySQL automatically creates an index on stock_reservation.sku for the foreign key.
