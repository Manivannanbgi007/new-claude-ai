-- ---------------------------------------------------------------
-- Runs ONLY on the first start of mysql-product (empty volume).
-- product_db and product_user are created by MYSQL_DATABASE / MYSQL_USER in .env.
-- This script adds Debezium's READ-ONLY replication user.
-- LOCAL DEV ONLY: password must match DEBEZIUM_DB_PASSWORD in .env and
-- "database.password" in debezium/product-cdc-connector.json.
-- ---------------------------------------------------------------
CREATE USER IF NOT EXISTS 'debezium'@'%' IDENTIFIED BY 'debezium_dev_password';

--   SELECT                  initial snapshot of existing rows
--   RELOAD, LOCK TABLES     consistent snapshot
--   SHOW DATABASES          discover the captured database
--   REPLICATION SLAVE       read the binlog stream
--   REPLICATION CLIENT      read binlog position / status
GRANT SELECT, RELOAD, SHOW DATABASES, LOCK TABLES, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'debezium'@'%';

FLUSH PRIVILEGES;
