# 021 DDD 分层边界基线调整 · 任务清单

## 任务批次

### Batch 1：基线与守门规则

#### T1：活文档与分层说明先行
- 产物：修改 `docs/architecture/ddd-standard-architecture.md`、`docs/architecture/claim-application-architecture.md`、`docs/architecture/code-navigation.md`、`domain/package-info.java`、`usecase/package-info.java`、`infrastructure/package-info.java`
- 路径：`docs/architecture/`、`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/`
- 完成条件：
  - 三份架构文档不再出现“出站端口一律声明在 domain”“基础设施不得依赖 usecase”的旧基线
  - 四层职责表、对象角色词典、类数表、自检命令按 design §五 改写
  - `domain` 说明只保留聚合、值对象、枚举、领域事件、领域服务、聚合 Repository、业务异常；`usecase` 说明增加出站能力端口、配置策略端口与端口模型
  - `infrastructure` 说明改为“实现 usecase 出站端口与 domain 聚合 Repository”
  - 文档中的目标路径与 design §二、§三 一致，可脱离代码评审

#### T2：ArchUnit 规则改造
- 产物：修改 `ClaimApplicationArchitectureTest.java`
- 路径：`backend/claim-application-service/src/test/java/com/wangguangwu/claim/application/`
- 完成条件：
  - 新增 `USE_CASE_CONTRACT_PACKAGES` 常量，逐项列出 design §4.1 的 18 个端口/模型包
  - `infrastructureMustNotDependOnUseCaseOrInboundAdapters` 拆为“禁止依赖 interfaces”与“只允许依赖 port/model”两条规则
  - 新增 `useCaseContractsMustNotDependOnOrchestration`，禁止端口/模型反向依赖用例编排
  - 领域、用例、入站、控制器四条既有规则保留且语义不变
  - 规则编译并运行通过；此时基础设施尚未依赖 `usecase`，不应出现新增违规
  - 后续每组搬迁完成后 Rule 4、Rule 5 仍须保持通过
  - 中文 JavaDoc 说明每条规则拦截的越界方向

### Batch 2：四组搬迁

#### T3：Group A 签收端口与快照模型
- 产物：新增 `usecase/intake/model/{ClaimApplicationIntakeLookup,ClaimApplicationIntakeLookupKind,ClaimApplicationIntakeSnapshot}.java`、`usecase/intake/port/{ClaimApplicationIntakePolicy,ClaimApplicationIntakeLock,ClaimApplicationIntakeSnapshotQuery,ClaimApplicationNoGenerator}.java`；删除原 `domain/claim` 下对应 7 个文件
- 路径：`usecase/intake/{model,port}/`、`domain/claim/`、`infrastructure/intake/`
- 完成条件：
  - 7 个类按 design §3.1 表移动，类名、字段、方法签名、JavaDoc 语义不变
  - design §3.1 列出的 3 个 usecase 文件、4 个 infrastructure 文件与 1 个测试文件 import 全部修正
  - `ClaimApplicationIntakeProperties` 仍绑定 `claim.intake`，只改实现的接口来源
  - `domain/claim/port` 只剩 `ClaimApplicationRepository`
  - 编译通过，`ClaimApplicationIntakeUseCaseTest` 通过

#### T4：Group B 外部能力端口与模型
- 产物：新增 `usecase/audit/{model,port}`、`usecase/underwriting/{model,port}`、`usecase/material/{model,port}` 下共 10 个类；删除 `domain/audit`、`domain/underwriting` 与 `domain/material` 下对应文件
- 路径：`usecase/{audit,underwriting,material}/{model,port}/`、`domain/{audit,underwriting,material}/`、`infrastructure/{audit,underwriting,material}/`
- 完成条件：
  - 10 个类按 design §3.2 表移动，`UnderwritingResult` 三值枚举值不变
  - design §3.2 列出的 5 个 usecase 文件、7 个 infrastructure 文件与 1 个测试文件 import 全部修正
  - `LocalIntegrationProperties` 的 `claim.integration.local.*` 配置键与枚举绑定不变
  - `domain/material/port` 只剩 `MaterialRepository`；`domain/audit`、`domain/underwriting` 已无 Java 文件
  - 编译通过，`AuditDeliveryUseCaseTest` 通过

#### T5：Group C 异步集成内核
- 产物：新增 `usecase/integration/outbox/{model,port}`、`usecase/integration/consumption/port`、`usecase/integration/compensation/port`、`usecase/integration/interaction/{model,port}` 下共 14 个类；删除 `domain/integration` 下全部 Java 文件
- 路径：`usecase/integration/`、`domain/integration/`、`infrastructure/integration/`
- 完成条件：
  - 14 个类按 design §3.3 表移动，`OutboxEvent.pending(...)`、`InteractionLog.of(...)`、枚举映射方法逻辑不变
  - design §3.3 列出的 10 个 usecase 文件、14 个 infrastructure 文件与 3 个测试文件 import 全部修正
  - `ClaimApplicationLifecycleRecordFactory`、两个 `*Properties` 的过期 Javadoc 同步更新
  - 事件类型、Topic、载荷字段、退避与失败标记逻辑不变
  - 编译通过，三个审核用例测试通过

#### T6：Group D Domain 清理
- 产物：移动 `ClaimApplicationIssueRepository` 到 `domain/issue/port/`；删除空目录 `domain/audit`、`domain/underwriting`、`domain/integration`
- 路径：`domain/issue/`、`infrastructure/issue/`、`usecase/issue/`、`test/.../issue/`
- 完成条件：
  - Repository 按 design §3.4 表移动，类内容不变
  - design §3.4 列出的 5 个生产文件与 4 个测试文件 import 修正
  - `IssueDomainEventPublisher` 仍在 `domain/issue/event`，`IssueDomainService` 未出现对 `usecase` 的依赖
  - `domain` 下不再存在 `audit`、`underwriting`、`integration` 包
  - 编译通过，问题件相关测试通过

### Batch 3：收口与文档

#### T7：旧路径清零与 020 同步
- 产物：全仓 import 与 Javadoc 清理；修改 `specs/020-message-contract-typing/design.md`、`specs/020-message-contract-typing/tasks.md`
- 完成条件：
  - 以下旧路径在生产代码、测试代码、活文档中的引用均为 0：`domain.claim.ClaimApplicationIntakeLookup`、`domain.claim.ClaimApplicationIntakeLookupKind`、`domain.claim.ClaimApplicationIntakeSnapshot`、`domain.claim.ClaimApplicationIntakePolicy`、`domain.claim.port.ClaimApplicationIntakeLock`、`domain.claim.port.ClaimApplicationIntakeSnapshotQuery`、`domain.claim.port.ClaimApplicationNoGenerator`、`domain.audit`、`domain.underwriting`、`domain.integration`、`domain.material.MaterialUploadRequest`、`domain.material.FileUploadResult`、`domain.material.port.MaterialFileProcessor`、`domain.material.port.MaterialBatchNoGenerator`、`domain.issue.ClaimApplicationIssueRepository`
  - 历史 spec `005`、`018`、`019` 不修改
  - 020 的 `OutboxEventPublisher` 路径改为 `usecase/integration/outbox/port`
  - 所有新增 `model`/`port` 类无未使用 import、无旧包名 Javadoc

#### T8：统计回填与文档终稿
- 产物：更新 `docs/architecture/ddd-standard-architecture.md` 与 `code-navigation.md` 的类数、目录数、自检期望值
- 完成条件：
  - 四层文件数与 design §二 目标表一致：`domain` 26、`usecase` 69、`interfaces` 43、`infrastructure` 45，总数 195
  - §5 自检命令按新包结构重写，并逐条执行
  - 预期值至少覆盖：`domain → 外层` 为 0、`infrastructure → 非 port/model 的 usecase` 为 0、`usecase → infrastructure/interfaces` 为 0、端口/模型反向依赖为 0
  - `domain` 下 `public interface` 为 6（4 个 Repository + `IssueDomainEvent` + `IssueDomainEventPublisher`），其中不在 `port` 子包的为 2
  - 文档中的示例路径与 `usecase/integration/interaction/model` 等实际路径一致

### Batch 4：验证

#### T9：全量构建与运行栈回归
- 产物：无代码产物，输出验证结论
- 完成条件：
  - `JAVA_HOME` 指向 JDK 21 执行 `mvn -B -pl backend/claim-application-service -am clean package` 成功
  - `ClaimApplicationArchitectureTest` 全部规则通过，既有单元测试全部通过
  - 运行栈重启应用服务后，签收首次与同号幂等、材料处理成功/失败与重新申请、自动承保回调、审核交付、审核签收（正常/重复/非法报文/DLQ）、问题件下发与关闭全部与 021 前一致
  - Outbox 投递、消费去重、滞留补偿与死信链路行为一致；核对 Topic、消费组、事件类型未变
  - 确认 Spring Bean、MyBatis Mapper 与 `@ConfigurationProperties` 均正常装配，无扫描范围变化
  - 确认 `domain` 无技术包残留，`infrastructure` 无对用例编排类的引用
