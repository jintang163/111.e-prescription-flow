-- eph_user 库初始化（用户/资质/CA/密钥 + 通用 outbox/inbox）
CREATE TABLE IF NOT EXISTS sys_user (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  username        VARCHAR(64)  NOT NULL,
  password_hash   CHAR(60)     NOT NULL,
  real_name       VARCHAR(64)  NOT NULL,
  user_type       TINYINT      NOT NULL COMMENT '1医生 2药师 3患者 9管理员',
  phone           VARCHAR(20),
  id_card_mask    VARCHAR(20) COMMENT '身份证脱敏展示',
  status          TINYINT      NOT NULL DEFAULT 1,
  created_at      DATETIME     NOT NULL,
  updated_at      DATETIME     NOT NULL,
  UNIQUE KEY uk_username (username),
  KEY idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

CREATE TABLE IF NOT EXISTS doctor_profile (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  license_no      VARCHAR(64) NOT NULL COMMENT '执业证书编号',
  title           VARCHAR(32) COMMENT '职称',
  dept_code       VARCHAR(32),
  dept_name       VARCHAR(64),
  hospital_name   VARCHAR(128),
  practice_scope  VARCHAR(255),
  license_status  TINYINT NOT NULL DEFAULT 1,
  qualified_at    DATE,
  created_at      DATETIME NOT NULL,
  UNIQUE KEY uk_user (user_id),
  UNIQUE KEY uk_license (license_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医生资质';

CREATE TABLE IF NOT EXISTS pharmacist_profile (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  license_no      VARCHAR(64) NOT NULL,
  title           VARCHAR(32),
  pharmacy_dept   VARCHAR(128),
  license_status  TINYINT NOT NULL DEFAULT 1,
  created_at      DATETIME NOT NULL,
  UNIQUE KEY uk_user (user_id),
  UNIQUE KEY uk_license (license_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药师资质';

CREATE TABLE IF NOT EXISTS patient_profile (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  name            VARCHAR(64) NOT NULL,
  gender          TINYINT COMMENT '1男 2女',
  birth_date      DATE,
  phone           VARCHAR(20),
  allergy_history VARCHAR(500),
  default_address VARCHAR(255),
  created_at      DATETIME NOT NULL,
  UNIQUE KEY uk_user (user_id),
  KEY idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='患者档案';

CREATE TABLE IF NOT EXISTS ca_certificate (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  subject_cn      VARCHAR(128) NOT NULL,
  cert_serial     VARCHAR(64) NOT NULL,
  cert_pem        MEDIUMTEXT NOT NULL,
  cert_type       VARCHAR(8) NOT NULL COMMENT 'ROOT/USER',
  owner_user_id   BIGINT NULL,
  issuer_serial   VARCHAR(64),
  not_before      DATETIME NOT NULL,
  not_after       DATETIME NOT NULL,
  revoked         TINYINT NOT NULL DEFAULT 0,
  revoked_at      DATETIME NULL,
  created_at      DATETIME NOT NULL,
  UNIQUE KEY uk_serial (cert_serial),
  KEY idx_owner (owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CA 证书';

CREATE TABLE IF NOT EXISTS user_signing_key (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  key_version     INT NOT NULL DEFAULT 1,
  alg             VARCHAR(16) NOT NULL DEFAULT 'RSA',
  key_size        INT NOT NULL DEFAULT 2048,
  cert_serial     VARCHAR(64) NOT NULL,
  private_key_enc VARCHAR(4096) NOT NULL COMMENT '主密钥 AES-GCM 包裹 base64',
  status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ROTATED/REVOKED',
  activated_at    DATETIME NOT NULL,
  rotated_at      DATETIME NULL,
  UNIQUE KEY uk_user_version (user_id, key_version),
  UNIQUE KEY uk_cert_serial (cert_serial)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户签名密钥';

CREATE TABLE IF NOT EXISTS outbox_event (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id        VARCHAR(40) NOT NULL,
  event_type      VARCHAR(64) NOT NULL,
  biz_key         VARCHAR(64),
  aggregate_id    BIGINT,
  payload         MEDIUMTEXT,
  status          VARCHAR(16) NOT NULL DEFAULT 'NEW',
  publish_attempts INT NOT NULL DEFAULT 0,
  next_retry_at   DATETIME,
  created_at      DATETIME NOT NULL,
  published_at    DATETIME NULL,
  UNIQUE KEY uk_event (event_id),
  KEY idx_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务发件箱';

CREATE TABLE IF NOT EXISTS inbox_event (
  event_id        VARCHAR(120) PRIMARY KEY,
  handler         VARCHAR(64) NOT NULL,
  consumed_at     DATETIME NOT NULL,
  KEY idx_handler (handler)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消费幂等';
