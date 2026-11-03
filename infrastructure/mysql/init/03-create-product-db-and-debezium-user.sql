-- ---------------------------------------------------------------
-- 1) Database + user for product-write-service (the product source of truth)
-- 2) A dedicated, READ-ONLY replication user for Debezium
-- Runs automatically ONLY on an empty MySQL volume. For an existing volume,
-- run it manually (see product-write-service/README.md).
-- LOCAL DEV ONLY: passwords must match .env and debezium/product-cdc-connector.json.
-- ---------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS product_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'product_user'@'%' IDENTIFIED BY 'product_dev_password';
GRANT ALL PRIVILEGES ON product_db.* TO 'product_user'@'%';

-- Debezium reads the binary log (binlog) like a replica would.
--   SELECT                  initial snapshot of existing rows
--   RELOAD, LOCK TABLES     consistent snapshot
--   SHOW DATABASES          discover the captured database
--   REPLICATION SLAVE       read the binlog stream
--   REPLICATION CLIENT      read binlog position / status
-- It can NOT insert, update or delete anything.
CREATE USER IF NOT EXISTS 'debezium'@'%' IDENTIFIED BY 'debezium_dev_password';
GRANT SELECT, RELOAD, SHOW DATABASES, LOCK TABLES, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'debezium'@'%';

FLUSH PRIVILEGES;
