# 012 问题件链路 · 技术设计

## 1. 改动模块

| 模块 | 改动范围 | 说明 |
|------|---------|------|
| claim-application-service | 新增 domain/issue 聚合 | 问题件领域模型、仓储端口、状态枚举 |
| claim-application-service | 新增 infrastructure/persistence/issue | MyBatis-Plus DO、Mapper、仓储实现 |
| claim-application-service | 新增 usecase/issue | 问题件下发与关闭用例 |
| claim-application-service | 修改 domain/claim | ClaimApplication 增加 ISSUE 状态、关联问题件方法 |
| claim-application-service | 新增 interfaces/intake/web/issue | HTTP 回调接口 |
| claim-application-service | 新增 interfaces/intake/web/render | 问题件响应渲染器 |
| claim-application-service | 新增 shared/exception | 问题件相关异常与错误码 |

## 2. 核心业务链路

### 2.1 问题件下发

```
审核系统 → HTTP POST /api/application/v1/callbacks/audit-issues
  → ClaimApplicationIssueIntakeController.receiveIssue()
    → ClaimApplicationIssueIntakeUseCase.receive(command)
      → ClaimApplicationRepository.findByNo(claimApplicationNo)
        → 校验申请存在 & 状态为 WAIT_AUDIT
      → ClaimApplicationIssueRepository.findByIssueNo(issueNo)
        → 幂等判断：已存在则返回成功
      → ClaimApplicationIssueRepository.hasOpenIssue(claimApplicationNo)
        → 校验无未关闭问题件
      → ClaimApplicationIssue.create(issueNo, claimApplicationNo, reason, auditor, issueContent)
      → ClaimApplicationIssueRepository.save(issue)
      → ClaimApplication.transitionToIssue()
      → ClaimApplicationRepository.save(application)
    → 返回成功响应
```

事务边界：整个用例方法在一个事务内完成，保证问题件创建与申请状态变更的原子性。

### 2.2 问题件关闭

```
审核系统 → HTTP POST /api/application/v1/callbacks/audit-issues/{issueNo}/close
  → ClaimApplicationIssueIntakeController.closeIssue(issueNo, command)
    → ClaimApplicationIssueCloseUseCase.close(command)
      → ClaimApplicationIssueRepository.findByIssueNo(issueNo)
        → 校验问题件存在
      → 幂等判断：已 CLOSED 则返回成功
      → ClaimApplicationIssue.close(auditor)
      → ClaimApplicationIssueRepository.save(issue)
      → ClaimApplicationRepository.findByIssueNo(issueNo)
      → ClaimApplication.transitionToWaitAudit()
      → ClaimApplicationRepository.save(application)
    → 返回成功响应
```

事务边界：整个用例方法在一个事务内完成。

## 3. 主要类职责

### 3.1 领域层（domain/issue）

**ClaimApplicationIssue**（聚合根）
- 职责：封装问题件的生命周期行为
- 属性：issueNo、claimApplicationNo、reason、auditor、issueContent、status、closedAt
- 方法：
  - `create(...)` 静态工厂方法：创建新问题件，状态为 OPEN
  - `close(auditor)`：关闭问题件，记录 closedAt
  - `isOpen()`：判断是否未关闭
  - `isClosed()`：判断是否已关闭

**ClaimApplicationIssueStatus**（枚举）
- OPEN：未关闭
- CLOSED：已关闭

**ClaimApplicationIssueRepository**（端口）
- `findByIssueNo(issueNo)`：按问题件编号查询
- `hasOpenIssue(claimApplicationNo)`：判断申请是否有未关闭问题件
- `save(issue)`：保存问题件

### 3.2 领域层（domain/claim）

**ClaimApplication**（聚合根，修改）
- 新增状态值：ISSUE
- 新增方法：
  - `transitionToIssue()`：WAIT_AUDIT → ISSUE
  - `transitionToWaitAudit()`：ISSUE → WAIT_AUDIT
  - `isWaitAudit()`：判断状态是否为 WAIT_AUDIT

**ClaimApplicationStatus**（枚举，修改）
- 新增 ISSUE 值

### 3.3 用例层（usecase/issue）

**ClaimApplicationIssueIntakeUseCase**
- 职责：编排问题件下发流程
- 方法：`receive(ClaimApplicationIssueIntakeCommand)`
- 依赖：ClaimApplicationRepository、ClaimApplicationIssueRepository
- 事务：@Transactional

**ClaimApplicationIssueCloseUseCase**
- 职责：编排问题件关闭流程
- 方法：`close(ClaimApplicationIssueCloseCommand)`
- 依赖：ClaimApplicationIssueRepository、ClaimApplicationRepository
- 事务：@Transactional

**ClaimApplicationIssueIntakeCommand / ClaimApplicationIssueCloseCommand**（值对象）
- 封装用例入参

**ClaimApplicationIssueIntakeResult**（值对象）
- 封装用例返回：issueNo、claimApplicationNo、created（是否新创建）

**ClaimApplicationIssueCloseResult**（值对象）
- 封装用例返回：issueNo、closed（是否新关闭）

### 3.4 基础设施层（infrastructure/persistence/issue）

**ClaimApplicationIssueDO**（数据对象）
- 映射表 `t_claim_application_issue`
- 字段：id、claimApplicationNo、issueNo、reason、auditor、issueContentJson、status、isOpen、closedAt、createdAt、updatedAt

**ClaimApplicationIssueMapper**（MyBatis-Plus Mapper）
- 继承 BaseMapper<ClaimApplicationIssueDO>

**ClaimApplicationIssueRepositoryImpl**（仓储实现）
- 实现 ClaimApplicationIssueRepository 端口
- 依赖 ClaimApplicationIssueMapper
- 负责 DO ↔ 领域对象转换

**ClaimApplicationIssueConverter**（MapStruct 转换器）
- DO ↔ ClaimApplicationIssue 转换

### 3.5 接口层（interfaces/intake/web/issue）

**ClaimApplicationIssueIntakeController**
- 职责：接收审核系统的 HTTP 回调
- 路径前缀：`/api/application/v1/callbacks`
- 方法：
  - `POST /audit-issues`：下发问题件
  - `POST /audit-issues/{issueNo}/close`：关闭问题件
- 依赖：ClaimApplicationIssueIntakeUseCase、ClaimApplicationIssueCloseUseCase、ClaimApplicationResponseRenderer

**ClaimApplicationIssueIntakeRequest / ClaimApplicationIssueCloseRequest**（DTO）
- 封装 HTTP 请求体
- 包含 JSR-303 校验注解

### 3.6 接口层（interfaces/intake/web/render）

**ClaimApplicationIssueResponseRenderer**
- 职责：渲染问题件接口响应
- 方法：
  - `renderSuccess(traceId, result)`：渲染成功响应
  - `renderFailure(traceId, errorCode, errorMessage)`：渲染失败响应
- 错误码映射：
  - ISSUE_APPLICATION_NOT_FOUND → 404
  - ISSUE_APPLICATION_STATUS_INVALID → 400
  - ISSUE_ALREADY_OPEN → 409
  - ISSUE_NOT_FOUND → 404
  - ISSUE_INTAKE_INVALID → 400
  - ISSUE_SYSTEM_ERROR → 500

### 3.7 共享层（shared/exception）

**ClaimApplicationIssueErrorCode**（枚举）
- 实现业务错误码接口
- 枚举值：ISSUE_APPLICATION_NOT_FOUND、ISSUE_APPLICATION_STATUS_INVALID、ISSUE_ALREADY_OPEN、ISSUE_NOT_FOUND、ISSUE_INTAKE_INVALID、ISSUE_SYSTEM_ERROR

**ClaimApplicationIssueException**（异常类）
- 继承业务异常基类
- 携带错误码与业务上下文

## 4. 数据模型与数据库变更

### 4.1 表结构（已存在）

```sql
CREATE TABLE t_claim_application_issue (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
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
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_issue_no (issue_no),
    UNIQUE KEY uk_open_issue_claim_application (open_claim_application_no),
    KEY idx_issue_claim_application_created_at (claim_application_no, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

### 4.2 申请表变更

`t_claim_application` 表的 `status` 字段需支持 ISSUE 值。由于是 VARCHAR 类型，无需 DDL 变更。

## 5. 接口设计

### 5.1 下发问题件

**请求**
```
POST /api/application/v1/callbacks/audit-issues
Content-Type: application/json
X-Request-Id: {traceId}

{
  "claimApplicationNo": "CLA202610310001",
  "issueNo": "ISS202610310001",
  "reason": "缺少病历首页和出院小结",
  "auditor": "auditor_zhangsan",
  "issueContent": {
    "missingMaterials": ["病历首页", "出院小结"],
    "description": "请补充住院相关材料"
  }
}
```

**成功响应（新创建）**
```
HTTP/1.1 200 OK
Content-Type: application/json

{
  "success": true,
  "traceId": "{traceId}",
  "data": {
    "issueNo": "ISS202610310001",
    "claimApplicationNo": "CLA202610310001",
    "created": true
  }
}
```

**成功响应（幂等）**
```
HTTP/1.1 200 OK
Content-Type: application/json

{
  "success": true,
  "traceId": "{traceId}",
  "data": {
    "issueNo": "ISS202610310001",
    "claimApplicationNo": "CLA202610310001",
    "created": false
  }
}
```

**失败响应**
```
HTTP/1.1 404 Not Found
Content-Type: application/json

{
  "success": false,
  "traceId": "{traceId}",
  "error": {
    "code": "ISSUE_APPLICATION_NOT_FOUND",
    "message": "申请不存在，claimApplicationNo=CLA202610310001"
  }
}
```

### 5.2 关闭问题件

**请求**
```
POST /api/application/v1/callbacks/audit-issues/ISS202610310001/close
Content-Type: application/json
X-Request-Id: {traceId}

{
  "auditor": "auditor_zhangsan"
}
```

**成功响应（新关闭）**
```
HTTP/1.1 200 OK
Content-Type: application/json

{
  "success": true,
  "traceId": "{traceId}",
  "data": {
    "issueNo": "ISS202610310001",
    "closed": true
  }
}
```

**成功响应（幂等）**
```
HTTP/1.1 200 OK
Content-Type: application/json

{
  "success": true,
  "traceId": "{traceId}",
  "data": {
    "issueNo": "ISS202610310001",
    "closed": false
  }
}
```

## 6. 幂等、事务与异常处理

### 6.1 幂等

- **下发**：以 `issueNo` 为幂等键。查询 `findByIssueNo(issueNo)`，已存在则直接返回成功（`created=false`），不重复创建。
- **关闭**：以 `issueNo` + 状态为幂等键。查询问题件后判断状态，已 CLOSED 则直接返回成功（`closed=false`），不重复处理。

### 6.2 事务

- 下发与关闭用例均使用 `@Transactional`，保证问题件状态变更与申请状态变更的原子性。
- 事务传播行为：REQUIRED（默认）。
- 事务隔离级别：READ_COMMITTED（MySQL 默认）。

### 6.3 异常处理

- **业务异常**：使用 `ClaimApplicationIssueException`，携带错误码与业务上下文。
- **系统异常**：用例层捕获未预期异常，记录 ERROR 日志，抛出系统异常。
- **异常映射**：Controller 层通过 `ClaimApplicationIssueResponseRenderer` 将业务异常映射为 HTTP 响应。

### 6.4 并发控制

- 同一申请同一时间最多一个 OPEN 问题件：依赖数据库唯一约束 `uk_open_issue_claim_application`。
- 并发下发时，先查询后插入，可能触发唯一约束冲突。捕获 `DuplicateKeyException`，转换为业务异常 `ISSUE_ALREADY_OPEN`。

## 7. 对现有功能的影响

### 7.1 ClaimApplicationStatus 枚举

- 新增 ISSUE 值。
- 现有代码中所有 switch(status) 表达式需补充 ISSUE 分支，或确认不影响。
- 影响范围：
  - `ClaimApplicationResponseRenderer.statusOf()`：无需改动（只处理签收错误码）
  - 其他使用状态的地方：需逐一检查

### 7.2 ClaimApplication 聚合根

- 新增 `transitionToIssue()` 和 `transitionToWaitAudit()` 方法。
- 新增 `isWaitAudit()` 判断方法。
- 不影响现有方法。

### 7.3 数据库

- `t_claim_application_issue` 表已存在，无需 DDL 变更。
- `t_claim_application.status` 字段为 VARCHAR，支持 ISSUE 值，无需 DDL 变更。

### 7.4 接口契约

- 新增两个 HTTP 接口，不影响现有接口。
- 路径前缀 `/api/application/v1/callbacks` 与现有契约一致。

## 8. 测试策略

### 8.1 单元测试

**ClaimApplicationIssueIntakeUseCaseTest**
- 首次下发：申请状态为 WAIT_AUDIT → 创建成功，状态变更为 ISSUE
- 幂等下发：issueNo 已存在 → 返回成功，不重复创建
- 申请不存在：抛 ISSUE_APPLICATION_NOT_FOUND
- 申请状态不匹配：抛 ISSUE_APPLICATION_STATUS_INVALID
- 已有未关闭问题件：抛 ISSUE_ALREADY_OPEN

**ClaimApplicationIssueCloseUseCaseTest**
- 首次关闭：问题件状态为 OPEN → 关闭成功，申请状态回退 WAIT_AUDIT
- 幂等关闭：问题件已 CLOSED → 返回成功，不重复处理
- 问题件不存在：抛 ISSUE_NOT_FOUND

**ClaimApplicationIssueTest**（领域模型）
- 创建问题件：状态为 OPEN
- 关闭问题件：状态变更为 CLOSED，记录 closedAt
- 重复关闭：抛异常

**ClaimApplicationTest**（状态流转）
- WAIT_AUDIT → ISSUE：合法流转
- ISSUE → WAIT_AUDIT：合法流转
- 其他流转：抛异常

### 8.2 集成测试

本次不实现集成测试，依赖运行栈黄金路径验证。
