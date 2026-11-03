USE claim_application_db;

-- 024 理赔受理聚合根重设计：为申请主表补充乐观锁版本号。
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，执行前先确认列不存在：
--   SELECT COUNT(*) FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA = 'claim_application_db'
--      AND TABLE_NAME = 't_claim_application'
--      AND COLUMN_NAME = 'version';
-- 返回 0 时执行本脚本，返回 1 时表示已迁移，跳过。
ALTER TABLE t_claim_application
    ADD COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '聚合乐观锁版本号' AFTER claim_application_attempt;
