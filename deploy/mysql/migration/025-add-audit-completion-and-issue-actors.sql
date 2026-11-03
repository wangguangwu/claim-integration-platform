USE claim_application_db;

-- 025 理赔申请全链路编排：补充审核完成时间、问题件下发人/关闭人与案件号查询索引。
-- 本脚本为一次性迁移，MySQL 8 不支持 ADD COLUMN IF NOT EXISTS / ADD KEY IF NOT EXISTS，
-- 执行前先逐项确认目标列或索引不存在：
--   SELECT COUNT(*) FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA = 'claim_application_db'
--      AND TABLE_NAME = 't_claim_application'
--      AND COLUMN_NAME = 'completed_at';
--   SELECT COLUMN_NAME FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA = 'claim_application_db'
--      AND TABLE_NAME = 't_claim_application_issue'
--      AND COLUMN_NAME IN ('auditor', 'raised_by', 'closed_by');
--   SELECT COUNT(*) FROM information_schema.STATISTICS
--    WHERE TABLE_SCHEMA = 'claim_application_db'
--      AND TABLE_NAME = 't_claim_case_relation'
--      AND INDEX_NAME = 'idx_case_relation_tenant_case';
-- 每项返回 0（或已出现目标列）时跳过对应语句，避免重复执行报错。

ALTER TABLE t_claim_application
    ADD COLUMN completed_at DATETIME(3) NULL COMMENT '审核完成时间，COMPLETED 时非空'
        AFTER claim_application_attempt;

ALTER TABLE t_claim_application_issue
    CHANGE COLUMN auditor raised_by VARCHAR(128) NOT NULL COMMENT '问题件下发人标识';

ALTER TABLE t_claim_application_issue
    ADD COLUMN closed_by VARCHAR(128) NULL COMMENT '问题件关闭人标识，未关闭时为空'
        AFTER raised_by;

ALTER TABLE t_claim_case_relation
    ADD KEY idx_case_relation_tenant_case (tenant_code, case_no);
