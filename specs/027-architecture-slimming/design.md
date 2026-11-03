# 027 架构减负与边界修正设计

## 1. 改动模块

| 模块 | 改动 |
| --- | --- |
| `shared/exception` | 新增 `RepositoryConflictException` |
| `domain/claim` | 时间参数化、`bindVersion` 简化、`restore` 增加终态一致性校验 |
| `application/process` | 签收、问题件、审核签收、签收事务服务调整时间与异常捕获 |
| `application/process/compensation` | 新增补偿门面拆分后的五个处理器与共享常量 |
| `infrastructure/persistence` | 唯一键异常翻译、删除终态一致性校验 |
| `application/port`、`infrastructure/adapter` | 删除材料批次号端口与实现 |
| `interfaces/web/tenantprotocol` | 删除流水线工厂，`execute` 合并进注册表 |
| `interfaces/web` | 签收控制器改依赖注册表 |
| `ClaimApplicationArchitectureTest` | 新增 Spring DAO 依赖规则与领域时钟规则，更新方法签名规则 |
| 测试与文档 | 迁移补偿测试、更新调用签名与文档引用 |

## 2. 唯一键冲突与持久化技术解耦

### 2.1 新增共享冲突信号

`shared/exception/RepositoryConflictException.java`：

```java
public class RepositoryConflictException extends RuntimeException {

    public RepositoryConflictException(String message) {
        super(message);
    }

    public RepositoryConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

该异常表示“唯一约束冲突”这一持久化事实，不属于 `BusinessException` 体系：未被业务处理的冲突应作为系统错误上抛，不能被映射为业务拒绝。

### 2.2 仓储翻译

- `MyBatisClaimApplicationRepository.insert`：在根表与材料表写入外层捕获 `org.springframework.dao.DuplicateKeyException`，抛出 `RepositoryConflictException`，message 包含 `claimApplicationNo`、`tenantCode`、`externalClaimApplicationNo`，原始异常作为 cause。
- `MyBatisClaimApplicationRepository.save`：在根表条件更新与子表写入外层同样翻译；版本不命中仍返回 `false`，两条语义保持独立。
- `ClaimCaseRelationRepositoryImpl.insertAll`：逐条插入外层翻译，供调用方按消息重投语义处理。

翻译只发生在 `infrastructure/persistence`，应用层看不到 Spring DAO 类型。

### 2.3 用例捕获

- `ClaimApplicationIntakeUseCase.executeWithoutPlatformNumber`：`catch (DuplicateKeyException)` 改为 `catch (RepositoryConflictException)`；`ClaimApplicationIntakeTransactionService.resolveAfterDuplicateKey` 更名为 `resolveAfterKeyConflict`，行为不变。
- `ClaimApplicationIssueIntakeUseCase.saveOrResolveConflict`：改为捕获 `RepositoryConflictException`，仍按幂等返回。

## 3. 领域时间参数化

### 3.1 方法签名

```java
// ClaimApplication
public void completeAudit(LocalDateTime completedAt)
public boolean closeIssue(String issueNo, String closedBy, LocalDateTime closedAt)
public void reapply(ClaimApplication submitted, String batchNo, LocalDateTime operatedAt)
public void supplement(ClaimApplication submitted, String batchNo, LocalDateTime operatedAt)

// Material
public void softDelete(LocalDateTime deletedAt)

// ClaimApplicationIssue
public void close(String closedBy, LocalDateTime closedAt)
```

- 时间参数统一使用 `AssertUtil.notNull` 校验。
- `reapply` / `supplement` 把 `operatedAt` 传给 `softDelete` 与 `issue.close`，同一业务动作内所有时间点一致。
- 领域层不再持有 `ZoneId`，也不再读取系统时钟。

### 3.2 调用方

| 调用方 | 取值 |
| --- | --- |
| `AuditSignedTransactionService.completeIfAwaitingAudit` | `LocalDateTime.now(BUSINESS_ZONE_ID)` |
| `ClaimApplicationIssueCloseUseCase.close` 及冲突重试分支 | 同上 |
| `ClaimApplicationIntakeTransactionService.intakeExisting` 的重新申请与补材分支 | 同上 |

三个类各自持有 `private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai")`，与现有 `ClaimApplicationLifecycleRecordFactory`、`OutboxRelayUseCase` 的写法一致，不额外引入时钟抽象。

## 4. 聚合与持久化边界修正

### 4.1 版本回填

`ClaimApplication.bindVersion` 简化为：

```java
public void bindVersion(Long version) {
    AssertUtil.notNull(version, "乐观锁版本不能为空");
    this.version = version;
}
```

- 插入后回填 `0L`、保存后回填 `current + 1` 的算术仍由 `MyBatisClaimApplicationRepository` 决定。
- 删除“插入版本或下一版本”的校验与其测试，改为直接断言回填结果。

### 4.2 终态一致性不变量

`ClaimApplication.restore` 在构建聚合前校验：

```java
boolean completed = status == ClaimApplicationStatus.COMPLETED;
boolean hasCompletedAt = completedAt != null;
if (completed != hasCompletedAt) {
    throw new IllegalStateException("理赔申请状态与完成时间不一致，claimApplicationNo=" + claimApplicationNo
            + "，状态=" + status + "，完成时间=" + completedAt);
}
```

- 删除 `MyBatisClaimApplicationRepository.validateCompletionState` 及 `insert`、`save` 中的两处调用。
- 恢复测试辅助方法：`status` 为 `COMPLETED` 时传入固定完成时间，其余状态传 `null`。

## 5. 补偿用例拆分

### 5.1 目标结构

```text
application/process/
├── ClaimApplicationCompensationUseCase.java        门面，仅委派
└── compensation/
    ├── CompensationDefaults.java                   业务时区、请求标识前缀
    ├── OutboxSendingCompensationHandler.java       投递滞留重置
    ├── PendingAcceptCompensationHandler.java       待受理滞留重放
    ├── AcceptedCompensationHandler.java            已受理滞留重新分流
    ├── UnderwritingCompensationHandler.java        承保中滞留恢复
    └── WaitAuditCompensationHandler.java           等待审核滞留恢复
```

新增 `package-info.java` 说明补偿处理器职责与依赖边界。该子包属于 026“层内按角色分包”的延续，本 Spec 评审通过即视为目录调整确认。

### 5.2 门面

`ClaimApplicationCompensationUseCase` 保留五个公开方法，方法体只有一行委派，依赖五个处理器：

```java
public int resetStuckSendingEvents() { return outboxSendingCompensationHandler.reset(); }
public int redrivePendingAcceptClaimApplications() { return pendingAcceptCompensationHandler.redrive(); }
public int redispatchAcceptedClaimApplications() { return acceptedCompensationHandler.redispatch(); }
public int recoverUnderwritingClaimApplications() { return underwritingCompensationHandler.recover(); }
public int recoverWaitAuditClaimApplications() { return waitAuditCompensationHandler.recover(); }
```

`ClaimApplicationCompensationScheduler` 不改。

### 5.3 处理器职责与依赖

| 处理器 | 依赖 | 保留逻辑 |
| --- | --- | --- |
| `OutboxSendingCompensationHandler` | `OutboxEventRepository`、`ClaimApplicationCompensationPolicy` | 超时重置与日志 |
| `PendingAcceptCompensationHandler` | `ClaimApplicationRepository`、`MaterialProcessingUseCase`、`ClaimApplicationCompensationPolicy` | 批量扫描、按“申请编号 + 尝试次数”构造事件标识、单条失败隔离 |
| `AcceptedCompensationHandler` | `ClaimApplicationRepository`、`UnderwritingUseCase`、`ClaimApplicationCompensationPolicy` | 批量扫描、重新分流、单条失败隔离 |
| `UnderwritingCompensationHandler` | `ClaimApplicationRepository`、`UnderwritingSystemPort`、`UnderwritingCallbackUseCase`、`ClaimApplicationLifecycleRecordFactory`、`ClaimApplicationCompensationTransactionService`、`ClaimApplicationCompensationPolicy` | 查询结果分类、完成推进、自动承保重发、轨迹与错误码 |
| `WaitAuditCompensationHandler` | `ClaimApplicationRepository`、`AuditSystemPort`、`AuditSignedUseCase`、`ClaimApplicationLifecycleRecordFactory`、`ClaimApplicationCompensationTransactionService`、`ClaimApplicationCompensationPolicy` | 签收状态分类、完成推进、重新交付、失败轨迹 |

`CompensationDefaults` 提供 `now()` 与 `requestId(claimApplicationNo)`；错误码常量保留在各自处理器内，字符串不得变化。

## 6. 材料批次号内联

删除：

- `application/port/MaterialBatchNoGenerator.java`
- `infrastructure/adapter/UuidMaterialBatchNoGenerator.java`

`ClaimApplicationIntakeUseCase` 内联生成：

```java
private static final String BATCH_NO_PREFIX = "B";
private static final DateTimeFormatter BATCH_NO_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

private String nextBatchNo() {
    return BATCH_NO_PREFIX + LocalDate.now(BUSINESS_ZONE_ID).format(BATCH_NO_DATE_FORMATTER)
            + UUID.randomUUID().toString().replace("-", "");
}
```

批次号在 `intake` 入口生成一次，同时用于 `ClaimApplication.submit` 与 `intakeExisting`，保证聚合内材料批次与重新申请/补材批次一致。

## 7. 租户协议入口合并

`TenantProtocolPipelineRegistry` 新增：

```java
public ClaimApplicationIntakeCommand execute(TenantInfo tenantInfo, String rawPayload) {
    ClaimApplicationIntakeCommand command = getPipeline(tenantInfo).execute(rawPayload);
    if (command == null || !tenantInfo.tenantCode().equals(command.tenantCode())) {
        throw new BusinessException(ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_INVALID,
                "租户申请协议转换结果无效");
    }
    return command;
}
```

- 删除 `TenantProtocolPipelineFactory`。
- `ClaimApplicationIntakeController` 注入并调用 `TenantProtocolPipelineRegistry.execute`。
- 五阶段流水线、租户实现与异常码不动。

## 8. ArchUnit 规则

`ClaimApplicationArchitectureTest` 新增：

1. `applicationAndDomainMustNotDependOnSpringDaoExceptions`：`application..` 与 `domain..` 不得依赖 `org.springframework.dao..`，测试类除外。
2. `domainMustNotReadSystemClock`：`domain..` 不得调用 `java.time.LocalDateTime.now()`，测试类除外。

更新：

- `materialStateChangesOnlyThroughAggregate` 的 `softDelete` 签名改为 `softDelete(LocalDateTime.class)`。
- `issueStateChangesOnlyThroughAggregate` 的 `close` 签名改为 `close(String.class, LocalDateTime.class)`。

## 9. 测试调整

| 测试 | 调整 |
| --- | --- |
| `ClaimApplicationIntakeUseCaseTest` | 移除批次号端口 mock；用参数捕获断言批次号格式与材料批次一致；冲突异常改为 `RepositoryConflictException` |
| `ClaimApplicationIssueIntakeUseCaseTest` | 冲突异常改为 `RepositoryConflictException` |
| `ClaimApplicationIssueTransitionTest` | 时间参数改为固定值；`persisted` 在 `COMPLETED` 时传完成时间；版本测试改为断言回填；新增 `restore` 终态一致性用例 |
| `ClaimApplicationIssueCloseUseCaseTest` | `close` 与时间断言使用固定值 |
| `ClaimApplicationCompensationUseCaseTest` | 迁移为 `compensation/WaitAuditCompensationHandlerTest`，断言与覆盖不变 |
| `ClaimApplicationArchitectureTest` | 新增两条规则，更新两条方法签名规则 |

不删除既有业务覆盖，不降低断言强度。

## 10. 文档同步

- `docs/architecture/system-architecture.md`：删除工厂节点，链路改为“注册表定位并执行”。
- `docs/architecture/code-navigation.md`：异步集成行补充补偿处理器；删除 adapter 的“本地批次号”描述。
- `docs/architecture/claim-application-architecture.md`、`docs/architecture/ddd-standard-architecture.md`：同步删除 adapter 批次号职责。
- `docs/testing/audit-signed-and-issue-query.md`：补偿测试类引用改为处理器测试。

## 11. 对现有功能的影响

- 对外契约、状态机、错误码、消息结构、数据库结构与调度周期不变。
- 冲突识别从 Spring 异常类型切换为共享异常类型，分类逻辑与重试次数不变。
- 领域时间由调用方传入，生产时区与当前实现一致。
- 补偿门面方法签名不变，调度器无需修改。
- 租户协议入口减少一层对象，异常码与响应不变。
