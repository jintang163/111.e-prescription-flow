-- eph_transfer 库
CREATE TABLE IF NOT EXISTS pharmacy (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  code          VARCHAR(32) NOT NULL,
  name          VARCHAR(128) NOT NULL,
  adapter_type  VARCHAR(32) NOT NULL DEFAULT 'MOCK',
  base_url      VARCHAR(255),
  auth_type     VARCHAR(16) NOT NULL DEFAULT 'HMAC',
  app_key       VARCHAR(64),
  sign_secret   VARCHAR(255) COMMENT 'HMAC 密钥（演示明文，生产加密）',
  priority      INT NOT NULL DEFAULT 100,
  status        TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  created_at    DATETIME NOT NULL,
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合作药店';

CREATE TABLE IF NOT EXISTS pharmacy_drug_catalog (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  pharmacy_id     BIGINT NOT NULL,
  drug_code       VARCHAR(40) NOT NULL,
  sku_code        VARCHAR(64),
  price           DECIMAL(10,2),
  available_stock INT NOT NULL DEFAULT 0,
  stock_synced_at DATETIME,
  UNIQUE KEY uk_pharmacy_drug (pharmacy_id, drug_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药店药品库存(Mock)';

CREATE TABLE IF NOT EXISTS pharmacy_order (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no          VARCHAR(48) NOT NULL,
  rx_no             VARCHAR(32) NOT NULL,
  rx_version        INT NOT NULL,
  pharmacy_id       BIGINT NOT NULL,
  pharmacy_code     VARCHAR(32) NOT NULL,
  adapter_type      VARCHAR(32) NOT NULL,
  status            VARCHAR(32) NOT NULL,
  stock_hold_no     VARCHAR(64),
  external_order_no VARCHAR(64),
  idempotency_key   VARCHAR(128) NOT NULL,
  fail_reason       VARCHAR(500),
  attempts          INT NOT NULL DEFAULT 0,
  next_retry_at     DATETIME NULL,
  accepted_at       DATETIME NULL,
  dispensed_at      DATETIME NULL,
  picked_up_at      DATETIME NULL,
  created_at        DATETIME NOT NULL,
  updated_at       DATETIME NOT NULL,
  UNIQUE KEY uk_order_no (order_no),
  UNIQUE KEY uk_idempotency (idempotency_key),
  KEY idx_rx (rx_no),
  KEY idx_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药店订单';

CREATE TABLE IF NOT EXISTS pharmacy_order_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no    VARCHAR(48) NOT NULL,
  from_status VARCHAR(32),
  to_status   VARCHAR(32) NOT NULL,
  detail      VARCHAR(500),
  created_at  DATETIME NOT NULL,
  KEY idx_order (order_no, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态流水';

CREATE TABLE IF NOT EXISTS pharmacy_callback_log (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  pharmacy_code   VARCHAR(32) NOT NULL,
  headers         TEXT,
  raw_body        MEDIUMTEXT,
  signature       VARCHAR(512),
  verify_result   VARCHAR(16) NOT NULL,
  parsed_event    VARCHAR(64),
  processed       TINYINT NOT NULL DEFAULT 0,
  created_at      DATETIME NOT NULL,
  KEY idx_pharmacy_time (pharmacy_code, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药店回调原始报文';

CREATE TABLE IF NOT EXISTS dispatch_attempt (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  rx_no       VARCHAR(32) NOT NULL,
  pharmacy_id BIGINT NOT NULL,
  result      VARCHAR(32) NOT NULL COMMENT 'STOCK_OK/OUT_OF_STOCK/REJECT/TIMEOUT',
  detail      VARCHAR(500),
  created_at  DATETIME NOT NULL,
  KEY idx_rx (rx_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派单/改派记录';

CREATE TABLE IF NOT EXISTS outbox_event (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id         VARCHAR(40) NOT NULL,
  event_type       VARCHAR(64) NOT NULL,
  biz_key          VARCHAR(64),
  aggregate_id     BIGINT,
  payload          MEDIUMTEXT,
  status           VARCHAR(16) NOT NULL DEFAULT 'NEW',
  publish_attempts INT NOT NULL DEFAULT 0,
  next_retry_at    DATETIME,
  created_at       DATETIME NOT NULL,
  published_at     DATETIME NULL,
  UNIQUE KEY uk_event (event_id),
  KEY idx_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务发件箱';

CREATE TABLE IF NOT EXISTS inbox_event (
  event_id    VARCHAR(120) PRIMARY KEY,
  handler     VARCHAR(64) NOT NULL,
  consumed_at DATETIME NOT NULL,
  KEY idx_handler (handler)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消费幂等';
