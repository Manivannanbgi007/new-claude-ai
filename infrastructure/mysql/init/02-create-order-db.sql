-- ---------------------------------------------------------------
-- Database + user for order-service (one database per service).
-- MySQL runs files in /docker-entrypoint-initdb.d ONLY when the data volume is empty.
-- For an existing volume, run this file manually (see event-driven-architecture.md).
-- LOCAL DEV ONLY: the password must match ORDER_DB_PASSWORD in .env.
-- ---------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS order_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'order_user'@'%' IDENTIFIED BY 'order_dev_password';

-- Rights on order_db ONLY: order-service can never read stock_db, and vice versa.
GRANT ALL PRIVILEGES ON order_db.* TO 'order_user'@'%';
FLUSH PRIVILEGES;
