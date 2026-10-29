USE claim_application_db;

-- 理赔申请聚合根；租户与外部申请号共同保证接入幂等。
CREATE TABLE IF NOT EXISTS t_claim_application (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_code VARCHAR(64) NOT NULL COMMENT '理赔申请所属租户编码',
    claim_application_no VARCHAR(64) NOT NULL COMMENT '平台全局唯一理赔申请编号',
    external_claim_application_no VARCHAR(128) NOT NULL COMMENT '租户侧申请编号，与租户编码组成幂等键',
    status VARCHAR(32) NOT NULL COMMENT '理赔申请生命周期状态',
    claim_application_attempt INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '同一外部申请编号的受理尝试次数',
    applicant_json JSON NULL COMMENT '申请人标准化信息 JSON',
    primary_insured_json JSON NULL COMMENT '主被保险人标准化信息 JSON',
    accident_person_json JSON NULL COMMENT '出险人标准化信息 JSON',
    payee_json JSON NULL COMMENT '领款人标准化信息 JSON',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录最后更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_claim_application_no (claim_application_no),
    UNIQUE KEY uk_tenant_external_claim_application (tenant_code, external_claim_application_no),
    KEY idx_claim_application_status_updated_at (status, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '理赔申请主表';

-- 材料保留初始 URL 与上传、转存后的 URL，支持追溯文件处理链路。
CREATE TABLE IF NOT EXISTS t_claim_application_material (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    claim_application_no VARCHAR(64) NOT NULL COMMENT '所属理赔申请的平台申请编号',
    batch_no VARCHAR(64) NOT NULL COMMENT '同次申请提交共用的材料批次编号',
    material_type VARCHAR(64) NOT NULL COMMENT '归一化后的材料类型',
    original_file_url VARCHAR(1024) NOT NULL COMMENT '租户提交的原始文件地址',
    uploaded_file_url VARCHAR(1024) NULL COMMENT '文件处理完成后的上传或转存地址',
    status VARCHAR(32) NOT NULL COMMENT '材料处理状态',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已被重新申请流程逻辑删除',
    deleted_at DATETIME(3) NULL COMMENT '逻辑删除发生时间，未删除时为空',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录最后更新时间',
    PRIMARY KEY (id),
    KEY idx_material_claim_application_batch (claim_application_no, batch_no),
    KEY idx_material_claim_application_deleted (claim_application_no, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '理赔申请材料表';

-- 同一申请当前仅允许一个未关闭问题件，生成列上的唯一索引负责并发约束。
CREATE TABLE IF NOT EXISTS t_claim_application_issue (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    claim_application_no VARCHAR(64) NOT NULL COMMENT '所属理赔申请的平台申请编号',
    issue_no VARCHAR(64) NOT NULL COMMENT '平台问题件编号',
    reason VARCHAR(512) NOT NULL COMMENT '问题件原因说明',
    auditor VARCHAR(128) NOT NULL COMMENT '创建或处理问题件的审核人员标识',
    issue_content_json JSON NOT NULL COMMENT '问题件详情 JSON',
    status VARCHAR(32) NOT NULL COMMENT '问题件处理状态',
    is_open TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否为未关闭问题件',
    open_claim_application_no VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN is_open = 1 THEN claim_application_no ELSE NULL END
    ) STORED COMMENT '未关闭问题件时等于平台申请编号，用于唯一约束',
    closed_at DATETIME(3) NULL COMMENT '问题件关闭时间，未关闭时为空',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录最后更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_issue_no (issue_no),
    UNIQUE KEY uk_open_issue_claim_application (open_claim_application_no),
    KEY idx_issue_claim_application_created_at (claim_application_no, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '理赔申请问题件表';

-- 审核签收返回的案件与理赔申请的关联，重复回调不重复建关系。
CREATE TABLE IF NOT EXISTS t_claim_case_relation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_code VARCHAR(64) NOT NULL COMMENT '理赔申请所属租户编码',
    claim_application_no VARCHAR(64) NOT NULL COMMENT '关联理赔申请的平台申请编号',
    case_no VARCHAR(64) NOT NULL COMMENT '审核签收返回的案件编号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '关联创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '关联最后更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_claim_application_case (tenant_code, claim_application_no, case_no),
    KEY idx_case_relation_claim_application (claim_application_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '理赔申请与案件关联表';

-- 一行记录一个交互事件；同一 interaction_no 的多行按发生时间组成完整行为。
-- 只有受理成功后才有平台申请号，因此签收受理前的失败不写入本表。
CREATE TABLE IF NOT EXISTS t_claim_interaction_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    claim_application_no VARCHAR(64) NOT NULL COMMENT '所属理赔申请的平台申请编号',
    interaction_no VARCHAR(64) NOT NULL COMMENT '一次交互行为的聚合编号',
    interaction_type VARCHAR(64) NOT NULL COMMENT '交互业务类型',
    action VARCHAR(32) NOT NULL COMMENT '当前事件动作，例如开始、完成、复用或失败',
    direction VARCHAR(16) NOT NULL COMMENT '交互方向，例如请求或回调',
    target_system VARCHAR(64) NOT NULL COMMENT '目标外部系统或内部组件',
    request_id VARCHAR(128) NULL COMMENT '调用方提供的幂等请求标识',
    trace_id VARCHAR(128) NULL COMMENT '链路追踪标识',
    result_status VARCHAR(32) NOT NULL COMMENT '交互结果状态',
    error_code VARCHAR(64) NULL COMMENT '失败时的业务或系统错误码',
    error_message VARCHAR(512) NULL COMMENT '失败时的脱敏错误说明',
    occurred_at DATETIME(3) NOT NULL COMMENT '事件实际发生时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录最后更新时间',
    PRIMARY KEY (id),
    KEY idx_interaction_claim_application_occurred_at (claim_application_no, occurred_at),
    KEY idx_interaction_group_occurred_at (interaction_no, occurred_at),
    KEY idx_interaction_request_id (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '理赔申请交互事件表';

-- 与业务状态同事务写入的待投递事件，为后续可靠异步投递提供基础。
CREATE TABLE IF NOT EXISTS t_claim_outbox_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id VARCHAR(64) NOT NULL COMMENT '全局唯一事件标识',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    event_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '事件载荷版本',
    claim_application_no VARCHAR(64) NOT NULL COMMENT '事件所属理赔申请的平台申请编号',
    tenant_code VARCHAR(64) NOT NULL COMMENT '事件所属租户编码',
    payload JSON NOT NULL COMMENT '不含敏感信息的事件载荷 JSON',
    status VARCHAR(32) NOT NULL COMMENT '投递状态',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已执行的投递重试次数',
    next_retry_at DATETIME(3) NULL COMMENT '下一次允许投递的时间',
    published_at DATETIME(3) NULL COMMENT '成功投递到消息系统的时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '事件记录最后更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_status_next_retry_at (status, next_retry_at),
    KEY idx_outbox_claim_application_created_at (claim_application_no, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '本地事务消息待投递表';

-- 消费者按消费者组和事件标识去重，支撑至少一次投递。
CREATE TABLE IF NOT EXISTS t_claim_consumed_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    consumer_group VARCHAR(128) NOT NULL COMMENT '消费者组，用于区分独立消费语义',
    event_id VARCHAR(64) NOT NULL COMMENT '已完成消费的全局唯一事件标识',
    claim_application_no VARCHAR(64) NOT NULL COMMENT '消息所属理赔申请的平台申请编号',
    consumed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '消费业务处理成功的时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '消费登记创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '消费登记最后更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_consumed_event (consumer_group, event_id),
    KEY idx_consumed_claim_application (claim_application_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '消息消费幂等登记表';
