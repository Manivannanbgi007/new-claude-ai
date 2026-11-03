-- ---------------------------------------------------------------
-- V2: transactional outbox. A ProductSnapshot (or tombstone) is written here in the
-- SAME transaction as every product change; OutboxRelay publishes it to
-- product.catalog.v1. Debezium does not capture this table (table.include.list).
-- ---------------------------------------------------------------
CREATE TABLE outbox_event
(
    id           BIGINT          NOT NULL AUTO_INCREMENT,
    event_id     VARCHAR(36)     NOT NULL,
    topic        VARCHAR(200)    NOT NULL,
    message_key  VARCHAR(100)    NOT NULL,
    event_type   VARCHAR(100)    NOT NULL,
    -- Confluent Avro wire format (magic byte + schema id + Avro data); NULL = tombstone
    payload      VARBINARY(16000) NULL,
    created_at   DATETIME(6)     NOT NULL,
    published_at DATETIME(6)     NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- The relay's query: "oldest unpublished first"
CREATE INDEX idx_outbox_unpublished ON outbox_event (published_at, id);
