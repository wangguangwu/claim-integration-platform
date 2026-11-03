# 027 架构减负与边界修正任务

## T1 唯一键冲突信号与仓储翻译

- 产物：`shared/exception/RepositoryConflictException.java`；`MyBatisClaimApplicationRepository.insert/save`、`ClaimCaseRelationRepositoryImpl.insertAll` 捕获 `DuplicateKeyException` 并翻译。
- 完成条件：唯一键冲突在 `infrastructure/persistence` 内被翻译，message 含申请编号与租户上下文，原始异常作为 cause；版本不命中仍返回 `false`。

## T2 应用用例改捕共享异常

- 产物：`ClaimApplicationIntakeUseCase`、`ClaimApplicationIssueIntakeUseCase` 改为捕获 `RepositoryConflictException`；`resolveAfterDuplicateKey` 更名为 `resolveAfterKeyConflict`。
- 完成条件：`application` 主源码无 `org.springframework.dao` 引用；签收冲突重读与问题件幂等分支行为不变；两个用例的既有测试同步更新并通过。

## T3 领域时间参数化

- 产物：`ClaimApplication.completeAudit/closeIssue/reapply/supplement`、`Material.softDelete`、`ClaimApplicationIssue.close` 增加时间参数；三个应用调用方按 `Asia/Shanghai` 传入。
- 完成条件：`domain` 主源码无 `LocalDateTime.now()`；时间参数空值被拒绝；完成审核、关闭问题件、重新申请、补材的返回状态与时间字段语义不变；相关测试使用固定时间并通过。

## T4 聚合版本与终态不变量修正

- 产物：`ClaimApplication.bindVersion` 只做空值校验与赋值；`restore` 校验 `COMPLETED` 与 `completedAt` 一致性；删除仓储 `validateCompletionState` 及两处调用。
- 完成条件：版本递增算术只存在于持久化更新语句；`COMPLETED` 与完成时间不一致的数据在还原时被拒绝；仓储不再包含业务不变量；测试辅助方法与版本断言同步更新并通过。

## T5 补偿用例拆分

- 产物：`application/process/compensation` 下 `CompensationDefaults` 与五个处理器；`ClaimApplicationCompensationUseCase` 改为只做委派的门面。
- 完成条件：门面五个公开方法签名不变且方法体只调用处理器；五条恢复流程的扫描状态、超时、批量、日志、错误码与返回条数不变；`ClaimApplicationCompensationScheduler` 无改动；等待审核补偿测试迁移到 `WaitAuditCompensationHandlerTest` 并通过。

## T6 材料批次号内联

- 产物：签收用例内联批次号生成；删除 `MaterialBatchNoGenerator` 与 `UuidMaterialBatchNoGenerator`。
- 完成条件：全仓无批次号端口引用；批次号格式仍为 `B + yyyyMMdd + 32 位十六进制`；同一签收中材料批次与重新申请/补材批次一致；签收用例测试改用参数捕获断言。

## T7 租户协议入口合并

- 产物：`TenantProtocolPipelineRegistry.execute` 承担定位、执行与跨租户校验；删除 `TenantProtocolPipelineFactory`；控制器改依赖注册表。
- 完成条件：全仓无工厂引用；控制器只注入注册表；缺少租户信息、未匹配、配置冲突与转换结果无效的异常码不变。

## T8 ArchUnit 规则与测试收口

- 产物：`ClaimApplicationArchitectureTest` 新增 Spring DAO 依赖规则与领域时钟规则，更新材料软删除、问题件关闭的方法签名规则。
- 完成条件：两条新规则能分别拦截 `org.springframework.dao` 引用与 `LocalDateTime.now()` 调用；原有分层、契约、聚合封装、restore 限制规则全部通过。

## T9 文档同步

- 产物：`system-architecture.md`、`code-navigation.md`、`claim-application-architecture.md`、`ddd-standard-architecture.md`、`audit-signed-and-issue-query.md` 更新。
- 完成条件：协议链路不再出现 `TenantProtocolPipelineFactory`；`adapter` 不再描述“本地批次号”；补偿说明指向门面与处理器；测试类引用与实际文件名一致。

## T10 构建与运行栈回归

- 产物：编译、测试与运行栈验证记录。
- 完成条件：JDK 21 下 `mvn -pl backend/claim-application-service -am test` 全部通过；服务与网关重启后健康检查通过；黄金路径“签收 → 材料处理 → 承保/审核交付 → 问题件与补材 → 审核签收 `COMPLETED`”通过；HTTP、MQ 与数据库契约无变化。
