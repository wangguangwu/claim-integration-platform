# 025 理赔申请全链路编排、问题件交互与审核完成 · 任务清单

## 执行顺序与编译检查点

- 批次顺序即执行顺序。
- T1–T5 是数据库、领域模型与读模型的同一次切换：T5 完成时必须恢复模块编译通过。
- T6–T7 完成审核签收完成链路，T8–T9 在其上增加 `WAIT_AUDIT` 补偿。
- T10 收口问题件字段语义与签收复用验证。
- T11–T13 依次完成测试补齐、文档同步与运行栈回归。

## 任务批次

### Batch 1：数据库与领域模型

#### T1：数据库结构与迁移脚本
- 产物：更新后的建表脚本与 025 迁移脚本。
- 路径：`deploy/mysql/init/02-application-schema.sql`、`deploy/mysql/migration/025-add-audit-completion-and-issue-actors.sql`。
- 完成条件：
  - `t_claim_application` 增加 `completed_at DATETIME(3) NULL`，位置在 `claim_application_attempt` 之后。
  - `t_claim_application_issue` 的 `auditor` 重命名为 `raised_by`，新增 `closed_by VARCHAR(128) NULL`，注释分别为“问题件下发人标识”和“问题件关闭人标识”。
  - `t_claim_case_relation` 增加 `KEY idx_case_relation_tenant_case (tenant_code, case_no)`。
  - 迁移脚本头部写明一次性执行方式；每项变更执行前用 `information_schema.COLUMNS` 或 `information_schema.STATISTICS` 判断是否已存在。
  - 历史问题件 `closed_by` 为 `NULL`，`raised_by` 继承原 `auditor` 值。

#### T2：状态机新增终态与完成行为
- 产物：`ClaimApplicationStatus`、`ClaimApplication` 的终态行为。
- 路径：`domain/claim/ClaimApplicationStatus.java`、`domain/claim/ClaimApplication.java`。
- 完成条件：
  - `ClaimApplicationStatus` 新增 `COMPLETED`，枚举注释说明终态语义；`isReusableAfterIntake()` 纳入 `COMPLETED`。
  - `ClaimApplication` 新增可空 `completedAt` 字段，`restore` 在 `claimApplicationAttempt` 之后接收该字段。
  - 新增 `completeAudit()`：仅允许 `WAIT_AUDIT`，存在未关闭问题件时拒绝，成功后写入 `COMPLETED` 与完成时间。
  - 新增 `hasOpenIssue()` 与 `isCompleted()`。
  - `reapply`、`supplement`、`raiseIssue` 在 `COMPLETED` 上均被既有状态校验拒绝。
  - 同步适配 `ClaimApplicationIssueTransitionTest` 的 `restore` 调用，保证测试编译通过。

#### T3：问题件下发人与关闭人拆分
- 产物：`ClaimApplicationIssue` 字段与行为改造。
- 路径：`domain/claim/issue/ClaimApplicationIssue.java`、`domain/claim/ClaimApplication.java`。
- 完成条件：
  - `auditor` 字段替换为 `raisedBy` 与可空 `closedBy`。
  - `create` 接收 `raisedBy`，`restore` 同时接收 `raisedBy` 与 `closedBy`，`close` 只写 `closedBy`、`closedAt` 与状态。
  - `raisedBy` 必填且不超过 128 字符；`close` 的关闭人必填。
  - `ClaimApplication.raiseIssue`、`closeIssue`、`supplement` 的参数名与注释同步为下发人/关闭人语义，补材关闭仍使用 `TENANT_SUPPLEMENT`。
  - 同步适配领域测试与问题件相关用例中的 `create`、`close`、`getAuditor` 引用，保证模块编译通过。

### Batch 2：持久化与读模型

#### T4：聚合持久化映射与仓储保存
- 产物：申请主表、问题件表的数据对象、转换器与仓储。
- 路径：`infrastructure/claim/persistence/{ClaimApplicationDO,ClaimApplicationDataConverter,MyBatisClaimApplicationRepository,ClaimApplicationIssueDO,ClaimApplicationIssueConverter}.java`。
- 完成条件：
  - `ClaimApplicationDO` 映射 `completed_at`，转换器双向读写；`insert` 时完成时间必须为空。
  - `MyBatisClaimApplicationRepository.save` 更新 `completed_at`，并在写库前校验：`COMPLETED` 必须有完成时间，非 `COMPLETED` 不得有完成时间，违反时抛出 `IllegalStateException` 且不写任何表。
  - `ClaimApplicationIssueDO` 用 `raisedBy` 映射 `raised_by`、`closedBy` 映射 `closed_by`，转换器按新 `restore` 签名重建。
  - `saveIssues` 插入时写下发人与关闭人；更新时只写状态、未关闭标记、关闭人与关闭时间，不更新下发人。
  - `restore` 调用处补齐 `completedAt` 参数。

#### T5：读模型与查询响应字段
- 产物：详情摘要、问题件视图与查询映射。
- 路径：`usecase/query/model/ClaimApplicationSummary.java`、`usecase/query/model/IssueView.java`、`infrastructure/query/persistence/MyBatisClaimApplicationDetailQuery.java`。
- 完成条件：
  - `ClaimApplicationSummary` 末尾增加 `completedAt`，`toSummary` 从 DO 读取。
  - `IssueView` 用 `raisedBy`、`closedBy` 替换 `auditor`，字段顺序为 `issueNo`、`reason`、`raisedBy`、`issueContent`、`status`、`closedBy`、`closedAt`、`createdAt`。
  - `toIssueView` 与全部 `IssueView` 构造处同步新签名；问题件列表响应自动带出两个人员字段，原 `auditor` 字段消失。
  - 按案件号查询问题件响应与渲染器保持不变；`COMPLETED` 申请查询返回空未关闭问题件。
  - 同步适配查询用例测试的 `IssueView` 构造，保证模块编译通过。

### Batch 3：审核签收完成链路

#### T6：审核签收命令、结果与错误码
- 产物：命令约束、结果枚举与新异步错误码。
- 路径：`usecase/audit/command/AuditSignedCommand.java`、`usecase/audit/result/AuditSignedOutcome.java`、`shared/contract/ClaimApplicationAsyncErrorCode.java`、`interfaces/web/BusinessErrorStatusRegistry.java`。
- 完成条件：
  - `AuditSignedCommand` 紧凑构造器对 `caseNos` 去空白、去重、校验非空且每条不超过 64 字符；已规范化列表不可变。
  - `AuditSignedOutcome` 新增 `COMPLETED`，枚举注释说明与 `RECORDED`/`REUSED` 的区别。
  - `ClaimApplicationAsyncErrorCode` 新增 `AUDIT_SIGNED_TENANT_MISMATCH` 与 `AUDIT_SIGNED_STATUS_INVALID`。
  - `BusinessErrorStatusRegistry` 分别登记 `400` 与 `409`，错误码映射完整性测试通过。

#### T7：审核签收事务与用例改造
- 产物：完成语义的审核签收事务服务与简化后的用例。
- 路径：`usecase/audit/AuditSignedTransactionService.java`、`usecase/audit/AuditSignedUseCase.java`、`usecase/audit/AuditSignedConflictException.java`。
- 完成条件：
  - `AuditSignedUseCase` 只保留消费登记预判与事务服务委托，不再注入 `ClaimApplicationRepository`。
  - 事务服务在同一 `@Transactional` 内依次完成：消费登记、聚合加载、租户校验、状态校验、未关闭问题件校验、缺失案件号追加、开始轨迹、`WAIT_AUDIT` 完成迁移与保存、结束轨迹。
  - 申请不存在抛 `CLAIM_APPLICATION_NOT_FOUND`；租户不一致抛 `AUDIT_SIGNED_TENANT_MISMATCH`；状态不合法或 `WAIT_AUDIT` 存在未关闭问题件抛 `AUDIT_SIGNED_STATUS_INVALID`。
  - `WAIT_AUDIT` 调用 `completeAudit()` 后 `save` 返回 `false` 时抛 `AuditSignedConflictException`，事务整体回滚；`COMPLETED` 只追加缺失关系，不保存聚合。
  - 结果按设计返回 `COMPLETED`、`RECORDED`、`REUSED`、`SKIPPED`；轨迹结束动作按是否发生迁移或新增关系写 `COMPLETED`，否则写 `REUSED`。
  - 重写 `AuditSignedTransactionServiceTest` 与 `AuditSignedUseCaseTest`，覆盖完成、重复、租户不一致、状态不合法、未关闭问题件与版本冲突。

### Batch 4：`WAIT_AUDIT` 滞留补偿

#### T8：审核签收状态查询端口与本地实现
- 产物：查询端口、结果模型、本地实现与配置。
- 路径：`usecase/audit/port/AuditSignedStatusQuery.java`、`usecase/audit/model/AuditSignedStatus.java`、`infrastructure/audit/external/LocalAuditSignedStatusQuery.java`、`infrastructure/integration/config/LocalIntegrationProperties.java`、`application.yml`。
- 完成条件：
  - `AuditSignedStatus` 为密封接口，包含 `Signed(List<String> caseNos)`、`Processing`、`NotFound` 三种结果；`Signed` 构造时复制列表。
  - `LocalAuditSignedStatusQuery` 继承 `AbstractLocalIntegration`，按配置返回三种结果；查询失败开关开启时抛出异常；`SIGNED` 但案件号为空时记录错误日志并抛出 `IllegalStateException`。
  - `LocalIntegrationProperties` 增加 `auditSignedResult`、`auditSignedCaseNos`、`failAuditSignedQuery`，默认 `PROCESSING`、空列表、`false`。
  - `application.yml` 的 `claim.integration.local` 增加对应占位符。

#### T9：补偿用例、轨迹与调度
- 产物：`WAIT_AUDIT` 滞留补偿与第五类调度步骤。
- 路径：`usecase/integration/compensation/ClaimApplicationCompensationUseCase.java`、`usecase/integration/compensation/ClaimApplicationCompensationTransactionService.java`、`usecase/integration/compensation/port/ClaimApplicationCompensationPolicy.java`、`infrastructure/integration/config/ClaimApplicationCompensationProperties.java`、`interfaces/integration/scheduling/ClaimApplicationCompensationScheduler.java`、`application.yml`。
- 完成条件：
  - 补偿策略增加 `waitAuditTimeout()`，配置增加 `wait-audit-timeout`，`application.yml` 默认 `5m` 并更新“五类滞留”注释。
  - 新增 `recoverWaitAuditClaimApplications()`：按 `WAIT_AUDIT` 与更新时间阈值批量查询，单条异常不中断同批。
  - 已签收且案件号非空时以 `compensation-audit-signed-` + UUID 作为事件标识调用审核签收用例；案件号为空按查询失败处理，保持 `WAIT_AUDIT`。
  - 处理中只记录 INFO；未找到时以新 traceId 重新调用审核交付端口，成功与失败分别写 `AUDIT_DELIVERY` 轨迹；查询失败与签收失败写 `FAILED` 的 `AUDIT_SIGNED` 轨迹。
  - `ClaimApplicationCompensationTransactionService.recordAttempt` 接收 `InteractionType` 参数，承保补偿调用处传 `UNDERWRITING`。
  - 调度器在承保中补偿之后执行等待审核滞留补偿。
  - 新增补偿用例测试，覆盖已签收、处理中、未找到、查询失败与空案件号。

### Batch 5：问题件用例与签收复用收口

#### T10：问题件用例语义与签收复用验证
- 产物：问题件命令注释、用例参数语义与终态复用测试。
- 路径：`usecase/issue/command/*.java`、`usecase/issue/ClaimApplicationIssueIntakeUseCase.java`、`usecase/issue/ClaimApplicationIssueCloseUseCase.java`、`usecase/intake/ClaimApplicationIntakeUseCaseTest.java`。
- 完成条件：
  - 下发命令的 `auditor` 注释改为“下发人”，关闭命令的 `auditor` 改为“关闭人”，HTTP 请求字段保持不变。
  - 下发用例传给 `raiseIssue` 的值语义为下发人，关闭用例传给 `closeIssue` 的值语义为关闭人；日志与错误信息同步。
  - 新增或扩展签收测试：`COMPLETED` 申请携带与不携带平台申请编号均返回 `REUSED`，不触发重新申请与补材。
  - 新增或扩展问题件测试：关闭后 `raisedBy` 保持不变、`closedBy` 为本次关闭人；未关闭问题件 `closedBy` 为空。

### Batch 6：测试、文档与回归

#### T11：单元与架构测试补齐
- 产物：新增与更新的测试类。
- 路径：`src/test/java/com/wangguangwu/claim/application/**`。
- 完成条件：
  - 领域测试覆盖 `WAIT_AUDIT → COMPLETED`、完成时间写入、非 `WAIT_AUDIT` 拒绝完成、存在未关闭问题件拒绝完成、`COMPLETED` 拒绝重新申请/补材/下发问题件。
  - 审核签收事务测试覆盖租户不一致、状态不合法、完成迁移、仅补关系、重复事件、版本冲突与轨迹写入。
  - 补偿测试覆盖三种查询结果、查询失败、空案件号与单条失败隔离。
  - 查询测试覆盖 `completedAt`、`raisedBy`、`closedBy` 与空值映射。
  - 现有 ArchUnit 规则全部通过；如新增依赖违反分层，优先调整包位置而不是放宽规则。
  - `BusinessErrorStatusRegistryTest` 覆盖两个新错误码。

#### T12：契约与架构文档同步
- 产物：架构、集成契约、测试与运维文档更新。
- 路径：`docs/architecture/claim-application-architecture.md`、`docs/integration/claim-application-integration-contract.md`、`docs/integration/claim-application-issue-integration-contract.md`、`docs/testing/audit-signed-and-issue-query.md`、`docs/operations/dead-letter-alert.md`、`docs/architecture/code-navigation.md`。
- 完成条件：
  - 状态机表与图加入 `COMPLETED`，删除“审核签收不改变状态”的旧描述。
  - 审核签收章节说明完成语义、事务边界、租户与状态校验、可复用状态与错误码。
  - 问题件文档说明请求字段 `auditor` 不变、响应字段改为 `raisedBy`/`closedBy`、租户补材关闭人常量不变。
  - 测试文档补充完成链路、重复消息、租户不一致、补偿三种结果与查询字段验证步骤。
  - 死信与导航文档补充新错误码与新增类的位置（仅修改已列举相关内容的章节）。

#### T13：构建与运行栈回归
- 产物：构建结果与五类黄金路径验证记录。
- 路径：Maven 全量构建、`deploy` 运行栈、验证文档。
- 完成条件：
  - 执行 025 迁移脚本，确认三处结构变更存在且历史问题件可读。
  - JDK 21 下全量 Maven 构建通过，既有测试与新增测试全部通过，ArchUnit 通过。
  - 重启服务后完成有保单、无保单、问题件、补材与审核完成五类黄金路径。
  - 验证同一 `eventId` 重投 `SKIPPED`、不同 `eventId` 只补缺失案件号、`COMPLETED` 后新增案件号只追加关系。
  - 验证租户不一致与 `ISSUE` 状态签收重投耗尽进入死信，数据库无业务写入。
  - 通过本地审核签收查询配置验证已签收、未找到与查询失败三种补偿结果。
  - 验证详情摘要 `completedAt`、问题件 `raisedBy`/`closedBy` 与跨租户 404。
  - 无遗留 TODO、临时代码与无关格式化改动。
