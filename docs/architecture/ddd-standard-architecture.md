# 标准 DDD 架构说明

本文回答一个问题：什么叫"符合标准 DDD"，判定依据是什么，以及 `claim-application-service` 的 133 个类各自落在哪一层。
文档面向本项目开发者，术语以 Eric Evans / Vaughn Vernon《实现域驱动设计》、业界广泛引用的四层结构、Alibaba COLA 4.0 与 Fowler《企业应用架构模式》为基准。

## 1. 标准只规定三件事

DDD 的标准约束只有三条，其余都是项目方言：

1. **层的职责**：每一层放什么类型、不放什么类型，有明确定义。展开成三条最容易被漏掉的硬规则：
   - **聚合自己守不变式**：聚合根不得对层外暴露公开 setter，构造器私有，状态只能经命名领域方法迁移；从数据库重建聚合走 owning 域的反水化工厂，不能"先构造再逐字段 set"。写了状态机又开着 `setStatus`，等于没有状态机。
   - **创建知识只留在 owning 域**：其他层不得 `new` 本域聚合或逐字段拼装其状态，只能调用该域 `domain` 暴露的工厂方法；入站适配器也不得组装聚合——租户协议流水线的产物是 `ClaimApplicationIntakeCommand` 命令，聚合由签收用例调用 `ClaimApplication.submit(...)` 创建，否则聚合的组装知识在 `interfaces` 又有第二份；同一类记录（如异步 Outbox 事件与交互日志）只允许一个装配入口，且装配入口不得自行推断领域词汇——方向、目标系统、结果状态这类固定映射挂在说出它们的枚举上（`InteractionType.direction()`、`InteractionAction.resultStatus()`），否则用例层就成了第二套业务定义。
   - **业务性拒绝说业务的语言**：不变式违反应抛本域业务异常与错误码，常见拒绝走异常类上的命名静态工厂（`ClaimApplicationIntakeException.invalid(...)`）统一构造，`IllegalStateException` 这类 JDK 异常留给真正的编程错误；否则入站层只能靠兜底 handler 把业务拒绝上报成系统错误。
2. **依赖方向**：`interfaces → usecase → domain ← infrastructure`，且 `domain` 不依赖任何框架。这是"依赖倒置"，是标准，不是风格。配置阈值同样是实现细节：用例需要批量、退避与滞留阈值时，在 `domain` 声明 `*Policy` 端口，由 `infrastructure/<域>/config` 的 `*Properties` 绑定配置实现；把 `@ConfigurationProperties` 挨着用例放，用例层就带上了 Spring，换配置中心要改用例。
3. **对象角色命名**：一个类的后缀要能说明它在模式里的角色——`DO`、ORM 映射、转换器、仓储实现、端口、值对象各不相同。Fowler 的 Data Mapper 定义决定了 `*Mapper` 只能是 ORM，这就是为什么 DO↔领域对象的转换器不能叫 `Mapper`。同一后缀在整棵树上只能有一种含义：`*UseCase` 一律是用例入口具体类（不为它保留单实现接口），`*TransactionService` 一律是事务边界；两个名字只差一个词（`*Validation` 与 `*Validator`）视为职责未说清，必须改名拆分。

标准**不规定**：

- **目录组织形式**：先层级（`domain/intake`）还是先业务域（`intake/domain`），DDD 没有立场，只要求四层职责清楚、方向单一。本项目定案为**层级优先、层内分域**。
- **目录粒度**：`infrastructure` 下要不要按聚合、按技术角色再分子包，Vernon 的原书示例按聚合分，COLA 全部平铺，两者都合规。
- **包名拼写**：`usecase` 与 `application`/`app`、`interfaces` 与 `adapter`、`external` 与 `gatewayimpl`，都是流派差异。

因此判断"是否标准"只能靠可扫描的规则，不能靠目录看起来像不像某个模板。

## 2. 四层职责

| 层 | 职责 | 放什么 | 禁止放什么 |
|---|---|---|---|
| `interfaces` | 把外部请求翻译成用例调用（Ports & Adapters 里的 driving adapter） | Controller、MQ 消费者、定时任务触发器、租户协议解析与防腐、HTTP 请求/响应 DTO、字段渲染 | 业务规则、数据库访问、事务编排、聚合组装 |
| `usecase` | 用例编排：按顺序调用领域与端口，管理事务、幂等与重试 | UseCase、TransactionService、记录装配工厂、命令入参与结果出参 | 业务不变式（属于聚合）、DO、框架化的持久化细节、配置绑定 |
| `domain` | 业务本身：状态、不变式、词汇 | 聚合根、实体、值对象、状态枚举、领域事件、**端口（Repository/Gateway 接口，集中在该聚合的 `port` 子包）**、业务异常与错误码、领域服务 | Spring、MyBatis-Plus、Redisson、MapStruct、Feign、MQ、Jackson；DO；任何序列化细节 |
| `infrastructure` | 端口的技术实现（driven adapter） | `*DO`、ORM `*DOMapper`、DO↔领域对象 `*Converter`、`MyBatis*Repository` 实现、Redis/锁/编号生成、Feign 客户端、MQ 发送、`*Policy` 端口的 `*Properties` 配置适配器 | 业务规则、用例流程决策 |

三条最容易搞错的归属，逐条说明：

- **端口在 `domain`，实现留在 `infrastructure`。** 领域决定"我需要什么能力"（保存申请、生成编号、加幂等锁、投递事件），实现是被替换的技术细节。只有方向反过来才成立：`infrastructure` 依赖 `domain`，绝不依赖 `usecase`。这也是本项目最近一次修正的内容——把 `OutboxEventPublisher`、业务异常从 `usecase` 移回 `domain`，否则实现类只能反向 import。
- **DO 永远不进 `domain`。** DO 是表结构的影子，进领域层就等于让数据库形状决定业务模型。DO 只允许出现在 `infrastructure`，跨层传的一律是领域对象。
- **`*Converter` 与 `*Mapper` 是两种东西。** `ClaimApplicationDOMapper extends BaseMapper<ClaimApplicationDO>` 是 ORM；`ClaimApplicationDataConverter` 是 DO↔领域对象的映射器（MapStruct 的 `@Mapper` 注解是框架 API，不是类名，可以保留）。

## 3. 对象角色词典

| 角色 | 后缀/形态 | 所在层 | 本项目实例 |
|---|---|---|---|
| 聚合根 | 业务名词，record/class，构造器私有或只暴露命名工厂 | `domain/<聚合>` | `domain/claim/ClaimApplication`、`domain/material/Material`、`domain/integration/interaction/InteractionLog`、`domain/integration/outbox/OutboxEvent` |
| 值对象 | 业务名词 | `domain`，人员角色另入 `domain/claim/person` | `PersonInfo` 基类与 `Applicant`/`PrimaryInsured`/`AccidentPerson`/`Payee` 四个角色子类、`MaterialSubmission` |
| 状态/类型枚举 | `*Status`、`*Type`、`*Action`、`*Direction` | `domain` | `ClaimApplicationStatus`、`OutboxEventStatus`、`ClaimApplicationEventType`、`InteractionType`、`InteractionDirection`、`InteractionAction`、`InteractionResultStatus` |
| 枚举自带的业务映射 | 枚举字段与方法 | `domain` | `InteractionType.direction()`/`targetSystem()`、`InteractionAction.resultStatus()`——方向、目标系统、结果状态这类固定词汇由枚举自己回答，不得由用例层 `switch` 推断 |
| 仓储端口 | `*Repository` | `domain/<聚合>/port`（该域只有一个端口时直接放聚合包） | `ClaimApplicationRepository`、`MaterialRepository`、`OutboxEventRepository` |
| 外部能力端口 | `*Query`、`*Generator`、`*Lock`、`*Requester`、`*Processor`、`*Publisher`、`*Policy` | `domain/<聚合>/port` | `ValidPolicyQuery`、`ClaimApplicationNoGenerator`、`ClaimApplicationIntakeLock`、`MaterialFileProcessor`、`OutboxEventPublisher`、`OutboxRelayPolicy`、`ClaimApplicationCompensationPolicy`（16 个端口，其中 13 个在 `port` 子包） |
| 配置适配器 | `*Properties`（实现 `domain` 的 `*Policy` 端口） | `infrastructure/<域>/config` | `OutboxRelayProperties`、`ClaimApplicationCompensationProperties` |
| 仓储实现 | `MyBatis*Repository` | `infrastructure/<域>/persistence` | `infrastructure/intake/persistence/MyBatisClaimApplicationRepository` |
| 持久化对象 | `*DO` | `infrastructure/<域>/persistence` | `ClaimApplicationDO`、`MaterialDO`、`OutboxEventDO` |
| ORM 映射 | `*DOMapper` | `infrastructure/<域>/persistence` | `ClaimApplicationDOMapper` |
| DO↔领域转换器 | `*DataConverter` | `infrastructure/<域>/persistence` | `ClaimApplicationDataConverter`、`MaterialDataConverter` |
| 外部系统适配器 | `Feign*` + `*FeignClient` | `infrastructure/<域>/external` | `FeignAutoUnderwritingRequester`、`UnderwritingFeignClient` |
| 缓存/锁/编号/消息实现 | 技术前缀 + 端口名 | `infrastructure/<域>/{cache,lock,number,messaging}` | `RedisClaimApplicationExistenceQuery`、`RedissonClaimApplicationIntakeLock`、`RocketMqOutboxPublisher` |
| 业务异常与错误码 | `*Exception`、`*ErrorCode`，常见错误用枚举命名的静态工厂 | `domain/<聚合>/exception`（跨域的进 `shared/contract`） | `ClaimApplicationIntakeException`、`ClaimApplicationIntakeException.invalid(...)`、`ClaimApplicationAsyncException` |
| 用例与编排 | `*UseCase`（具体类，不再保留单实现接口）、`*TransactionService`、`*RecordFactory`、`*Factory` | `usecase/<域>` | `ClaimApplicationIntakeUseCase`、`MaterialProcessingUseCase`、`ClaimApplicationIntakeMaterialFactory`、`ClaimApplicationLifecycleRecordFactory` |
| 命令入参/结果出参 | `*Command`、`*Result`、`*Disposition`、`*Outcome` | `usecase/<域>/{command,result}` | `MaterialProcessCommand`、`ClaimApplicationIntakeCommand`、`ClaimApplicationIntakeResult` |
| 入站通道 | `*Controller`、`*Consumer`、`*Scheduler`、`*Pipeline` | `interfaces/<域>/{web,messaging,scheduling,tenantprotocol}`；流水线实现另入 `tenantprotocol/core/pipeline` 与 `tenants/<租户>` | `ClaimApplicationIntakeController`、`MaterialProcessConsumer`、`OutboxRelayScheduler`、`TenantAProtocolPipeline` |
| 报文防腐组件 | `*Parser`、`*Validator`、`*Converter`、`*FieldGuard` | `interfaces/<域>/tenantprotocol/core/stage`（端口）与 `tenants/{tenant_a,tenant_b}`（每租户一套 request/解析/校验/转换） | `TenantARequestParser`、`TenantBRequestValidator`、`TenantARequestConverter`（输出 `ClaimApplicationIntakeCommand`）、`TenantProtocolFieldGuard` |
| 响应渲染 | `*ResponseRenderer`（唯一实现，不再按租户路由） | `interfaces/<域>/web/render` | `ClaimApplicationResponseRenderer` |
| 跨域契约 | 信封、命令 DTO | `shared/contract` | `ClaimApplicationEventEnvelope`、`UnderwritingCompletedCommand` |

## 4. 本项目的落位

根包是四层，业务域是层内第二级；`domain` 层的理赔申请聚合子包按聚合本身命名叫 `claim`。类数合计 137，目录 76 个，`application` 之下最深 5 级包（`interfaces/intake/tenantprotocol/core/pipeline`）。

| 域键 | `interfaces` | `usecase` | `domain` | `infrastructure` |
|---|---|---|---|---|
| 申请签收（`intake` / 聚合 `claim`） | 24 | 7 | 15 | 8 |
| 材料处理（`material`） | 1 | 4 | 7 | 7 |
| 承保确认（`underwriting`） | 3 | 6 | 5 | 4 |
| 异步集成（`integration`） | 2 | 3 | 14 | 12 |
| `shared` | 1 | — | — | 1（另有 `contract` 4） |
| `support` | 2 | — | — | 4（另有 `config` 2） |
| 根包 | — | — | — | 1（`ClaimApplicationServiceApplication`） |

- `interfaces/intake` 的 24 个类里有 18 个是租户协议防腐层（`tenantprotocol/core/{pipeline,stage}` 放流水线装配与五个阶段端口，`tenantprotocol/tenants/{tenant_a,tenant_b}` 放每租户一套 request/解析/校验/转换），这是本项目"多租户报文接不进统一领域模型"的真实复杂度，不是分层装饰。
- 防腐层的产物是 `ClaimApplicationIntakeCommand`，`interfaces` 里对 `ClaimApplication` 的引用为 0；该层对 `domain` 的依赖只剩 `Applicant`/`PrimaryInsured`/`AccidentPerson`/`Payee` 四个人员角色、`Gender`、`MaterialSubmission` 与业务异常，共 12 个文件。
- 包内不平铺：同一包里出现两类以上互不相干的职责、且每类可聚成 ≥2 个类时必须拆子包。本轮落地为 `domain/<聚合>/port`（理赔申请另有 `domain/claim/person` 放人员基类与四个角色）、`interfaces/intake/tenantprotocol/core/{pipeline,stage}`、`tenants/{tenant_a,tenant_b}`、`interfaces/intake/web/render`。一个域只剩一个端口时端口留在聚合包（现为 `interaction`、`consumption`、`compensation`），不建只挂一个子目录的中转层。
- `integration` 是平台异步内核：Outbox 投递、消费去重、交互轨迹装配、滞留补偿。业务域允许调用它的记录装配工厂（`usecase/integration/record`），允许它的补偿重放业务用例，这两条是显式写进 005 的例外，不是默认规则。记录装配工厂只做载荷序列化与标识生成，交互事件本身由 `domain/integration/interaction/InteractionLog.of(...)` 装配。
- 异步链路的批量、退避与滞留阈值由 `domain` 的 `OutboxRelayPolicy`、`ClaimApplicationCompensationPolicy` 声明，`infrastructure/integration/config` 的 `*Properties` 绑定配置实现；调度间隔由 `@Scheduled` 占位符读取，不进入策略端口。
- 两个 `@RestControllerAdvice` 都限定 `basePackages` 到各自的 `interfaces/<域>/web`：无作用域限制的兜底 advice 会接管其他入站通道的异常，与限定作用域的 advice 优先级不确定。
- `shared` 不含用例：只含跨域契约、统一响应与摘要工具，因此没有 `usecase` 子包。
- `support` 不属业务域，放 Spring 配置和受 `claim.mock.enabled` 控制的本地 Mock；内部仍按层名分子包（`support/mock/interfaces/web`、`support/mock/infrastructure`），避免一棵树里混用 `adapter` 与 `interfaces` 两个词。

## 5. 判定与自检

新写一个类，问三个问题即可定位层：

1. 换掉 MySQL/RocketMQ/Redis，这个类要不要改？要改 → `infrastructure`。
2. 它承载业务不变式或业务词汇吗？是 → `domain`（哪怕它是接口或异常）。
3. 它只是把外部请求翻译成用例调用吗？是 → `interfaces`。三者都不是 → `usecase`。

可执行的扫描（在 `backend/claim-application-service/src/main/java/com/wangguangwu/claim/application` 下执行，除第 8 项应为 16、第 8b 项应为 3、第 15 项应为 9 处白名单外结果必须全为 0）：

```bash
P=com.wangguangwu.claim.application
# 1 domain 层框架依赖
grep -rl -E 'import (org\.springframework|com\.baomidou|org\.redisson|feign\.|org\.apache\.rocketmq|com\.alibaba|org\.mapstruct|com\.fasterxml)' --include='*.java' domain | wc -l
# 2 domain 依赖任何上层
grep -rh "^import ${P}\.\(interfaces\|usecase\|infrastructure\|support\)\." --include='*.java' domain | wc -l
# 3 infrastructure 反向依赖 usecase/interfaces
grep -rh "^import ${P}\.\(usecase\|interfaces\)\." --include='*.java' infrastructure | wc -l
# 4 usecase 依赖 interfaces/infrastructure
grep -rh "^import ${P}\.\(interfaces\|infrastructure\)\." --include='*.java' usecase | wc -l
# 5 跨域引用他域 interfaces/infrastructure（domain 层的 claim 与其余三层的 intake 视为同域）
for L in interfaces usecase domain shared support; do for d in $(ls $L 2>/dev/null); do for o in intake material underwriting integration; do
  [ "$d" = "$o" ] && continue; [ "$d" = "claim" ] && [ "$o" = "intake" ] && continue
  grep -rh "^import ${P}\.\(interfaces\|infrastructure\)\.${o}\." --include='*.java' "$L/$d" 2>/dev/null
done; done; done | wc -l
# 6 DO 跨出 infrastructure；7 非 ORM 的 *Mapper
grep -rl -E '\b[A-Za-z]+DO\b' --include='*.java' . | grep -vc '^./infrastructure/'
find . -name '*Mapper.java' | grep -vc 'DOMapper\.java$'
# 8 端口是否都在 domain（应为 16）
grep -rl '^public interface' --include='*.java' domain | wc -l
# 8b 多端口聚合的端口是否都收进 port 子包（应为 3：interaction、consumption、compensation 各一个单端口）
grep -rl '^public interface' --include='*.java' domain | grep -vc '/port/'
# 9 聚合根封装：domain 内不得有 Lombok @Setter 或显式 public setter
grep -rn '@Setter\|public void set' --include='*.java' domain | wc -l
# 10 聚合创建所有权：owning 域之外不得直接 new 聚合（走领域工厂，无白名单例外）
grep -rn 'new Material(\|new ClaimApplication(\|new OutboxEvent(\|new InteractionLog(' \
  --include='*.java' . | grep -v -E '^\./domain/(claim|material|integration)/' | wc -l
# 11 交互词汇不得在 domain 之外重复定义
grep -rn '"INBOUND"\|"OUTBOUND"\|"CLAIM_APPLICATION_SERVICE"\|"MATERIAL_PROCESSOR"\|"UNDERWRITING_SYSTEM"' \
  --include='*.java' . | grep -vc '^./domain/integration/interaction/'
# 12 入站层不得引用聚合根（聚合组装只发生在用例与领域；用词边界排除同前缀的用例命令与结果）
grep -rhE '\bClaimApplication\b' --include='*.java' interfaces | wc -l
# 13 用例层不得自行绑定配置
grep -rn '@ConfigurationProperties' --include='*.java' usecase | wc -l
# 14 样板日志：不得再手写 Logger（统一 Lombok @Slf4j）
grep -rn 'LoggerFactory' --include='*.java' . | wc -l
# 15 样板构造器：纯赋值的依赖注入统一 @RequiredArgsConstructor，白名单外不得手写
grep -rn '^\s*public [A-Z][A-Za-z0-9]*(' --include='*.java' . | wc -l
# 16 人员角色：聚合、受理命令与租户转换器的装配入口不得声明基类 PersonInfo
grep -rn 'PersonInfo' --include='*.java' domain/claim/ClaimApplication.java usecase/intake/command \
  interfaces/intake/tenantprotocol | wc -l
```

当前结果：第 1～7、9～14、16 项全部为 0，第 8 项 16 个端口（其中 13 个在 `port` 子包）、第 8b 项 3 个单端口域、第 15 项 8（两个异常类需要 `super(message)`、两个租户流水线需要 `super(...)` 装配组件、四个人员角色值对象需要 `super(...)` 传递基础字段；Lombok 不生成 `super` 调用，这些都属于有逻辑的构造器，不在 `@RequiredArgsConstructor` 范围），生产类 135、目录 76。单元测试代码在功能定稿前全部移除，`mvn -B -pl backend/claim-application-service -am package` 编译通过；经 `claim-gateway` 的黄金路径（首次签收、同号幂等、改造前落库的人员 JSON 由新代码按角色还原、tenant-b 协议、未支持租户错误映射、同号并发单行、异步链路至 `WAIT_AUDIT`、材料失败与重新申请覆盖四个角色字段并进入第 2 次尝试）逐项一致。

第 14、15 项是样板收敛的量化口径，属于本项目方言而不是标准强制项：依赖注入统一 `@RequiredArgsConstructor` + `private final`，日志统一 `@Slf4j`，多字段装配统一 `@Builder`（`ClaimApplication`、`Material` 的建造者是 `@Builder(access = PRIVATE)`，公开的创建入口仍只有 `submit`/`createPending`/`restore`/`reapply`；`ClaimApplicationIntakeCommand` 与 `*DO` 用公开建造者）。第 16 项约束人员角色的装配口径：四类人员不共用一个 `PersonInfo` 位置参数，而是由 `PersonInfo` 基类派生 `Applicant`/`PrimaryInsured`/`AccidentPerson`/`Payee` 四个密封（sealed）终态子类，聚合字段、受理命令与租户转换器一律按角色类型声明，四个同名字段被按位置装错在编译期就不可能；基类只持有四个基础字段与相等语义，新增角色必须显式出现在 `permits` 列表里。方言改动不得松动第 9、10 项：聚合的字段写入只能发生在聚合自己内部。

第 9、10、12、13 项对应"层的职责"里的聚合封装与所有权和"依赖方向"里的配置归属，此前不满足（`ClaimApplication`、`Material` 暴露 Lombok setter；`usecase/intake` 直接构造材料聚合与异步记录聚合，与 `integration` 的记录装配工厂形成两套实现；交互事件本身也靠在用例层 `new InteractionLog` 拼装），由 005 的 T6、T8、T9 归位后归零；两个聚合的构造器已改私有，只能经 `submit`/`createPending`/`restore`/`pending`/`of` 工厂创建，且工厂调用点只出现在 `domain` 与 `usecase`；`OutboxEvent` 是投递状态的记录型，与规范构造器逐字等价的 `restore` 已删除，持久化层直接以规范构造器还原。第 11 项是同一规则的另一面：装配入口不得自带一套业务词汇。"层的职责"第三条——不变式失败改说本域业务异常与错误码——会改变对外错误语义，不在 005 范围，另立 Spec。

## 6. 本项目的标准与方言分界

标准强制项与本项目方言的完整清单、以及历次为对齐词典而重命名/重定位的类和目录组织形式的裁定，记录在
[005 Spec 设计文档的"目录词典"](../../specs/005-application-service-package-refactoring/design.md)。
后续对"这样放对不对"的争议，先查那份清单，不在清单里的才需要重新裁定——不要每次凭感觉重议。
