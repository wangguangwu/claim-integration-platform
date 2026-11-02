# 021 DDD 分层边界基线调整 · 设计

## 一、基线变更

本次不是修正单个类的包名，而是替换“端口一律放 domain”的旧基线。旧基线由 [标准 DDD 架构说明](../../docs/architecture/ddd-standard-architecture.md) 明确记录，并在 005 之后把 `OutboxEventPublisher` 从 `usecase` 移回 `domain`。021 改为按“业务归属”判断：业务模型留在 `domain`，业务流程需要的出站能力归 `usecase`。

| 对象 | 旧基线 | 新基线 | 判定理由 |
|---|---|---|---|
| 聚合 Repository | `domain/<聚合>/port` | `domain/<聚合>/port`，不变 | 仓储表达聚合生命周期的持久化契约，属于业务模型边界 |
| 加锁、发号、快照查询 | `domain/claim/port` | `usecase/intake/port` | 解决签收编排的并发与编号问题，不是理赔业务概念 |
| 外部系统调用端口 | `domain/{audit,underwriting,material}/port` | `usecase/{audit,underwriting,material}/port` | 端口描述用例需要的外部能力，由用例决定调用时机与失败语义 |
| 端口查询/请求/结果模型 | `domain/<域>` | `usecase/<域>/model` | 随端口一起被用例与基础设施共享，不能继续留在业务模型包 |
| 配置策略 `*Policy` | `domain` | `usecase/<域>/port` | 策略值是流程阈值，不是业务不变式；配置适配器仍留在 `infrastructure` |
| Outbox、消费去重、补偿、交互轨迹 | `domain/integration` | `usecase/integration` | 平台异步内核是技术能力，不是业务域 |
| `domain/audit`、`domain/underwriting` | 存在，仅放端口与端口模型 | 删除 | 两个域没有聚合、值对象或领域服务，移走端口后为空 |
| `domain/integration` | 存在 | 删除 | 整个包都是技术内核 |
| 配置适配器实现 | 实现 `domain` 的 `*Policy` | 实现 `usecase/<域>/port` 的 `*Policy` | 实现位置与扫描范围不变，只改实现的接口来源 |
| `usecase` 包名 | `usecase` | `usecase`，不改名 | 根包已是 `com.wangguangwu.claim.application`，再叫 `application` 会产生 `application.application`，并破坏 ArchUnit 的 `..application..` 匹配 |
| `infrastructure` 依赖 | 只允许 `domain` | 允许 `usecase/<域>/{port,model}` 与 `domain` | 端口所有权转移后，实现类必须能引用端口；但不允许引用用例编排 |

统一约束：**仅调整职责归属和包依赖，不改变现有业务行为、事务边界和消息语义。**

## 二、目标包结构

```text
application/
├── interfaces/                         # 结构不变
├── domain/
│   ├── claim/
│   │   ├── ClaimApplication.java
│   │   ├── ClaimApplicationStatus.java
│   │   ├── MaterialSubmission.java
│   │   ├── person/
│   │   ├── exception/
│   │   └── port/ClaimApplicationRepository.java
│   ├── claimcase/
│   │   ├── ClaimCaseRelation.java
│   │   └── port/ClaimCaseRelationRepository.java
│   ├── material/
│   │   ├── Material.java
│   │   ├── MaterialStatus.java
│   │   └── port/MaterialRepository.java
│   └── issue/
│       ├── ClaimApplicationIssue.java
│       ├── ClaimApplicationIssueStatus.java
│       ├── port/ClaimApplicationIssueRepository.java
│       ├── event/                      # 含 IssueDomainEventPublisher
│       ├── exception/
│       └── service/IssueDomainService.java
├── usecase/
│   ├── intake/{command,result,model,port}
│   ├── material/{command,result,model,port}
│   ├── underwriting/{result,model,port}
│   ├── audit/{command,result,model,port}
│   ├── issue/                          # 结构不变
│   └── integration/
│       ├── outbox/{model,port}
│       ├── consumption/port
│       ├── compensation/port
│       ├── interaction/{model,port}
│       └── record/
├── infrastructure/                     # 目录不变，仅改实现声明与 import
├── shared/
└── support/
```

`domain` 最终只保留：聚合根与实体、值对象、状态枚举、领域事件、领域服务、聚合 Repository、业务异常与错误码。`domain/audit`、`domain/underwriting`、`domain/integration` 在搬迁后删除。

目标文件数（含 `package-info.java`，以 `find -name '*.java'` 统计）：

| 层 | 调整前 | 调整后 | 变化 |
|---|---:|---:|---|
| `domain` | 57 | 26 | −31 |
| `usecase` | 38 | 69 | +31 |
| `interfaces` | 43 | 43 | 0 |
| `infrastructure` | 45 | 45 | 0 |
| `shared` / `support` / 根包 | 12 | 12 | 0 |
| 合计 | 195 | 195 | 0 |

## 三、四组搬迁清单

每组是一个可独立编译、可独立运行测试的单元。组内顺序固定为：先移动 model/port 类，再修正 `usecase` 引用，再修正 `infrastructure` 引用，最后修正测试引用。

### 3.1 Group A：签收端口与快照模型（7 个类）

| 原路径 | 新路径 |
|---|---|
| `domain/claim/ClaimApplicationIntakeLookup.java` | `usecase/intake/model/ClaimApplicationIntakeLookup.java` |
| `domain/claim/ClaimApplicationIntakeLookupKind.java` | `usecase/intake/model/ClaimApplicationIntakeLookupKind.java` |
| `domain/claim/ClaimApplicationIntakeSnapshot.java` | `usecase/intake/model/ClaimApplicationIntakeSnapshot.java` |
| `domain/claim/ClaimApplicationIntakePolicy.java` | `usecase/intake/port/ClaimApplicationIntakePolicy.java` |
| `domain/claim/port/ClaimApplicationIntakeLock.java` | `usecase/intake/port/ClaimApplicationIntakeLock.java` |
| `domain/claim/port/ClaimApplicationIntakeSnapshotQuery.java` | `usecase/intake/port/ClaimApplicationIntakeSnapshotQuery.java` |
| `domain/claim/port/ClaimApplicationNoGenerator.java` | `usecase/intake/port/ClaimApplicationNoGenerator.java` |

`domain/claim/port/ClaimApplicationRepository.java` 原地保留。`domain/claim` 从 18 个文件降到 11 个。

需要同步 import 的文件：

- `usecase/intake/ClaimApplicationIntakeUseCase.java`
- `usecase/intake/ClaimApplicationIntakeTransactionService.java`
- `usecase/intake/ClaimApplicationIntakeOutcome.java`
- `infrastructure/intake/lock/RedissonClaimApplicationIntakeLock.java`
- `infrastructure/intake/cache/RedisClaimApplicationIntakeSnapshotQuery.java`
- `infrastructure/intake/number/RedisMySqlClaimApplicationNoGenerator.java`
- `infrastructure/intake/config/ClaimApplicationIntakeProperties.java`
- `test/.../usecase/intake/ClaimApplicationIntakeUseCaseTest.java`

`ClaimApplicationIntakeProperties` 从“实现领域策略”改为“实现用例策略”，字段、前缀 `claim.intake`、绑定方式均不变。

### 3.2 Group B：外部能力端口与模型（10 个类）

| 原路径 | 新路径 |
|---|---|
| `domain/audit/AuditDeliveryRequest.java` | `usecase/audit/model/AuditDeliveryRequest.java` |
| `domain/audit/ServiceAuditSystemPort.java` | `usecase/audit/port/ServiceAuditSystemPort.java` |
| `domain/underwriting/UnderwritingResult.java` | `usecase/underwriting/model/UnderwritingResult.java` |
| `domain/underwriting/port/AutoUnderwritingRequester.java` | `usecase/underwriting/port/AutoUnderwritingRequester.java` |
| `domain/underwriting/port/UnderwritingResultQuery.java` | `usecase/underwriting/port/UnderwritingResultQuery.java` |
| `domain/underwriting/port/ValidPolicyQuery.java` | `usecase/underwriting/port/ValidPolicyQuery.java` |
| `domain/material/MaterialUploadRequest.java` | `usecase/material/model/MaterialUploadRequest.java` |
| `domain/material/FileUploadResult.java` | `usecase/material/model/FileUploadResult.java` |
| `domain/material/port/MaterialFileProcessor.java` | `usecase/material/port/MaterialFileProcessor.java` |
| `domain/material/port/MaterialBatchNoGenerator.java` | `usecase/material/port/MaterialBatchNoGenerator.java` |

`domain/material/port/MaterialRepository.java` 原地保留。`domain/audit`、`domain/underwriting` 搬空，`domain/material` 从 7 个文件降到 3 个。

需要同步 import 的文件：

- `usecase/audit/AuditDeliveryUseCase.java`
- `usecase/underwriting/UnderwritingUseCase.java`
- `usecase/material/MaterialProcessingUseCase.java`
- `usecase/intake/ClaimApplicationIntakeMaterialFactory.java`
- `usecase/integration/compensation/ClaimApplicationCompensationUseCase.java`
- `infrastructure/audit/external/LocalAuditSystemClient.java`
- `infrastructure/material/external/LocalMaterialFileProcessor.java`
- `infrastructure/material/number/UuidMaterialBatchNoGenerator.java`
- `infrastructure/underwriting/external/LocalAutoUnderwritingRequester.java`
- `infrastructure/underwriting/external/LocalUnderwritingResultQuery.java`
- `infrastructure/underwriting/external/LocalValidPolicyQuery.java`
- `infrastructure/integration/config/LocalIntegrationProperties.java`
- `test/.../usecase/audit/AuditDeliveryUseCaseTest.java`

`UnderwritingResult` 保持 `PROCESSING / COMPLETED / NOT_FOUND` 三值枚举与 JSON 绑定名不变，只改包；`LocalIntegrationProperties` 的 `claim.integration.local.*` 配置键不变。

### 3.3 Group C：异步集成内核（14 个类）

| 原路径 | 新路径 |
|---|---|
| `domain/integration/outbox/OutboxEvent.java` | `usecase/integration/outbox/model/OutboxEvent.java` |
| `domain/integration/outbox/OutboxEventStatus.java` | `usecase/integration/outbox/model/OutboxEventStatus.java` |
| `domain/integration/outbox/ClaimApplicationEventType.java` | `usecase/integration/outbox/model/ClaimApplicationEventType.java` |
| `domain/integration/outbox/port/OutboxEventPublisher.java` | `usecase/integration/outbox/port/OutboxEventPublisher.java` |
| `domain/integration/outbox/port/OutboxEventRepository.java` | `usecase/integration/outbox/port/OutboxEventRepository.java` |
| `domain/integration/outbox/port/OutboxRelayPolicy.java` | `usecase/integration/outbox/port/OutboxRelayPolicy.java` |
| `domain/integration/consumption/ConsumedEventRepository.java` | `usecase/integration/consumption/port/ConsumedEventRepository.java` |
| `domain/integration/compensation/ClaimApplicationCompensationPolicy.java` | `usecase/integration/compensation/port/ClaimApplicationCompensationPolicy.java` |
| `domain/integration/interaction/InteractionLog.java` | `usecase/integration/interaction/model/InteractionLog.java` |
| `domain/integration/interaction/InteractionType.java` | `usecase/integration/interaction/model/InteractionType.java` |
| `domain/integration/interaction/InteractionAction.java` | `usecase/integration/interaction/model/InteractionAction.java` |
| `domain/integration/interaction/InteractionDirection.java` | `usecase/integration/interaction/model/InteractionDirection.java` |
| `domain/integration/interaction/InteractionResultStatus.java` | `usecase/integration/interaction/model/InteractionResultStatus.java` |
| `domain/integration/interaction/InteractionLogRepository.java` | `usecase/integration/interaction/port/InteractionLogRepository.java` |

`InteractionLog.of(...)`、`OutboxEvent.pending(...)`、`OutboxEvent` 的持久化还原构造方式保持不变；`InteractionType.direction()`、`targetSystem()` 与 `InteractionAction.resultStatus()` 的映射逻辑不变，只改所在包。

需要同步 import 的文件：

- `usecase/integration/outbox/OutboxRelayUseCase.java`
- `usecase/integration/record/ClaimApplicationLifecycleRecordFactory.java`
- `usecase/integration/compensation/ClaimApplicationCompensationUseCase.java`
- `usecase/integration/compensation/ClaimApplicationCompensationTransactionService.java`
- `usecase/intake/ClaimApplicationIntakeTransactionService.java`
- `usecase/material/MaterialProcessingTransactionService.java`
- `usecase/underwriting/UnderwritingTransactionService.java`
- `usecase/underwriting/UnderwritingCallbackTransactionService.java`
- `usecase/audit/AuditDeliveryTransactionService.java`
- `usecase/audit/AuditSignedTransactionService.java`
- `infrastructure/integration/persistence/outbox/MyBatisOutboxEventRepository.java`
- `infrastructure/integration/persistence/outbox/OutboxEventDOMapper.java`
- `infrastructure/integration/messaging/RocketMqOutboxPublisher.java`
- `infrastructure/integration/config/OutboxRelayProperties.java`
- `infrastructure/integration/config/ClaimApplicationCompensationProperties.java`
- `infrastructure/integration/persistence/consumption/MyBatisConsumedEventRepository.java`
- `infrastructure/integration/persistence/interaction/InteractionLogDOMapper.java`
- `infrastructure/integration/persistence/interaction/MyBatisInteractionLogRepository.java`
- `infrastructure/integration/local/AbstractLocalIntegration.java`
- `infrastructure/audit/external/LocalAuditSystemClient.java`
- `infrastructure/material/external/LocalMaterialFileProcessor.java`
- `infrastructure/underwriting/external/LocalAutoUnderwritingRequester.java`
- `infrastructure/underwriting/external/LocalUnderwritingResultQuery.java`
- `infrastructure/underwriting/external/LocalValidPolicyQuery.java`
- `test/.../usecase/audit/AuditDeliveryUseCaseTest.java`
- `test/.../usecase/audit/AuditSignedTransactionServiceTest.java`
- `test/.../usecase/audit/AuditSignedUseCaseTest.java`

Javadoc 同步：`ClaimApplicationLifecycleRecordFactory` 中“领域词汇由 `domain/integration` 决定”改为“由 `usecase/integration/interaction/model` 决定”；`OutboxRelayProperties`、`ClaimApplicationCompensationProperties` 的“领域策略端口”改为“用例出站策略端口”。

### 3.4 Group D：Domain 清理

| 动作 | 路径 |
|---|---|
| 移动 Repository | `domain/issue/ClaimApplicationIssueRepository.java` → `domain/issue/port/ClaimApplicationIssueRepository.java` |
| 删除空包 | `domain/audit/`、`domain/underwriting/`、`domain/integration/` |
| 更新分层说明 | `domain/package-info.java` |

需要同步 import：`domain/issue/service/IssueDomainService.java`、`infrastructure/issue/persistence/ClaimApplicationIssueRepositoryImpl.java`、`usecase/issue/ClaimApplicationIssueCloseUseCase.java`、`usecase/issue/ClaimApplicationIssueIntakeUseCase.java`、`usecase/issue/ClaimApplicationIssueQueryUseCase.java`、`test/.../domain/issue/service/IssueDomainServiceTest.java`、`test/.../usecase/issue/ClaimApplicationIssueCloseUseCaseTest.java`、`test/.../usecase/issue/ClaimApplicationIssueIntakeUseCaseTest.java`、`test/.../usecase/issue/ClaimApplicationIssueQueryUseCaseTest.java`。

`IssueDomainEventPublisher` 保留在 `domain/issue/event`：它由 `IssueDomainService` 在领域内部调用，属于领域事件端口；移到 `usecase` 会迫使 `domain → usecase` 反向依赖。

## 四、ArchUnit 规则调整

`ClaimApplicationArchitectureTest` 保留“领域不依赖外层”“用例不依赖适配器”“入站不依赖基础设施”“控制器声明错误码”四条规则，替换“基础设施不得依赖任何 usecase”规则，并新增端口反向依赖规则。

### 4.1 允许的基础设施依赖白名单

使用固定包名，不使用宽泛的 `..usecase..`：

```java
private static final String[] USE_CASE_CONTRACT_PACKAGES = {
        "..usecase.intake.port..", "..usecase.intake.model..",
        "..usecase.material.port..", "..usecase.material.model..",
        "..usecase.underwriting.port..", "..usecase.underwriting.model..",
        "..usecase.audit.port..", "..usecase.audit.model..",
        "..usecase.issue.port..", "..usecase.issue.model..",
        "..usecase.integration.outbox.port..", "..usecase.integration.outbox.model..",
        "..usecase.integration.consumption.port..", "..usecase.integration.consumption.model..",
        "..usecase.integration.compensation.port..", "..usecase.integration.compensation.model..",
        "..usecase.integration.interaction.port..", "..usecase.integration.interaction.model.."
};
```

`issue` 目前没有 `port/model` 之外的共享模型，仍列入白名单，保证后续新增时不需要再改规则。

### 4.2 规则清单

1. `domainMustNotDependOnOuterLayers`：不变，禁止 `domain → usecase/infrastructure/interfaces/support`。
2. `useCaseMustNotDependOnAdapters`：不变，禁止 `usecase → infrastructure/interfaces`。
3. `infrastructureMustNotDependOnInboundAdapters`：禁止 `infrastructure → interfaces`。
4. `infrastructureMustDependOnlyOnUseCaseContracts`：

```java
noClasses()
        .that().resideInAPackage("..infrastructure..")
        .should().dependOnClassesThat(
                resideInAPackage("..usecase..")
                        .and(resideOutsideOfPackages(USE_CASE_CONTRACT_PACKAGES)))
        .because("基础设施只能依赖用例的出站端口与端口模型，不得依赖用例编排")
        .check(APPLICATION_CLASSES);
```

5. `useCaseContractsMustNotDependOnOrchestration`：

```java
noClasses()
        .that().resideInAnyPackage(USE_CASE_CONTRACT_PACKAGES)
        .should().dependOnClassesThat(
                resideInAPackage("..usecase..")
                        .and(resideOutsideOfPackages(USE_CASE_CONTRACT_PACKAGES)))
        .because("端口与端口模型不得反向依赖用例编排类")
        .check(APPLICATION_CLASSES);
```

6. `inboundAdaptersMustNotDependOnInfrastructure`：不变。
7. `controllersMustDeclareApiErrorCodes`：不变。

规则 4、5 使用 `JavaClass.Predicates.resideInAPackage` 与 `resideOutsideOfPackages` 组合谓词，避免把 `usecase/integration/record`、`usecase/intake/ClaimApplicationIntakeUseCase` 等编排类误放进允许范围。

## 五、文档与路径同步

| 文档 | 需要改动的部分 |
|---|---|
| `docs/architecture/ddd-standard-architecture.md` | §1.2 依赖方向：`*Policy` 端口从 `domain` 改为 `usecase/<域>/port`；§2 四层职责表与“端口在 domain”条目；§3 对象角色词典中的聚合、枚举、仓储端口、外部能力端口、配置适配器；§4 类数表与 `integration` 说明；§5 自检命令与当前结果 |
| `docs/architecture/claim-application-architecture.md` | 目录说明中的依赖方向、`domain` 内容、`integration` 定位、跨域引用允许范围 |
| `docs/architecture/code-navigation.md` | 四层职责、能力定位表、排查路径、并发控制、异步一致性、演进原则 |
| `domain/package-info.java` | 删除“端口接口”表述，改为“聚合 Repository 与领域事件端口” |
| `usecase/package-info.java` | 增加“出站能力端口、配置策略端口、端口查询模型”职责 |
| `infrastructure/package-info.java` | 改为“实现 usecase 出站端口与 domain 聚合 Repository” |
| `specs/020-message-contract-typing/design.md` | `domain/integration/outbox/port` → `usecase/integration/outbox/port` |
| `specs/020-message-contract-typing/tasks.md` | T5 路径同步为 `usecase/integration/outbox/port/` |

历史 spec 005、018、019 不重写。005 中“端口在 domain”的记录作为历史裁定保留；021 在新文档中明确替代关系。

## 六、行为不变性

| 关注点 | 本次处理 |
|---|---|
| 业务规则与状态机 | 类内容原样移动，不修改 `ClaimApplication`、`Material`、`IssueDomainService` 的任何判断或状态迁移 |
| 事务边界 | Outbox 仍由 `*TransactionService` 在本地事务内直接写入；不引入领域事件驱动 Outbox；不调整 `@Transactional` 位置 |
| 消息契约 | Topic、消费组、事件类型、事件版本、JSON 字段、死信策略全部不变；020 只改 Java 类型，不改线上格式 |
| 持久化 | `*DO`、`*DOMapper`、`*DataConverter`、表结构与 `MyBatis*Repository` 均不移动；`OutboxEvent` 的数据库还原方式不变 |
| Redis | 键、TTL、降级开关、并发上限不变；`ClaimApplicationIntakeProperties` 只改实现的接口来源 |
| HTTP/MQ 入口 | `interfaces` 包结构、Controller 注解、Consumer 函数签名与绑定不变 |
| 异常 | `domain/<域>/exception` 的错误码保留；`BusinessException` 与全局处理器不变 |
| Spring 装配 | 根包不变，组件扫描、`@MapperScan`、`@ConfigurationProperties` 扫描范围不变；Bean 名称仍由类简单名生成 |

## 七、实施顺序

1. 先改活文档与三个 `package-info.java`，确定新基线。
2. 再改 ArchUnit：新规则先就位。此时基础设施尚未依赖 `usecase`，规则应保持通过，作为后续搬迁的守门规则。
3. 按 Group A → B → C → D 搬迁，每组内先 model/port、再 usecase、再 infrastructure、最后测试，保证该组结束时编译、相关测试与 ArchUnit 均通过。
4. 全仓清理旧包引用与过期 Javadoc，同步 020 的路径引用。
5. 重新统计类数、目录数与自检期望值，回填文档。
6. JDK 21 全量构建，随后在运行栈执行黄金路径与失败路径回归。

## 八、风险与对策

| 风险 | 对策 |
|---|---|
| ArchUnit 白名单漏包，导致基础设施合法依赖被误判 | 白名单显式列出所有 `<域>/{port,model}`；新增端口包时同步扩展数组 |
| 端口或模型反向引用 `usecase` 编排类 | 规则 5 单独拦截；搬迁时先移模型再改引用，发现反向依赖立即调整归属 |
| `InteractionLog`、`OutboxEvent` 被误当作领域聚合继续放在 `domain` | 文档与 `package-info` 明确其为异步内核记录模型；只在 `usecase/integration` 保留唯一装配工厂 |
| 搬迁过程中 Spring 扫描或 MyBatis Mapper 扫描失效 | `infrastructure` 目录与根包均不动；构建后启动运行栈验证 Bean 与 Mapper 均能装配 |
| 组内移类后未修完 import 导致编译中断 | 每组作为一个编译单元：先移 model/port，再一次性修 usecase、infrastructure 与测试引用，组结束必须编译与测试通过 |
| 020 与 021 的路径互相踩踏 | 021 先合并；021 完成时同步 020 的两处路径，020 再按新路径实现 |

## 九、验收映射

| 验收标准 | 对应任务 | 验证方式 |
|---|---|---|
| 1. `domain` 只留业务模型，三个技术包删除 | T3–T6 | `find domain`、`grep '^public interface' domain` |
| 2. 技术端口与模型位于 `usecase/<域>/{port,model}` | T3–T6 | 目录检查与 import 扫描 |
| 3. `infrastructure` 只依赖 `usecase` 的 port/model 与 `domain` | T2、T9 | ArchUnit 规则 3–5 |
| 4. 新 ArchUnit 规则可分别拦截三类越界 | T2、T9 | 架构测试通过；逐条规则独立运行 |
| 5. 全量构建与既有测试通过，020 路径已同步 | T7、T9 | Maven 构建、020 文档检查 |
| 6. 运行栈行为与 021 前一致 | T9 | 签收、材料、承保、审核交付、审核签收、问题件、Outbox、补偿、死信回归 |
| 7. 活文档与新包结构一致 | T1、T8 | 文档自检命令输出与预期值一致 |
