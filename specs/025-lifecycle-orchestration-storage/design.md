# 025 理赔申请全链路编排、问题件交互与审核完成 · 设计

## 1. 设计范围与关键裁定

| 对象 | 裁定 | 理由 |
| --- | --- | --- |
| 审核签收 | 审核系统完成签收的事实通知，驱动申请进入终态 `COMPLETED` | 只有审核系统回传全部案件号才代表平台受理职责结束 |
| 完成事务边界 | 消费登记、案件关联追加、申请状态迁移、完成时间与轨迹同一事务 | 任一失败不得留下“关系已写、状态未完成”的部分数据 |
| `ClaimCaseRelation` | 仍是聚合外追加事实，不进入聚合加载与保存 | 一个申请可对应多个案件号，关系追加不改变聚合不变量 |
| `COMPLETED` | 唯一终态，不允许再迁移 | 终态后重新申请、补材、下发问题件全部拒绝 |
| 问题件 Actor | 拆分为下发人 `raisedBy` 与关闭人 `closedBy` | 关闭不得覆盖下发人，历史可追溯两种角色 |
| 问题件接口字段 | HTTP 请求字段维持 `auditor`，在接口层映射为下发人或关闭人 | 不破坏审核系统既有调用契约 |
| `WAIT_AUDIT` 补偿 | 通过审核签收状态查询端口判断已签收、处理中、未找到 | 审核交付丢失与签收消息丢失都表现为 `WAIT_AUDIT` 滞留 |
| 查询 | 继续只走读模型，不加载聚合、不写轨迹 | 查询组合不等于聚合组合，与 024 边界一致 |

本 Spec 取代 024 验收标准第 8 条“审核签收链路行为与 023 保持一致”的结论：审核签收从“只追加案件关联”升级为“案件关联与申请完成同事务”。

## 2. 模块与包结构

```text
domain/claim
├── ClaimApplication.java                 # 新增 completedAt、completeAudit、hasOpenIssue、isCompleted
├── ClaimApplicationStatus.java           # 新增 COMPLETED，可复用状态纳入终态
└── issue/ClaimApplicationIssue.java      # raisedBy / closedBy 拆分

usecase/audit
├── AuditSignedUseCase.java               # 只做消费预判，业务校验下沉事务服务
├── AuditSignedTransactionService.java    # 事务内完成校验、关系追加、状态迁移与轨迹
├── AuditSignedConflictException.java     # 新增，版本冲突内部信号
├── command/AuditSignedCommand.java       # 案件号规范化与约束
├── model/AuditSignedStatus.java          # 新增，审核签收查询结果
├── port/AuditSignedStatusQuery.java      # 新增，审核签收状态查询端口
└── result/AuditSignedOutcome.java        # 新增 COMPLETED

usecase/integration/compensation
├── ClaimApplicationCompensationUseCase.java            # 新增 WAIT_AUDIT 滞留补偿
├── ClaimApplicationCompensationTransactionService.java # 轨迹写入支持指定交互类型
└── port/ClaimApplicationCompensationPolicy.java        # 新增 waitAuditTimeout

infrastructure/audit/external
└── LocalAuditSignedStatusQuery.java      # 新增，本地审核签收查询实现

infrastructure/claim/persistence
├── ClaimApplicationDO.java / ClaimApplicationDataConverter.java / MyBatisClaimApplicationRepository.java
├── ClaimApplicationIssueDO.java / ClaimApplicationIssueConverter.java
└── 保存与重建同步完成时间、下发人、关闭人

infrastructure/query/persistence
└── MyBatisClaimApplicationDetailQuery.java

infrastructure/integration/config
├── ClaimApplicationCompensationProperties.java
└── LocalIntegrationProperties.java

interfaces/integration/scheduling
└── ClaimApplicationCompensationScheduler.java
```

基础设施层只依赖 `usecase/audit/port`、`usecase/audit/model` 等既有契约包，ArchUnit 规则不需要新增白名单。

## 3. 领域模型

### 3.1 状态机

`ClaimApplicationStatus` 在末尾新增 `COMPLETED`，迁移表如下：

| 当前状态 | 目标状态 | 触发 |
| --- | --- | --- |
| `PENDING_ACCEPT` | `ACCEPTED` | 全部材料处理成功 |
| `PENDING_ACCEPT` | `ACCEPT_FAILED` | 任一材料处理失败 |
| `ACCEPT_FAILED` | `PENDING_ACCEPT` | 重新申请 |
| `ACCEPTED` | `WAIT_AUDIT` | 存在有效保单 |
| `ACCEPTED` | `UNDERWRITING` | 不存在有效保单 |
| `UNDERWRITING` | `WAIT_AUDIT` | 承保完成 |
| `WAIT_AUDIT` | `ISSUE` | 下发问题件 |
| `ISSUE` | `WAIT_AUDIT` | 审核系统关闭问题件 |
| `ISSUE` | `PENDING_ACCEPT` | 租户补材 |
| `WAIT_AUDIT` | `COMPLETED` | 审核签收完成 |

`COMPLETED` 只能作为目标状态，不再提供任何离开该状态的领域方法。`isReusableAfterIntake()` 增加 `COMPLETED`，使终态申请与 `ACCEPTED`、`UNDERWRITING`、`WAIT_AUDIT` 一样命中签收可复用快照。

### 3.2 聚合根字段与行为

新增字段：

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `completedAt` | `LocalDateTime` | `COMPLETED` 时非空，其他状态为空 |

`restore` 签名在 `claimApplicationAttempt` 之后增加 `completedAt`，持久化层显式传入，其他层不得调用。

新增行为：

```java
public void completeAudit() {
    ensureStatus(ClaimApplicationStatus.WAIT_AUDIT, "完成审核签收");
    if (hasOpenIssue()) {
        throw new DomainException(ClaimApplicationErrorCode.CLAIM_APPLICATION_STATUS_INVALID,
                "存在未关闭问题件，不允许完成审核签收，claimApplicationNo=" + claimApplicationNo);
    }
    status = ClaimApplicationStatus.COMPLETED;
    completedAt = LocalDateTime.now();
}

public boolean hasOpenIssue() { ... }
public boolean isCompleted() { return status == ClaimApplicationStatus.COMPLETED; }
```

`raiseIssue` 继续只允许 `WAIT_AUDIT`，因此 `COMPLETED` 下发问题件天然被拒绝。`closeIssue` 对已关闭问题件仍先返回 `false`，不校验状态；未关闭问题件仍要求 `ISSUE`，因此 `COMPLETED` 上的未关闭问题件只能通过数据修复处理。

### 3.3 问题件实体

`ClaimApplicationIssue` 用两个字段替换 `auditor`：

| 字段 | 约束 |
| --- | --- |
| `raisedBy` | 下发人，必填，最大 128 字符，落库后不可变 |
| `closedBy` | 关闭人，可空，未关闭时为空 |

- `create(issueNo, reason, raisedBy, issueContent)` 写入 `raisedBy`。
- `close(closedBy)` 只写 `closedBy`、`closedAt` 与状态，不再修改 `raisedBy`。
- `restore` 同时接收 `raisedBy` 与 `closedBy`；历史数据 `raisedBy` 取原 `auditor`，`closedBy` 允许为空。
- 租户补材关闭问题件仍使用平台常量 `ClaimApplication.SUPPLEMENT_AUDITOR` 作为 `closedBy`。

### 3.4 一致性不变量

- `COMPLETED` 必须有 `completedAt`，非 `COMPLETED` 必须没有 `completedAt`；仓储保存前校验，违反时抛出 `IllegalStateException`，不写库。
- 同一申请最多一个未关闭问题件，继续由聚合行为与生成列唯一索引共同保证。
- 案件关联与申请状态的一致性由审核签收事务保证，不在 `ClaimCaseRelation` 上增加状态字段。

## 4. 审核签收完成链路

### 4.1 用例分工

`AuditSignedUseCase`：

```text
if 消费登记已存在 → SKIPPED
return transactionService.record(command)
```

用例不再预加载聚合，避免“事务外校验、事务内已变化”的重复读。申请不存在、租户不一致、状态不合法全部由事务服务在事务内判定。

### 4.2 事务服务

`AuditSignedTransactionService.record` 保持 `@Transactional`，执行顺序：

```text
1. register(consumerGroup, eventId, claimApplicationNo)
   返回 false → SKIPPED（重复事件，不写任何数据）
2. findByClaimApplicationNo
   不存在 → ApplicationException(CLAIM_APPLICATION_NOT_FOUND)
3. tenantCode 不一致 → ApplicationException(AUDIT_SIGNED_TENANT_MISMATCH)
4. 状态既不是 WAIT_AUDIT 也不是 COMPLETED → ApplicationException(AUDIT_SIGNED_STATUS_INVALID)
5. WAIT_AUDIT 且存在未关闭问题件 → ApplicationException(AUDIT_SIGNED_STATUS_INVALID)
6. 查询已登记案件号，计算缺失案件号并 insertAll
7. 写 STARTED 轨迹
8. WAIT_AUDIT：completeAudit() + save，save 返回 false → 抛 AuditSignedConflictException
9. COMPLETED：不保存聚合，只追加缺失关系
10. 写结束轨迹并返回结果
```

任何异常向上抛出，Spring 回滚整个事务，包括第 1 步的消费登记与第 6 步的案件关联。

### 4.3 结果语义

`AuditSignedOutcome` 增加 `COMPLETED`：

| 结果 | 条件 |
| --- | --- |
| `COMPLETED` | 本次从 `WAIT_AUDIT` 迁移到 `COMPLETED`，无论是否有新增案件号 |
| `RECORDED` | 已是 `COMPLETED`，本次补充了至少一个新案件号 |
| `REUSED` | 已是 `COMPLETED`，本次案件号全部已登记 |
| `SKIPPED` | 同一 `eventId` 已登记消费 |

轨迹结束动作：迁移或新增关系写 `COMPLETED`，全部已存在写 `REUSED`。

### 4.4 并发与幂等矩阵

| 场景 | 结果 |
| --- | --- |
| 同一 `eventId` 重复投递 | 消费登记返回 `false`，返回 `SKIPPED` |
| 同一申请两个不同 `eventId` 并发 | 最多一方 `save` 成功；另一方版本冲突回滚后重试，重试看到 `COMPLETED` 只补关系 |
| 两个事件包含相同案件号 | 唯一键冲突使后到事务回滚；重试时已存在关系不再插入 |
| 问题件在签收前被打开 | 状态为 `ISSUE`，拒绝签收；问题件关闭后重放消息 |
| 补偿与 MQ 同时完成 | 只有一方迁移状态，另一方按 `COMPLETED` 补齐关系 |
| 数据库异常或版本冲突 | 全部回滚，消息按既有重投与死信机制处理 |

`AuditSignedConflictException` 只作为事务回滚与消费重试信号，不映射 HTTP 状态，也不写入消费登记。

### 4.5 消息约束

`AuditSignedCommand` 增加紧凑构造器，对 `caseNos` 统一去空白、去重、校验非空且每条不超过 64 字符。审核签收消息装配器保留现有 `AUDIT_SIGNED_INVALID` 错误码与报文级校验；补偿链路直接构造命令时复用同一约束，非法案件号按查询失败处理，不进入签收事务。

## 5. 问题件交互调整

### 5.1 字段映射

| 入口 | 原字段 | 新语义 |
| --- | --- | --- |
| 问题件下发 HTTP `auditor` | 审核人员 | 映射为 `raisedBy` |
| 问题件关闭 HTTP `auditor` | 处理人 | 映射为 `closedBy` |
| 租户补材 | 无 | 关闭人固定为 `TENANT_SUPPLEMENT` |
| 问题件列表查询 | `auditor` | 返回 `raisedBy` 与 `closedBy` |
| 按案件号查询问题件 | 不返回人员 | 契约不变 |

### 5.2 行为规则

- `raiseIssue` 只允许 `WAIT_AUDIT` 且无未关闭问题件，下发后进入 `ISSUE`。
- `closeIssue` 对已关闭问题件返回 `false`；未关闭问题件要求 `ISSUE`，关闭后回到 `WAIT_AUDIT`。
- `supplement` 要求 `ISSUE` 且存在未关闭问题件，关闭未关闭问题件后进入 `PENDING_ACCEPT`，人员保持不变。
- `COMPLETED` 上不存在未关闭问题件；对已完成的申请重复提交按 `REUSED` 处理，不进入 `reapply` 或 `supplement`。

## 6. `WAIT_AUDIT` 滞留补偿

### 6.1 查询端口与模型

新增 `usecase/audit/port/AuditSignedStatusQuery`：

```java
AuditSignedStatus query(String claimApplicationNo);
```

新增 `usecase/audit/model/AuditSignedStatus` 密封接口：

```java
sealed interface AuditSignedStatus {
    record Signed(List<String> caseNos) implements AuditSignedStatus { }
    record Processing() implements AuditSignedStatus { }
    record NotFound() implements AuditSignedStatus { }
}
```

`Signed` 构造时复制案件号列表；空列表由补偿用例按查询失败处理，不完成申请。

### 6.2 本地实现与配置

`LocalAuditSignedStatusQuery` 继承既有 `AbstractLocalIntegration`，交互类型使用 `AUDIT_SIGNED`，按配置返回：

| 配置 | 说明 |
| --- | --- |
| `claim.integration.local.audit-signed-result` | `PROCESSING`、`SIGNED`、`NOT_FOUND`，默认 `PROCESSING` |
| `claim.integration.local.audit-signed-case-nos` | `SIGNED` 时返回的案件号列表 |
| `claim.integration.local.fail-audit-signed-query` | 开启后查询固定抛出异常，用于验证补偿重试 |

`SIGNED` 但案件号为空时本地实现记录错误日志并抛出 `IllegalStateException`，补偿用例按查询失败处理，保持 `WAIT_AUDIT`。

### 6.3 补偿用例

`ClaimApplicationCompensationUseCase` 新增：

```java
public int recoverWaitAuditClaimApplications()
```

流程：

```text
按 WAIT_AUDIT + updated_at <= now - waitAuditTimeout 批量查询
for each 申请:
    try:
        status = auditSignedStatusQuery.query(claimApplicationNo)
        switch status:
            Signed(caseNos):
                案件号为空 → 记录 WARN + FAILED AUDIT_SIGNED 轨迹，保持 WAIT_AUDIT
                否则 auditSignedUseCase.record(补偿命令)
            Processing:
                记录 INFO，不写状态与轨迹
            NotFound:
                以新 traceId 调用 ServiceAuditSystemPort.deliver(...)
                成功写 AUDIT_DELIVERY 的 STARTED/COMPLETED 轨迹
                失败写 FAILED 轨迹，保持 WAIT_AUDIT
    catch RuntimeException:
        记录 WARN + FAILED AUDIT_SIGNED 轨迹，等待下一轮
```

补偿命令：

- `eventId` 使用 `compensation-audit-signed-` + UUID，长度不超过 64。
- `tenantCode` 取聚合租户。
- `traceId` 每轮新生成。
- 签收完成后由签收事务写入 `AUDIT_SIGNED` 轨迹，补偿不再重复写成功轨迹。

### 6.4 调度与配置

- `ClaimApplicationCompensationPolicy` 增加 `waitAuditTimeout()`。
- `ClaimApplicationCompensationProperties` 增加 `waitAuditTimeout`。
- `application.yml` 增加 `claim.compensation.wait-audit-timeout`，默认 `5m`。
- 调度顺序调整为：投递滞留重置 → 待受理 → 已受理 → 承保中 → 等待审核滞留。
- 单条申请异常只记录日志，不中断同批其他申请，也不中断后续补偿步骤。

## 7. 存储与读模型

### 7.1 数据库变更

建表脚本与迁移脚本同步修改：

| 表 | 变更 |
| --- | --- |
| `t_claim_application` | 增加 `completed_at DATETIME(3) NULL`，位于 `claim_application_attempt` 之后 |
| `t_claim_application_issue` | `auditor` 重命名为 `raised_by`，新增 `closed_by VARCHAR(128) NULL` |
| `t_claim_case_relation` | 增加 `KEY idx_case_relation_tenant_case (tenant_code, case_no)` |

迁移脚本 `deploy/mysql/migration/025-add-audit-completion-and-issue-actors.sql`：

- 头部写明一次性执行方式，并要求先用 `information_schema.COLUMNS` / `information_schema.STATISTICS` 判断目标列或索引是否已存在。
- `t_claim_application` 用 `ADD COLUMN` 增加 `completed_at`。
- `t_claim_application_issue` 用 `CHANGE COLUMN auditor raised_by ...` 重命名并保留注释语义，再 `ADD COLUMN closed_by ... NULL`。
- `t_claim_case_relation` 用 `ADD KEY` 增加租户案件号索引。
- 历史问题件 `closed_by` 保持 `NULL`，`raised_by` 继承原 `auditor`，不尝试恢复历史关闭人。

### 7.2 持久化改造

- `ClaimApplicationDO` 增加 `completedAt`，`ClaimApplicationDataConverter` 双向映射。
- `MyBatisClaimApplicationRepository.save` 增加 `completedAt` 条件更新与状态/完成时间一致性校验；`insert` 时 `completedAt` 必须为空。
- `ClaimApplicationIssueDO` 用 `raisedBy` 映射 `raised_by`、`closedBy` 映射 `closed_by`。
- `saveIssues` 插入时写 `raisedBy` 与 `closedBy`；更新时只写 `status`、`is_open`、`closedBy`、`closedAt`，不更新 `raisedBy`。
- `MyBatisClaimApplicationDetailQuery.toIssueView` 读取 `raisedBy` 与 `closedBy`；`toSummary` 读取 `completedAt`。

### 7.3 读模型与接口

- `ClaimApplicationSummary` 末尾增加 `completedAt`。
- `IssueView` 用 `raisedBy`、`closedBy` 替换 `auditor`，字段顺序为：`issueNo`、`reason`、`raisedBy`、`issueContent`、`status`、`closedBy`、`closedAt`、`createdAt`。
- 详情查询响应中的申请摘要自动带出 `completedAt`；问题件列表响应自动带出 `raisedBy` 与 `closedBy`，原 `auditor` 字段移除。
- 按案件号查询问题件接口不增加人员字段，`COMPLETED` 申请返回 `hasOpenIssue=false`。
- 空完成时间、空关闭人按 `null` 序列化，不改变 `ApiResponse` 包裹结构。

### 7.4 签收可复用快照

- `isReusableAfterIntake()` 纳入 `COMPLETED` 后，终态申请写入可复用快照，TTL 与其他安全状态一致。
- 审核完成不需要主动失效快照：`COMPLETED` 仍是可复用状态，命中后只写复用轨迹并返回既有 `claimApplicationNo`。
- 已完成申请携带平台申请编号提交时走数据库读取，因状态既不可重新申请也不可补材，返回 `REUSED`。

## 8. 错误码与契约

### 8.1 错误码

| 错误码 | 场景 | HTTP 映射 |
| --- | --- | --- |
| `AUDIT_SIGNED_TENANT_MISMATCH` | 消息租户与申请租户不一致 | 400 |
| `AUDIT_SIGNED_STATUS_INVALID` | 申请状态不允许签收完成，或 `WAIT_AUDIT` 存在未关闭问题件 | 409 |

两者加入 `ClaimApplicationAsyncErrorCode` 并登记 `BusinessErrorStatusRegistry`。MQ 消费失败继续由 `AuditSignedConsumer` 记录一条 ERROR 后原样抛出，重投耗尽进入既有死信主题。

### 8.2 契约不变项

- 审核签收 Topic `claim_application_audit_signed_v1`、消费者组 `claim-application-audit-signed-v1`、消息信封与 `payload.caseNos` 不变。
- 问题件下发、关闭 HTTP 路径与请求字段 `auditor` 不变。
- 问题件按案件号查询路径与响应字段不变。
- 不新增审核完成对外通知 Topic。

### 8.3 契约变更项

- 审核签收首次在 `WAIT_AUDIT` 完成后，申请状态变为 `COMPLETED`。
- 详情摘要新增 `completedAt`。
- 问题件列表视图新增 `raisedBy`、`closedBy`，移除 `auditor`；该接口由 024 新增，当前无外部消费者。

## 9. 对现有功能的影响

- 024 的签收、材料处理、承保分流、问题件读写与读模型结构保持不变，只在既有链路上补终态与人员字段。
- 审核签收由纯事实记录变为状态迁移，`AuditSignedOutcome` 新增枚举值；现有日志与测试同步更新。
- `t_claim_application_issue` 列重命名需要与代码同一发布窗口上线，迁移脚本先执行、服务后发布。
- 历史问题件关闭人为空，查询返回 `closedBy=null`，不影响 `raisedBy` 展示。
- 补偿新增第五类滞留处理，默认阈值与批量沿用现有配置风格；`COMPLETED` 不参与任何滞留扫描。

## 10. 验证设计

### 10.1 单元测试

| 范围 | 覆盖 |
| --- | --- |
| 领域状态机 | `WAIT_AUDIT → COMPLETED`、完成时间写入、`ISSUE`/`ACCEPTED` 拒绝完成、存在未关闭问题件拒绝完成、`COMPLETED` 拒绝重新申请/补材/下发问题件 |
| 问题件实体 | 关闭只写 `closedBy`、`raisedBy` 保持、未关闭 `closedBy` 为空、历史反水化允许空关闭人 |
| 签收事务 | 租户不一致、状态不合法、`WAIT_AUDIT` 完成、`COMPLETED` 只补关系、重复事件跳过、版本冲突抛内部异常、事务服务不吞异常 |
| 签收用例 | 消费预判与事务服务委托，不再预加载聚合 |
| 补偿 | 已签收推进完成、处理中保持、未找到重新交付、查询失败记录轨迹、空案件号不完成、单条失败不影响同批 |
| 查询 | 摘要完成时间、问题件下发人/关闭人、`null` 值映射 |
| 错误码 | 新错误码状态映射完整性测试通过 |

### 10.2 架构测试

沿用现有 ArchUnit 规则：端口与端口模型位于 `usecase/audit/{port,model}`，基础设施实现不得依赖用例编排；查询链路不得加载聚合或写轨迹；问题件关闭仍只能由聚合根调用。

### 10.3 运行栈黄金路径

在 JDK 21、MySQL/Redis/RocketMQ 运行栈上验证：

1. 有保单链路：签收 → 材料成功 → `ACCEPTED` → `WAIT_AUDIT` → 审核签收 → `COMPLETED`，`completed_at` 非空，案件关联完整。
2. 无保单链路：签收 → `UNDERWRITING` → 承保完成 → `WAIT_AUDIT` → 审核签收 → `COMPLETED`。
3. 问题件链路：下发 → `ISSUE` → 审核关闭 → `WAIT_AUDIT` → 审核签收 → `COMPLETED`，`raised_by` 与 `closed_by` 分别正确。
4. 补材链路：`ISSUE` 补材 → `PENDING_ACCEPT` → 重新处理 → 审核签收 → `COMPLETED`，补材关闭 `closed_by=TENANT_SUPPLEMENT`。
5. 重复与并发：同一 `eventId` 重投 `SKIPPED`；不同 `eventId` 只补缺失案件号；`COMPLETED` 后新增案件号只追加关系。
6. 异常：租户不一致与 `ISSUE` 状态签收重投耗尽进入死信；数据库无业务写入。
7. 补偿：将本地审核签收查询配置为 `SIGNED` 后，`WAIT_AUDIT` 滞留申请被补偿完成；配置为 `NOT_FOUND` 时重新交付且保持状态；配置为查询失败时下一轮重试。
8. 查询：详情摘要返回 `completedAt`；问题件列表返回 `raisedBy`/`closedBy`；跨租户查询仍 404。

## 11. 文档同步

- `docs/architecture/claim-application-architecture.md`：状态机、角色职责、审核签收链路、补偿与错误码。
- `docs/integration/claim-application-integration-contract.md`：审核签收完成语义、可复用状态、问题件响应字段、错误码。
- `docs/integration/claim-application-issue-integration-contract.md`：下发人与关闭人语义，请求字段不变。
- `docs/testing/audit-signed-and-issue-query.md`：完成链路、重复消息、租户不一致、补偿与查询字段验证步骤。
- `docs/operations/dead-letter-alert.md`：新增错误码的死信排查说明（如已列举消费错误码）。
- `docs/architecture/code-navigation.md`：新增端口、本地实现与补偿入口的定位（如已列举目录）。
