-- ---------------------------------------------------------------
-- V2: stock-service joins the order saga over Kafka.
--   outbox_event     events to publish (StockReserved, StockReservationFailed, StockReleased)
--   processed_event  order events already handled (idempotent consumer)
-- ---------------------------------------------------------------
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

CREATE TABLE processed_event
(
    event_id     VARCHAR(36)  NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    processed_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (event_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
