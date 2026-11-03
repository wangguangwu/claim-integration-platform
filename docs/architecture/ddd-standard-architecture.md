# 标准 DDD 架构说明

本文回答一个问题：什么叫"符合标准 DDD"，判定依据是什么，以及 `claim-application-service` 的类各自落在哪一层。
文档面向本项目开发者，术语以 Eric Evans / Vaughn Vernon《实现域驱动设计》、业界广泛引用的四层结构、Alibaba COLA 4.0 与 Fowler《企业应用架构模式》为基准。

## 1. 标准只规定三件事

DDD 的标准约束只有三条，其余都是项目方言：

1. **层的职责**：每一层放什么类型、不放什么类型，有明确定义。展开成三条最容易被漏掉的硬规则：
   - **聚合自己守不变式**：聚合根不得对层外暴露公开 setter，构造器私有，状态只能经命名领域方法迁移；从数据库重建聚合走 owning 域的反水化工厂，不能"先构造再逐字段 set"。写了状态机又开着 `setStatus`，等于没有状态机。
   - **创建知识只留在 owning 域**：其他层不得 `new` 本域聚合或逐字段拼装其状态，只能调用该域 `domain` 暴露的工厂方法；入站适配器也不得组装聚合——租户协议流水线的产物是 `ClaimApplicationIntakeCommand` 命令，聚合由签收用例调用 `ClaimApplication.submit(...)` 创建。同一类记录（如异步 Outbox 事件与交互日志）只允许一个装配入口，且装配入口不得自行推断领域词汇——方向、目标系统、结果状态这类固定映射挂在说出它们的枚举上（`InteractionType.direction()`、`InteractionAction.resultStatus()`）。
   - **业务性拒绝说业务的语言**：不变式违反应抛 `DomainException`，用例编排拒绝抛 `ApplicationException`，两者都继承传输无关的 `BusinessException` 并携带本域 `BusinessErrorCode`；入站适配与基础设施在自身边界内直接使用基类。`IllegalStateException` 这类 JDK 异常留给真正的编程错误。异常不携带 HTTP 类型：状态码由入站层 `BusinessErrorStatusRegistry` 集中映射，字段校验失败与报文不可解析使用的"请求非法"码、未预期异常使用的系统错误码由当前控制器实现 `ApiErrorCodeProvider` 声明，未声明时 ArchUnit 直接失败。局部重试信号（如同步判定与库中记录不一致）用返回值表达，不复用业务异常。
2. **依赖方向**：`interfaces → application → domain ← infrastructure`，且 `domain` 不依赖任何框架。这是"依赖倒置"，是标准，不是风格。配置阈值同样是实现细节：用例需要批量、退避与滞留阈值时，在 `application/port` 声明 `*Policy` 端口，由 `infrastructure/config` 的 `*Properties` 绑定配置实现；把 `@ConfigurationProperties` 挨着用例放，应用层就带上了 Spring。
3. **对象角色命名**：一个类的后缀要能说明它在模式里的角色——`DO`、ORM 映射、转换器、仓储实现、端口、值对象各不相同。Fowler 的 Data Mapper 定义决定了 `*Mapper` 只能是 ORM，这就是为什么 DO↔领域对象的转换器不能叫 `Mapper`。同一后缀在整棵树上只能有一种含义：`*UseCase` 一律是用例入口具体类（不为它保留单实现接口），`*TransactionService` 一律是事务边界；两个名字只差一个词（如 `*Validation` 与 `*Validator`）视为职责未说清，必须改名拆分。

标准**不规定**：

- **目录组织形式**：按层再按角色、按层再按业务域，还是纵向切片，DDD 没有立场，只要求四层职责清楚、方向单一。本项目定案为**层级优先、层内按对象角色分包**。
- **目录粒度**：`infrastructure` 下要不要按聚合、按技术角色再分子包，Vernon 的原书示例按聚合分，COLA 全部平铺，两者都合规。
- **包名拼写**：`application` 与 `usecase`/`app`、`interfaces` 与 `adapter`、`external` 与 `gatewayimpl`，都是流派差异。

因此判断"是否标准"只能靠可扫描的规则，不能靠目录看起来像不像某个模板。

## 2. 四层职责

| 层 | 职责 | 放什么 | 禁止放什么 |
|---|---|---|---|
| `interfaces` | 把外部请求翻译成用例调用（Ports & Adapters 里的 driving adapter） | `web` 下的 Controller、统一响应与异常处理、响应渲染、租户协议流水线；`consumer` 下的 MQ 消费者与报文解析装配；`dto` 下的 HTTP 请求/响应模型；`scheduler` 下的定时任务入口 | 业务规则、数据库访问、事务编排、聚合组装 |
| `application` | 用例编排：按顺序调用领域与端口，管理事务、幂等与重试 | `process` 下的 UseCase、TransactionService、记录装配工厂、结果模型；`command` 下的命令入参；`query` 下的查询用例与读模型；`port` 下的出站能力端口与端口模型；`exception` 下的应用异常与异步错误码 | 业务不变式（属于聚合）、DO、框架化的持久化细节、配置绑定 |
| `domain` | 业务本身：状态、不变式、词汇 | 聚合根、聚合内实体、值对象、状态枚举（`domain/claim` 及其 `{material,issue,person}` 子包）、聚合 Repository（`domain/claim/port`、`domain/claimcase/port`）、领域错误码（`domain/claim/exception`） | Spring、MyBatis-Plus、Redisson、MapStruct、Feign、MQ、Jackson；DO；任何序列化细节；锁、发号、外部系统调用、Outbox 等技术能力端口 |
| `infrastructure` | 端口的技术实现（driven adapter） | `persistence` 下的 `*DO`、ORM `*DOMapper`、DO↔领域对象 `*Converter`、`MyBatis*Repository` 与读模型实现；`adapter` 下的 Redis 缓存/锁/编号生成/熔断；`acl` 下的外部系统实现；`mq` 下的消息发布；`config` 下的 `*Properties` 与框架配置 | 业务规则、用例流程决策 |
| `shared` | 跨层、跨域的传输无关契约 | `exception` 下的 `BusinessException`、`DomainException` 与 `BusinessErrorCode`；`message` 下的消息契约；`util` 下的无业务归属工具 | 单一业务域模型、HTTP 请求响应、应用编排 |

三条最容易搞错的归属，逐条说明：

- **端口按能力归属，不按实现便利归属。** 聚合 Repository 表达业务模型的生命周期，留在 `domain/<聚合>/port`；锁、发号、快照查询、外部系统调用、Outbox、消费去重、补偿与交互轨迹属于用例编排需要的能力，声明在 `application/port`。`infrastructure` 因此允许依赖 `application/port`、`application/port/model`、`application/query/model` 与 `domain`，但不允许依赖用例编排类，也绝不依赖 `interfaces`。
- **DO 永远不进 `domain`。** DO 是表结构的影子，进领域层就等于让数据库形状决定业务模型。DO 只允许出现在 `infrastructure`，跨层传的一律是领域对象。
- **`*Converter` 与 `*Mapper` 是两种东西。** `ClaimApplicationDOMapper extends BaseMapper<ClaimApplicationDO>` 是 ORM；`ClaimApplicationDataConverter` 是 DO↔领域对象的映射器（MapStruct 的 `@Mapper` 注解是框架 API，不是类名，可以保留）。

## 3. 对象角色词典

| 角色 | 后缀/形态 | 所在层 | 本项目实例 |
|---|---|---|---|
| 聚合根 | 业务名词，record/class，构造器私有或只暴露命名工厂 | `domain/<聚合>` | `domain/claim/ClaimApplication` |
| 聚合内实体 | 业务名词，只能由聚合根创建与修改 | `domain/<聚合>/<子域>` | `domain/claim/material/Material`、`domain/claim/issue/ClaimApplicationIssue` |
| 聚合外追加事实 | 业务名词，只追加、不参与聚合加载与保存 | `domain/<聚合>/<子域>` | `domain/claimcase/ClaimCaseRelation`（审核签收案件关联） |
| 值对象 | 业务名词 | `domain/<聚合>/<子域>` | `domain/claim/person` 下的 `PersonInfo` 基类与 `Applicant`/`PrimaryInsured`/`AccidentPerson`/`Payee` 四个角色子类、`MaterialSubmission` |
| 状态/类型枚举 | `*Status`、`*Type`、`*Action`、`*Direction` | 业务状态在 `domain`；异步内核枚举在 `application/port/model` | `domain`：`ClaimApplicationStatus`、`MaterialStatus`、`ClaimApplicationIssueStatus`；`application/port/model`：`OutboxEventStatus`、`ClaimApplicationEventType`、`InteractionType`、`InteractionAction` |
| 枚举自带的业务映射 | 枚举字段与方法 | 随枚举所在层 | `InteractionType.direction()`/`targetSystem()`、`InteractionAction.resultStatus()`——方向、目标系统、结果状态这类固定词汇由枚举自己回答，不得由用例层 `switch` 推断 |
| 聚合仓储端口 | `*Repository` | `domain/<聚合>/port` | `domain/claim/port/ClaimApplicationRepository`、`domain/claimcase/port/ClaimCaseRelationRepository`（材料与问题件不设独立仓储） |
| 外部能力端口与配置策略 | `*Query`、`*Generator`、`*Lock`、`*Processor`、`*Publisher`、`*Policy`、`*Port` | `application/port` | `UnderwritingSystemPort`、`AuditSystemPort`、`ClaimApplicationNoGenerator`、`ClaimApplicationIntakeLock`、`ClaimApplicationIntakeSnapshotQuery`、`ClaimApplicationDetailQuery`、`MaterialFileProcessor`、`OutboxEventPublisher`、`OutboxRelayPolicy`、`ClaimApplicationCompensationPolicy`、`ClaimApplicationIntakePolicy` |
| 端口模型 | 随端口一起被用例与基础设施共享的查询/请求/结果 record 或枚举 | `application/port/model` | `ClaimApplicationIntakeLookup`、`ClaimApplicationIntakeSnapshot`、`AuditDeliveryRequest`、`UnderwritingResult`、`MaterialUploadRequest`、`FileUploadResult`、`OutboxEvent`、`InteractionLog` |
| 读模型 | 查询输出视图 | `application/query/model` | `ClaimApplicationWithMaterials`、`ClaimApplicationWithIssues`、`ClaimApplicationWithCases`、`IssueView`、`MaterialView`、`CaseView`、`ClaimApplicationSummary` |
| 配置适配器 | `*Properties`（实现 `application/port` 的 `*Policy` 端口） | `infrastructure/config` | `OutboxRelayProperties`、`ClaimApplicationCompensationProperties`、`ClaimApplicationIntakeProperties`、`ClaimApplicationRedisCircuitProperties` |
| 仓储实现 | `MyBatis*Repository` | `infrastructure/persistence` | `MyBatisClaimApplicationRepository`、`ClaimCaseRelationRepositoryImpl` |
| 读模型实现 | `MyBatis*Query` | `infrastructure/persistence` | `MyBatisClaimApplicationDetailQuery` |
| 持久化对象 | `*DO` | `infrastructure/persistence` | `ClaimApplicationDO`、`MaterialDO`、`OutboxEventDO` |
| ORM 映射 | `*DOMapper` | `infrastructure/persistence` | `ClaimApplicationDOMapper` |
| DO↔领域转换器 | `*DataConverter`、`*Converter` | `infrastructure/persistence` | `ClaimApplicationDataConverter`、`MaterialDataConverter`、`PersonInfoJsonConverter` |
| 外部系统实现 | 未接入真实系统时用 `Logging*`，只打印调用日志并返回可继续流程的默认结果；接入真实系统时替换本包实现 | `infrastructure/acl` | `LoggingUnderwritingSystem`、`LoggingAuditSystem`、`LoggingMaterialFileProcessor` |
| 缓存/锁/编号实现 | 技术前缀 + 端口名 | `infrastructure/adapter` | `RedisClaimApplicationIntakeSnapshotQuery`、`RedissonClaimApplicationIntakeLock`、`RedisMySqlClaimApplicationNoGenerator`、`RedisAvailabilityCircuit` |
| 消息发布 | 技术前缀 + `Publisher` | `infrastructure/mq` | `RocketMqOutboxPublisher` |
| 业务异常与错误码 | `BusinessException`（传输无关基类）、`DomainException`（领域拒绝）、`ApplicationException`（用例拒绝）+ `*ErrorCode implements BusinessErrorCode` | `shared/exception`、`application/exception`、`domain/claim/exception` | `BusinessException`、`DomainException`、`ApplicationException`、`ClaimApplicationAsyncErrorCode`、`ClaimApplicationErrorCode`、`ClaimApplicationIntakeErrorCode`、`ClaimApplicationIssueErrorCode`、`ClaimApplicationQueryErrorCode` |
| 入站错误码声明与异常渲染 | `ApiErrorCodeProvider`、`BusinessErrorStatusRegistry`、`WebExceptionHandler` | `interfaces/web` | 控制器声明请求非法码与系统错误码；注册表是错误码到 HTTP 状态的唯一映射；全局处理器是唯一 `@RestControllerAdvice` |
| 用例与编排 | `*UseCase`（具体类，不再保留单实现接口）、`*TransactionService`、`*RecordFactory`、`*Factory`、`*Resolver` | `application/process` | `ClaimApplicationIntakeUseCase`、`MaterialProcessingUseCase`、`ClaimApplicationDetailQueryUseCase`、`ClaimApplicationIntakeConflictResolver`、`ClaimApplicationLifecycleRecordFactory` |
| 命令入参/结果出参 | `*Command`、`*Result`、`*Disposition`、`*Outcome` | `application/command`、`application/process` | `MaterialProcessCommand`、`ClaimApplicationIntakeCommand`、`ClaimApplicationIntakeResult`、`AuditSignedOutcome` |
| 入站通道 | `*Controller`、`*Consumer`、`*Scheduler`、`*Pipeline` | `interfaces/{web,consumer,scheduler}`；流水线实现另入 `web/tenantprotocol/core/pipeline` 与 `tenants/<租户>` | `ClaimApplicationIntakeController`、`MaterialProcessConsumer`、`OutboxRelayScheduler`、`TenantAProtocolPipeline` |
| 报文防腐组件 | `TenantProtocolSignatureVerifier`、`TenantProtocolDecryptor`、`*Parser`、`*Converter`、`TenantProtocolValidator`（注解适配器）；必填约束以 `jakarta.validation` 注解声明在租户 request 上 | `interfaces/web/tenantprotocol/core/stage`（五个阶段端口与默认实现）与 `tenants/{tenant_a,tenant_b}`（每租户一套 request/解析/转换） | `DefaultTenantProtocolSignatureVerifier`、`DefaultTenantProtocolDecryptor`、`TenantARequestParser`、`BeanValidationTenantProtocolValidator`、`TenantARequestConverter`（输出 `ClaimApplicationIntakeCommand`） |
| 响应渲染 | `*ResponseRenderer`（唯一实现，不再按租户路由） | `interfaces/web/render` | `ClaimApplicationResponseRenderer`、`ClaimApplicationIssueResponseRenderer` |
| 跨域契约 | 消息密封接口与具体消息 record、错误码接口、异常基类、异步错误码 | `shared/message`、`shared/exception`、`application/exception` | `ClaimApplicationEventMessage`、`MaterialProcessMessage`、`AuditDeliveryRequestedMessage`、`AuditSignedMessage`、`BusinessErrorCode`、`BusinessException`、`ClaimApplicationAsyncErrorCode` |

## 4. 本项目的落位

根包 `com.wangguangwu.claim.application` 下是五个顶层包：四层加 `shared`；层内按对象角色分包，不再按业务域平铺。类数合计 203（不含 `package-info`）。

| 顶层包 | 子包 | 类数 | 说明 |
|---|---|---|---|
| `interfaces` | `web`（含 `render`、`tenantprotocol`）、`consumer`、`dto`、`scheduler` | 45 | `web` 28 个，其中 20 个是租户协议防腐层；`consumer` 5 个；`dto` 10 个；`scheduler` 2 个 |
| `application` | `command`、`query`（含 `model`）、`port`（含 `model`）、`process`、`exception` | 84 | `command` 7 个；`query` 8 个；`port` 33 个；`process` 32 个；`exception` 4 个 |
| `domain` | `claim`（含 `port`、`exception`） | 21 | 聚合与领域模型 15 个；聚合仓储 2 个；领域错误码 4 个 |
| `infrastructure` | `persistence`、`adapter`、`acl`、`mq`、`config` | 40 | 持久化 25 个；技术适配 5 个；外部系统 3 个；消息发布 1 个；配置 6 个 |
| `shared` | `exception`、`message`、`util` | 12 | 异常与错误码接口 3 个；消息契约 8 个；摘要工具 1 个 |
| 根包 | — | 1 | `ClaimApplicationServiceApplication` |

- `interfaces/web` 的 28 个类里有 20 个是租户协议防腐层（`tenantprotocol/core/{pipeline,stage}` 放流水线装配与五个阶段端口，`tenantprotocol/tenants/{tenant_a,tenant_b}` 放每租户一套 request/解析/校验/转换），这是本项目"多租户报文接不进统一领域模型"的真实复杂度，不是分层装饰。
- 防腐层的产物是 `ClaimApplicationIntakeCommand`，`interfaces` 里对 `ClaimApplication` 的引用为 0；该层对 `domain` 的依赖只剩 `Applicant`/`PrimaryInsured`/`AccidentPerson`/`Payee` 四个人员角色、`Gender`、`MaterialSubmission` 与业务错误码。
- 包内不平铺：同一包里出现两类以上互不相干的职责、且每类可聚成 ≥2 个类时必须拆子包。本轮落地为 `application/{command,query,port,process,exception}`、`interfaces/{web,consumer,dto,scheduler}`、`infrastructure/{persistence,adapter,acl,mq,config}`、`shared/{exception,message,util}`、`domain/claim/{material,issue,person,port,exception}`、`domain/claimcase/{port}`。
- 异步内核（Outbox 投递、消费去重、交互轨迹、滞留补偿）不再单独成域：端口与端口模型在 `application/port` 与 `application/port/model`，编排在 `application/process`，持久化在 `infrastructure/persistence`，调度入口在 `interfaces/scheduler`。记录装配工厂只做载荷序列化与标识生成，交互事件本身由 `InteractionLog.of(...)` 装配。
- 两个 `@RestControllerAdvice` 都限定 `basePackages` 到 `interfaces/web`：无作用域限制的兜底 advice 会接管其他入站通道的异常，与限定作用域的 advice 优先级不确定。
- `shared` 不含用例：只含跨层异常、消息契约与工具，因此不依赖任何业务分层。
- 外部系统能力由 `infrastructure/acl` 的日志实现提供：审核、承保、文件各一个极简实现，只打印调用日志并返回可继续流程的默认结果，接真实系统时替换本包实现；Redis、Redisson、发号、锁与熔断等技术适配在 `infrastructure/adapter`。

## 5. 判定与自检

新写一个类，问三个问题即可定位层：

1. 换掉 MySQL/RocketMQ/Redis，这个类要不要改？要改 → `infrastructure`。
2. 它承载业务不变式或业务词汇吗？是 → `domain`（哪怕它是接口或枚举）。
3. 它只是把外部请求翻译成用例调用吗？是 → `interfaces`。三者都不是 → `application`。

可执行的扫描（在 `backend/claim-application-service/src/main/java/com/wangguangwu/claim/application` 下执行，除第 8 项应为 2 外，其余必须为 0）：

```bash
P=com.wangguangwu.claim.application
# 1 domain 层框架依赖
grep -rl -E 'import (org\.springframework|com\.baomidou|org\.redisson|feign\.|org\.apache\.rocketmq|com\.alibaba|org\.mapstruct|com\.fasterxml)' --include='*.java' domain | wc -l
# 2 domain 依赖任何上层
grep -rh "^import ${P}\.\(interfaces\|application\|infrastructure\)\." --include='*.java' domain | wc -l
# 3 infrastructure 依赖 application 的包必须落在 port/port.model/query.model
grep -rh "^import ${P}\.application\." --include='*.java' infrastructure \
  | grep -v -E '\.application\.(port|query\.model)\.' | wc -l
# 4 application 依赖 interfaces/infrastructure
grep -rh "^import ${P}\.\(interfaces\|infrastructure\)\." --include='*.java' application | wc -l
# 5 interfaces 依赖 infrastructure
grep -rh "^import ${P}\.infrastructure\." --include='*.java' interfaces | wc -l
# 6 shared 反向依赖业务分层
grep -rh "^import ${P}\.\(domain\|application\|interfaces\|infrastructure\)\." --include='*.java' shared | wc -l
# 7 DO 跨出 infrastructure；非 ORM 的 *Mapper
grep -rl -E '\b[A-Za-z]+DO\b' --include='*.java' . | grep -vc '^./infrastructure/'
find . -name '*Mapper.java' | grep -vc 'DOMapper\.java$'
# 8 domain 接口只剩聚合 Repository（应为 2：ClaimApplicationRepository、ClaimCaseRelationRepository）
grep -rl '^public interface' --include='*.java' domain | wc -l
# 8b domain 接口必须全部位于 port 子包（应为 0）
grep -rl '^public interface' --include='*.java' domain | grep -vc '/port/'
# 9 聚合根封装：domain 内不得有 Lombok @Setter 或显式 public setter
grep -rn '@Setter\|public void set' --include='*.java' domain | wc -l
# 10 聚合创建所有权：owning 域之外不得直接 new 聚合
grep -rn 'new ClaimApplication(\|new Material(\|new ClaimApplicationIssue(' --include='*.java' . | grep -v -E '^\./domain/claim/' | wc -l
grep -rn 'new OutboxEvent(' --include='*.java' . | grep -v -E '^\./application/port/model/|^\./infrastructure/persistence/' | wc -l
grep -rn 'new InteractionLog(' --include='*.java' . | grep -vc '^\./application/port/model/'
# 11 入站层不得引用聚合根
grep -rhE '\bClaimApplication\b' --include='*.java' interfaces | wc -l
# 12 应用层不得自行绑定配置
grep -rn '@ConfigurationProperties' --include='*.java' application | wc -l
# 13 样板日志：不得再手写 Logger（统一 Lombok @Slf4j）
grep -rn 'LoggerFactory' --include='*.java' . | wc -l
# 14 domain/application 生产代码直接依赖异常基类（分层异常类自身除外）
grep -rn '^import .*shared\.exception\.BusinessException' --include='*.java' domain application \
  | grep -v '/exception/' | wc -l
```

当前结果：第 1～7、9～14 项均为 0；第 8 项 2 个接口（`ClaimApplicationRepository`、`ClaimCaseRelationRepository`，均在 `port` 子包），第 8b 项 0。

业务行为不保留单元测试，统一在运行栈上按黄金路径验证；`ClaimApplicationArchitectureTest` 是唯一保留的测试，持续校验分层方向，`mvn -pl backend/claim-application-service -am test` 仅运行该分层规则。

## 6. 本项目的标准与方言分界

标准强制项与本项目方言的完整清单、以及历次为对齐词典而重命名/重定位的类和目录组织形式的裁定，记录在
[005 Spec 设计文档的"目录词典"](../../specs/005-application-service-package-refactoring/design.md)；
025 之后的角色分包调整记录在
[026 Spec 设计文档](../../specs/026-package-standard-alignment/design.md)。
后续对"这样放对不对"的争议，先查这两份清单，不在清单里的才需要重新裁定——不要每次凭感觉重议。
