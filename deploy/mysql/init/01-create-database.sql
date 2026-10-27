-- Application 服务独占数据库，避免与 Case、AI 等服务跨库直连。
CREATE DATABASE IF NOT EXISTS claim_application_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

-- 服务运行使用应用账号，root 账号仅用于本地镜像初始化。
CREATE USER IF NOT EXISTS 'claim_app'@'%' IDENTIFIED BY 'claim_app';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
    ON claim_application_db.* TO 'claim_app'@'%';
FLUSH PRIVILEGES;
