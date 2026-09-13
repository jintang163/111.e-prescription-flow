-- eph_review 库：Flowable ACT_*/FL_* 表由引擎自建，这里仅建业务绑定表与发件箱
CREATE TABLE IF NOT EXISTS rx_process_binding (
  rx_no                 VARCHAR(32) PRIMARY KEY,
  rx_version            INT NOT NULL,
  process_instance_id   VARCHAR(64) NOT NULL,
  current_task_id       VARCHAR(64),
  current_node          VARCHAR(64),
  candidate_pharmacist  BIGINT NULL,
  status                VARCHAR(32) NOT NULL COMMENT 'RUNNING/APPROVED/REJECTED/AMENDING',
  updated_at            DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='处方-流程实例绑定';

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
