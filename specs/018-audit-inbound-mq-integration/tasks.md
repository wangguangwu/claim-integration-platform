# 018 审核签收 MQ 接入与问题件 HTTP 归位 · 任务清单

## 任务批次

### Batch 1：领域模型与契约

#### T1：新增审核签收交互类型
- 产物：修改 `InteractionType.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/domain/integration/interaction/InteractionType.java`
- 完成条件：
  - 新增 `AUDIT_SIGNED(InteractionDirection.INBOUND, "AUDIT_SYSTEM")`
  - 值级中文 JavaDoc 说明"审核系统回传签收结果及案件号"
  - 编译通过

#### T2：新增错误码
- 产物：修改 `ClaimApplicationAsyncErrorCode.java`、`ClaimApplicationIssueErrorCode.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/shared/contract/`、`domain/issue/exception/`
- 完成条件：
  - `ClaimApplicationAsyncErrorCode` 新增 `AUDIT_SIGNED_INVALID`
  - `ClaimApplicationIssueErrorCode` 新增 `CASE_RELATION_NOT_FOUND`
  - 均含中文 JavaDoc
  - 编译通过

#### T3：案件关系领域对象
- 产物：`ClaimCaseRelation.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/domain/claimcase/ClaimCaseRelation.java`
- 说明：`case` 是 Java 关键字，包名改用 `claimcase`
- 完成条件：
  - 字段：`tenantCode`、`claimApplicationNo`、`caseNo`
  - 静态工厂创建，三个字段非空白校验在构造阶段完成
  - 中文 JavaDoc，类声明后空一行
  - 编译通过

#### T4：案件关系仓储端口
- 产物：`ClaimCaseRelationRepository.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/domain/claimcase/port/ClaimCaseRelationRepository.java`
- 完成条件：
  - `List<String> findCaseNos(String claimApplicationNo)`
  - `Optional<String> findClaimApplicationNo(String tenantCode, String caseNo)`
  - `void insertAll(List<ClaimCaseRelation> relations)`
  - 不依赖 MyBatis、Spring 或 MQ 类型
  - 中文 JavaDoc
  - 编译通过

### Batch 2：持久化

#### T5：案件关系 MyBatis-Plus 持久化
- 产物：`ClaimCaseRelationDO.java`、`ClaimCaseRelationMapper.java`、`ClaimCaseRelationConverter.java`、`ClaimCaseRelationRepositoryImpl.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/infrastructure/claimcase/persistence/`
- 完成条件：
  - `ClaimCaseRelationMapper extends BaseMapper<ClaimCaseRelationDO>`，不写手写 SQL
  - `findCaseNos` 按 `claimApplicationNo` 查询
  - `findClaimApplicationNo` 按 `tenantCode + caseNo` 查询，返回 `Optional<String>`
  - `insertAll` 逐条调用 `BaseMapper.insert`，空集合直接返回
  - 唯一键冲突抛出 `DuplicateKeyException`，不静默吞掉
  - 中文 JavaDoc
  - 编译通过

### Batch 3：审核签收用例

#### T6：审核签收命令与结果
- 产物：`AuditSignedCommand.java`、`AuditSignedOutcome.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/usecase/audit/{command,result}/`
- 完成条件：
  - 命令字段：`claimApplicationNo`、`tenantCode`、`eventId`、`traceId`、`caseNos`
  - 结果枚举：`RECORDED`、`REUSED`、`SKIPPED`
  - 中文 JavaDoc
  - 编译通过

#### T7：审核签收事务服务
- 产物：`AuditSignedTransactionService.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/usecase/audit/AuditSignedTransactionService.java`
- 完成条件：
  - 常量 `CONSUMER_GROUP = "claim-application-audit-signed-v1"`
  - `@Transactional` 方法内依次执行：消费登记 → 查询已有案件号 → 插入缺失案件号 → 写 STARTED 轨迹 → 写 COMPLETED 轨迹
  - 消费登记返回 false 时返回 `SKIPPED`，不写任何数据
  - 全部案件号已存在时返回 `REUSED`
  - 至少插入一个新案件号时返回 `RECORDED`
  - 轨迹类型为 `AUDIT_SIGNED`，`requestId` 使用 `eventId`
  - 中文 JavaDoc
  - 编译通过

#### T8：审核签收用例
- 产物：`AuditSignedUseCase.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/usecase/audit/AuditSignedUseCase.java`
- 完成条件：
  - 消费登记已存在时记录 INFO 并直接返回，不查库
  - 申请不存在时抛出 `ClaimApplicationAsyncException`，错误码 `CLAIM_APPLICATION_NOT_FOUND`，异常信息包含 `claimApplicationNo` 与 `eventId`
  - 申请存在时调用事务服务，记录结果日志
  - 不引用 `ClaimApplicationEventEnvelope`、`Message`、`Map`
  - 中文 JavaDoc
  - 编译通过

#### T9：审核签收 Assembler 与 Consumer
- 产物：`AuditSignedMessageAssembler.java`、`AuditSignedConsumer.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/interfaces/audit/messaging/`
- 完成条件：
  - Assembler 校验信封事件类型为 `AUDIT_SIGNED`；`caseNos` 为非空数组、元素为字符串、去空白后非空且不超过 64 字符、去重
  - 非法载荷抛 `ClaimApplicationAsyncException`，错误码 `AUDIT_SIGNED_INVALID`
  - Consumer 声明 `@Bean Consumer<Message<ClaimApplicationEventEnvelope>> auditSigned()`
  - 失败时记录一条 ERROR（含 `consumerGroup`、`claimApplicationNo`、`eventId`、异常摘要）后原样抛出
  - Consumer 内不包含业务判断
  - 中文 JavaDoc
  - 编译通过

### Batch 4：问题件 HTTP

#### T10：问题件入口路径归位
- 产物：修改 `ClaimApplicationIssueIntakeController.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/interfaces/issue/web/`
- 完成条件：
  - 类级 `@RequestMapping` 改为 `/api/claim/v1/callbacks`
  - 下发路径为 `/audit-issues`，关闭路径为 `/audit-issues/{issueNo}/close`
  - 与 `UnderwritingCallbackController` 的 `/underwriting-completed` 无路径冲突
  - 旧路径 `/api/application/v1/callbacks/**` 不再暴露
  - 业务逻辑、请求与响应结构不变
  - 经网关 `Path=/api/claim/**` 可直接访问
  - 编译通过

#### T11：问题件查询用例
- 产物：
  - 修改 `ClaimApplicationIssueRepository.java` 与 `ClaimApplicationIssueRepositoryImpl.java`
  - 新增 `ClaimApplicationIssueQueryUseCase.java`、`ClaimApplicationIssueQueryResult.java`
- 路径：
  - `domain/issue/`、`infrastructure/issue/persistence/`
  - `usecase/issue/`
- 完成条件：
  - 仓储端口新增 `Optional<ClaimApplicationIssue> findOpenByClaimApplicationNo(String claimApplicationNo)`
  - 实现使用 `is_open = 1` 条件查询
  - 用例先按 `(tenantCode, caseNo)` 反查申请，查不到抛 `ClaimApplicationIssueException(CASE_RELATION_NOT_FOUND)`
  - 查到申请后查询未关闭问题件，组装 `ClaimApplicationIssueQueryResult`
  - 结果字段：`claimApplicationNo`、`caseNo`、`hasOpenIssue`、`issueNo`、`reason`、`raisedAt`
  - 只读，不写交互轨迹，不改状态
  - 中文 JavaDoc
  - 编译通过

#### T12：问题件查询接口与错误映射
- 产物：新增 `ClaimApplicationIssueQueryController.java`；修改 `ClaimApplicationIssueExceptionHandler.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/interfaces/issue/web/`
- 完成条件：
  - `GET /api/claim/v1/cases/{caseNo}/issue`
  - 从 `TenantContext` 获取租户编码，缺失时返回 400
  - 成功时用 `ApiResponse.success(traceId, data)` 渲染
  - 异常处理器新增 `CASE_RELATION_NOT_FOUND` → 404 映射
  - 中文 JavaDoc
  - 编译通过

### Batch 5：配置与文档

#### T13：MQ 与 Topic 配置
- 产物：修改 `application.yml`、`scripts/docker-start.sh`
- 路径：
  - `backend/claim-application-service/src/main/resources/application.yml`
  - `scripts/docker-start.sh`
- 完成条件：
  - `function.definition` 为 `materialProcess;auditDelivery;auditSigned`
  - 新增 `auditSigned-in-0` 的 destination、group、content-type
  - `auditSigned-in-0` 声明 `max-reconsume-times`，支持环境变量覆盖
  - `docker-start.sh` 创建 `claim_application_audit_signed_v1`，8 读 8 写
  - 不新增问题件 Topic 或 binding
  - 原有 binding 配置不变

#### T14：文档与基线修订
- 产物：修改 `specs/002-application-integration-foundation/requirements.md`、`docs/integration/claim-application-integration-contract.md`、`docs/environment/local-infrastructure-health-check.md`；新增 `docs/testing/audit-signed-and-issue-query.md`、`docs/operations/dead-letter-alert.md`
- 完成条件：
  - 002 中将"审核签收和问题件保持 HTTP 回调"改为"审核签收改 MQ，问题件保持 HTTP 并修正路径"，标注由 018 修订
  - 契约文档问题件路径改为 `/api/claim/v1/callbacks/**`，新增查询接口与审核签收 Topic
  - 健康检查清单补齐 `claim_application_audit_signed_v1`
  - 测试文档包含消息样例、发送命令、HTTP 调用示例与期望结果
  - 死信告警文档包含死信主题、消费组、邮件字段、监控配置方式与重放命令
  - 文档中不出现"已实现结案/撤销"的表述

### Batch 6：验证

#### T15：单元测试
- 产物：
  - `AuditSignedMessageAssemblerTest.java`
  - `AuditSignedUseCaseTest.java`
  - `ClaimApplicationIssueQueryUseCaseTest.java`
- 路径：`backend/claim-application-service/src/test/java/com/wangguangwu/claim/application/`
- 完成条件：
  - 多案件号落库数量正确；重复事件返回 `SKIPPED`；部分案件号已存在时只补插缺失项
  - `caseNos` 为空、含空白、超长、非字符串均抛出 `AUDIT_SIGNED_INVALID`
  - 查询用例覆盖：有未关闭问题件、无问题件、案件关系不存在
  - 测试通过

#### T16：构建与运行栈验证
- 产物：无代码产物，输出验证结论
- 完成条件：
  - `JAVA_HOME` 指向 JDK 21 执行全量 Maven 构建成功
  - 现有测试与 ArchUnit 4 条分层规则全部通过
  - 启动中间件与重新打包后的服务，发送审核签收消息并核对数据库
  - 经网关 8080 调用问题件下发、关闭接口，验证 2xx 与 4xx
  - 调用查询接口验证三种结果
  - 构造申请不存在的审核签收消息，确认进入死信主题
  - 旧路径 `/api/application/v1/callbacks/**` 返回 404
