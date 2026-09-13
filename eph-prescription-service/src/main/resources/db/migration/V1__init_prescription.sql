-- eph_prescription 库
CREATE TABLE IF NOT EXISTS prescription (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  rx_no                 VARCHAR(32) NOT NULL COMMENT '处方号 RX+日期+序列',
  rx_version            INT NOT NULL DEFAULT 1,
  patient_id            BIGINT NOT NULL,
  patient_name          VARCHAR(64) NOT NULL,
  patient_id_card_mask  VARCHAR(20),
  patient_age           INT,
  patient_gender        TINYINT,
  patient_phone         VARCHAR(20),
  doctor_id             BIGINT NOT NULL,
  doctor_name           VARCHAR(64) NOT NULL,
  dept_code             VARCHAR(32),
  dept_name             VARCHAR(64),
  rx_category           TINYINT NOT NULL DEFAULT 1 COMMENT '1普通 2急诊 3儿科 4麻精',
  diagnosis_summary     VARCHAR(500),
  rx_status             VARCHAR(32) NOT NULL,
  review_status         VARCHAR(16) NOT NULL DEFAULT 'NONE',
  doctor_signed         TINYINT NOT NULL DEFAULT 0,
  pharmacist_signed     TINYINT NOT NULL DEFAULT 0,
  amendment_count       INT NOT NULL DEFAULT 0,
  reject_reason         TEXT NULL,
  pdf_oss_key           VARCHAR(256),
  pdf_sha256            CHAR(64),
  pdf_status            VARCHAR(16) NOT NULL DEFAULT 'NONE',
  effective_at          DATETIME NULL,
  expire_at             DATETIME NULL,
  fulfillment_status    VARCHAR(32) NULL,
  current_pharmacy_id   BIGINT NULL,
  current_pharmacy_name VARCHAR(128) NULL,
  current_order_no      VARCHAR(48) NULL,
  related_rx_no         VARCHAR(32) NULL COMMENT '废方新开关联',
  version               INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
  created_at            DATETIME NOT NULL,
  updated_at            DATETIME NOT NULL,
  UNIQUE KEY uk_rx_no (rx_no),
  KEY idx_patient_status (patient_id, rx_status),
  KEY idx_doctor_created (doctor_id, created_at),
  KEY idx_status_updated (rx_status, updated_at),
  KEY idx_current_order (current_order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电子处方主表';

CREATE TABLE IF NOT EXISTS prescription_item (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  rx_id               BIGINT NOT NULL,
  seq                 INT NOT NULL,
  drug_code           VARCHAR(40) NOT NULL,
  drug_name           VARCHAR(128) NOT NULL,
  spec                VARCHAR(128),
  dosage_form         VARCHAR(32),
  qty                 DECIMAL(10,2) NOT NULL,
  unit                VARCHAR(16),
  single_dose         VARCHAR(64),
  dose_unit           VARCHAR(16),
  frequency           VARCHAR(32) COMMENT 'BID/TID/QD...',
  administration_route VARCHAR(32),
  days                INT,
  skin_test_flag      TINYINT NOT NULL DEFAULT 0,
  remark              VARCHAR(255),
  UNIQUE KEY uk_rx_seq (rx_id, seq),
  KEY idx_drug (drug_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='处方明细';

CREATE TABLE IF NOT EXISTS prescription_diagnosis (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  rx_id          BIGINT NOT NULL,
  seq            INT NOT NULL,
  icd10_code     VARCHAR(16),
  diagnosis_name VARCHAR(128) NOT NULL,
  UNIQUE KEY uk_rx_seq (rx_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='处方诊断';

CREATE TABLE IF NOT EXISTS signature_record (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  rx_id               BIGINT NOT NULL,
  rx_no               VARCHAR(32) NOT NULL,
  rx_version          INT NOT NULL,
  signer_id           BIGINT NOT NULL,
  signer_name         VARCHAR(64) NOT NULL,
  signer_role         VARCHAR(16) NOT NULL COMMENT 'DOCTOR/PHARMACIST',
  cert_serial         VARCHAR(64) NOT NULL,
  cert_pem_snapshot   MEDIUMTEXT NOT NULL,
  alg                 VARCHAR(32) NOT NULL DEFAULT 'SHA256withRSA',
  canonical_payload   MEDIUMTEXT NOT NULL,
  payload_sha256      CHAR(64) NOT NULL,
  signature_value     VARCHAR(1024) NOT NULL,
  sign_status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/SUPERSEDED',
  flow_action         VARCHAR(16) NOT NULL COMMENT 'SUBMIT/APPROVE',
  sign_time           DATETIME(3) NOT NULL,
  created_at          DATETIME NOT NULL,
  UNIQUE KEY uk_rx_version_role (rx_id, rx_version, signer_role),
  KEY idx_cert (cert_serial),
  KEY idx_rxno (rx_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字签名记录';

CREATE TABLE IF NOT EXISTS rx_status_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  rx_id       BIGINT NOT NULL,
  rx_no       VARCHAR(32) NOT NULL,
  from_status VARCHAR(32),
  to_status   VARCHAR(32) NOT NULL,
  actor_id    BIGINT,
  actor_role  VARCHAR(16),
  action      VARCHAR(64),
  comment     TEXT,
  created_at  DATETIME NOT NULL,
  KEY idx_rx_time (rx_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='处方状态流水';

CREATE TABLE IF NOT EXISTS review_record (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  rx_id                BIGINT NOT NULL,
  rx_no                VARCHAR(32) NOT NULL,
  rx_version           INT NOT NULL,
  pharmacist_id        BIGINT,
  pharmacist_name      VARCHAR(64),
  decision             VARCHAR(16) NOT NULL,
  comment              TEXT,
  duration_ms          BIGINT,
  created_at           DATETIME NOT NULL,
  KEY idx_rx (rx_no, rx_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审方记录投影';

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
