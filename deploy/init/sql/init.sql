-- 各服务连接时带 createDatabaseIfNotExist=true，这里预创建四个 schema 以确保独立隔离
CREATE DATABASE IF NOT EXISTS eph_user         DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS eph_prescription DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS eph_review       DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS eph_transfer     DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
