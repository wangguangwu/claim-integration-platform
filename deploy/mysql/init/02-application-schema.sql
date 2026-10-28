USE claim_application_db;

-- Application 聚合根；租户与外部申请号共同保证接入幂等。
CREATE TABLE IF NOT EXISTS t_claim_application (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_code VARCHAR(64) NOT NULL,
    application_no VARCHAR(64) NOT NULL,
    external_application_no VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    application_attempt INT UNSIGNED NOT NULL DEFAULT 1,
    applicant_json JSON NULL,
    primary_insured_json JSON NULL,
    accident_person_json JSON NULL,
    payee_json JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_application_no (application_no),
    UNIQUE KEY uk_tenant_external_application (tenant_code, external_application_no),
    KEY idx_application_status_updated_at (status, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 材料保留初始 URL 与上传、转存后的 URL，支持追溯文件处理链路。
CREATE TABLE IF NOT EXISTS t_claim_application_material (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_no VARCHAR(64) NOT NULL,
    batch_no VARCHAR(64) NOT NULL,
    material_type VARCHAR(64) NOT NULL,
    original_file_url VARCHAR(1024) NOT NULL,
    uploaded_file_url VARCHAR(1024) NULL,
    status VARCHAR(32) NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_material_application_batch (application_no, batch_no),
    KEY idx_material_application_deleted (application_no, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 同一申请当前仅允许一个未关闭问题件，生成列上的唯一索引负责并发约束。
CREATE TABLE IF NOT EXISTS t_claim_application_issue (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_no VARCHAR(64) NOT NULL,
    issue_no VARCHAR(64) NOT NULL,
    reason VARCHAR(512) NOT NULL,
    auditor VARCHAR(128) NOT NULL,
    issue_content_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    is_open TINYINT(1) NOT NULL DEFAULT 1,
    open_application_no VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN is_open = 1 THEN application_no ELSE NULL END
    ) STORED,
    closed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_issue_no (issue_no),
    UNIQUE KEY uk_open_issue_application (open_application_no),
    KEY idx_issue_application_created_at (application_no, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 审核签收返回的 Case 与 Application 的关联，重复回调不重复建关系。
CREATE TABLE IF NOT EXISTS t_claim_case_relation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_code VARCHAR(64) NOT NULL,
    application_no VARCHAR(64) NOT NULL,
    case_no VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_application_case (tenant_code, application_no, case_no),
    KEY idx_case_relation_application (application_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 一行记录一个交互事件；同一 interaction_no 的多行按发生时间组成完整行为。
-- 只有受理成功后才有平台申请号，因此签收受理前的失败不写入本表。
CREATE TABLE IF NOT EXISTS t_claim_interaction_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_no VARCHAR(64) NOT NULL,
    interaction_no VARCHAR(64) NOT NULL,
    interaction_type VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    target_system VARCHAR(64) NOT NULL,
    request_id VARCHAR(128) NULL,
    trace_id VARCHAR(128) NULL,
    result_status VARCHAR(32) NOT NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(512) NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_interaction_application_occurred_at (application_no, occurred_at),
    KEY idx_interaction_group_occurred_at (interaction_no, occurred_at),
    KEY idx_interaction_request_id (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 与业务状态同事务写入的待投递事件，为后续可靠异步投递提供基础。
CREATE TABLE IF NOT EXISTS t_claim_outbox_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_version INT UNSIGNED NOT NULL DEFAULT 1,
    application_no VARCHAR(64) NOT NULL,
    tenant_code VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NULL,
    published_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_status_next_retry_at (status, next_retry_at),
    KEY idx_outbox_application_created_at (application_no, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 消费者按消费者组和事件标识去重，支撑至少一次投递。
CREATE TABLE IF NOT EXISTS t_claim_consumed_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    consumer_group VARCHAR(128) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    application_no VARCHAR(64) NOT NULL,
    consumed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_consumed_event (consumer_group, event_id),
    KEY idx_consumed_application (application_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
