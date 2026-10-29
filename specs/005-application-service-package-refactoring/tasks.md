# 005 理赔申请服务包重构任务

## T1 包结构与代码迁移

- 状态：已完成
- 对应 design：总体架构、目标目录
- 产物：`interfaces`、`usecase`、`domain`、`infrastructure`、`shared`、`support` 根包及完成迁移的生产代码；`ApplicationEventEnvelope`、`UnderwritingCompletedCommand` 下沉 `shared/contract`；跨包 import 补齐与 import 顺序统一；迁移过程中产生的中间目录壳与 `.gitkeep` 占位删除。
- 完成条件：生产代码根包只有四层加 `shared`、`support`，不保留旧目录壳与空目录，类职责与业务行为不变。

## T2 测试与文档同步

- 状态：已完成
- 对应 design：目标目录、依赖方向规则、数据模型和接口设计、对现有功能的影响
- 产物：迁移后的测试包；本 Spec 的依赖方向规则与目标目录；`docs/architecture/claim-application-architecture.md` 目录说明；`docs/architecture/ddd-standard-architecture.md` 标准说明与扫描自检；003、004 design 的改动模块表按最终目录改写。
- 完成条件：测试代码无旧包引用，文档准确描述层目录、依赖方向约束与网关压测入口。

## T3 构建与回归验证

- 状态：已完成
- 对应 design：依赖方向规则、基础设施与异常处理
- 产物：Maven 测试结果、依赖方向检查结果与经网关的运行栈黄金路径验证记录。
- 完成条件：`mvn -B -pl backend/claim-application-service -am test` 通过；跨域 `interfaces`/`infrastructure` 引用、DO 跨层、`domain` 依赖基础设施框架均为 0；JMeter 默认入口仍为网关，首次签收、同号幂等、同号并发、`ACCEPT_FAILED` 重新申请与未支持租户错误映射行为与迁移前一致。

## T4 标准目录词典对齐

- 状态：已完成
- 对应 design：目录词典（标准强制项与本项目方言）、目标目录
- 产物：入站层去 `inbound` 中转层改为通道包（`web`/`messaging`/`scheduling`/`tenantprotocol`）；申请与材料持久化角色合并进 `persistence`；`integration` 持久化按聚合分 `persistence/{outbox,interaction,consumption}`；`underwriting` 外部适配去同义子层；跨域异常与加密工具上浮 `shared`；`ApplicationDataMapper`→`ApplicationDataConverter`、`MaterialDataMapper`→`MaterialDataConverter`；`@RestControllerAdvice(basePackages)` 同步。
- 完成条件：目录与 design 的词典一致，无 `inbound` 中转层、无同义反复子层、无以 `Mapper` 结尾的 DO↔领域对象转换器；生产与测试代码无旧包引用；Maven 测试与依赖方向检查通过。

## T5 依赖方向闭合

- 状态：已完成
- 对应 design：目录词典（标准强制项）、依赖方向规则
- 产物：8 个类的层次重定位——端口 `OutboxEventPublisher` 与事件词汇 `ApplicationEventType` 入 `domain/integration/outbox`，`OutboxRelayScheduler` 入 `interfaces/integration/scheduling`，`UuidMaterialBatchNoGenerator` 入 `infrastructure/material/number`，`ApplicationIntakeException` 与 `ApplicationIntakeErrorCode` 入 `domain/claim/exception`，`ApplicationAsyncException` 与 `ApplicationAsyncErrorCode` 入 `shared/contract`；空目录删除；本 Spec 词典与目标目录同步。
- 完成条件：`infrastructure → usecase`、`usecase → interfaces`、`domain → 框架`、DO 跨层、跨域 `interfaces`/`infrastructure` 五项扫描均为 0，出站端口全部声明在 `domain`；`mvn -B -pl backend/claim-application-service -am test` 通过。

## T6 聚合封装与所有权归位

- 状态：已完成（代码与单测通过，黄金路径回归并入 T7）
- 对应 design：目录词典（标准强制项：聚合根封装、聚合创建知识只留在 owning 域）、聚合封装与所有权归位
- 产物：`ClaimApplication`、`Material` 去 Lombok setter、构造器私有并增 `restore(...)` 反水化工厂，主键改由 `bindId(...)` 守卫回填；`MaterialDataConverter` 读方向改 `default` 方法调 `Material.restore`，`ApplicationDataConverter` 因 MapStruct 无法向抽象基类做构造器注入且聚合无 setter 而整体改为显式手写的普通 Spring 组件；`ApplicationIntakeRecordFactory` 的 Outbox 与交互日志构造改委托 `ApplicationLifecycleRecordFactory` 并缩为 `ApplicationIntakeMaterialFactory`；材料创建改为 `MaterialSubmission.toMaterial(applicationNo, batchNo)`；受影响测试的 `setStatus`/`setId` 改用受控工厂；005 与本文件、`docs/architecture` 同步。
- 完成条件：`domain` 聚合根无公开 setter 且构造器私有；`usecase/integration/record` 与 `domain/integration` 之外不出现 `new OutboxEvent`、`OutboxEvent.pending`、`new InteractionLog`；`domain/material` 之外不出现 `new Material`；状态流转、错误码、HTTP 响应与落库结果与归位前逐项一致；`mvn -B -pl backend/claim-application-service -am test` 通过。

## T7 回退为层级优先目录

- 状态：已完成
- 对应 design：总体架构、目标目录、目录组织决策记录、依赖方向规则
- 产物：129 个生产类与对应测试类从"业务域优先"迁到 `interfaces`/`usecase`/`domain`/`infrastructure` 四层（`intake/domain` → `domain/claim`，`<域>/adapter` → `interfaces/<域>`，`<域>/application` → `usecase/<域>`，`<域>/infrastructure` → `infrastructure/<域>`）；`shared/adapter/web` → `shared/interfaces/web`、`support/mock/adapter/web` → `support/mock/interfaces/web`；`InteractionType` 不动；`docs/architecture` 两份文档与本 Spec 三份文档同步为层级形状，归零扫描表达式同步。
- 完成条件：根包只剩四个层名加 `shared`、`support`；design「依赖方向规则」的禁止项扫描全部为 0；`mvn -B -pl backend/claim-application-service -am test` 通过；经 `claim-gateway` 的黄金路径（首次签收、同号幂等、同号并发单行、未支持租户错误映射、tenant-b 协议、异步链路至 `WAIT_AUDIT`、材料失败与重新申请）与回退前逐项一致。

## T8 位置与名称复核

- 状态：已完成
- 对应 design：目录词典（标准强制项：聚合创建知识归属、同一后缀单一含义）、位置与名称复核
- 产物：交互事件聚合构造收回 `domain/integration/interaction`——新增 `InteractionLog.of(...)` 工厂与 `InteractionDirection`、`InteractionResultStatus` 枚举，`InteractionType.direction()/targetSystem()`、`InteractionAction.resultStatus()` 承载固定映射，`ApplicationLifecycleRecordFactory` 去掉三份 `switch`、七个字符串常量和 `new InteractionLog`；删除 `ApplicationIntakeUseCase` 接口并把实现 `ApplicationIntakeService` 改名为 `ApplicationIntakeUseCase`（含测试类）；`TenantProtocolValidation` 改名 `TenantProtocolFieldGuard`，其"构建不合法异常"下沉为 `ApplicationIntakeException.invalid(...)` 并统一五处调用点与 `TenantProtocolPipeline{Registry,Factory}`、签收用例中的同类构造；`ApplicationIntakeExceptionHandler` 的 `@RestControllerAdvice` 限定 `basePackages` 到 `interfaces.intake.web`；归零扫描第 10 项去掉 `usecase/integration/record` 白名单并新增第 11 项词汇唯一性；架构文档第 1、3、4、5 节与 003 Spec 的类名同步。
- 完成条件：owning 域之外不出现聚合的 `new`（第 10 项为 0，无白名单）；交互词汇字面量只在 `domain/integration/interaction` 出现（第 11 项为 0）；`*UseCase` 后缀在四个域内都是具体类；无只差一个词的类名对；`mvn -B -pl backend/claim-application-service -am test` 通过；经 `claim-gateway` 的黄金路径（首次签收、同号幂等、同号并发单行、未支持租户错误映射、tenant-b 协议、异步链路至 `WAIT_AUDIT`、材料失败与重新申请）与复核前逐项一致，落库的 `direction`/`target_system`/`result_status` 取值不变。

## T9 标准 DDD 归位收尾

- 状态：已完成
- 对应 design：目录词典（标准强制项：聚合组装只发生在用例与领域、配置绑定属于实现细节）、标准 DDD 归位追加
- 产物：`MaterialSubmission` 由 `domain/material` 移入 `domain/claim`（申请提交的原始材料是签收命令的一部分），材料聚合改为提供 `Material.createPending(applicationNo, batchNo, submission)` 重载，删除 `toMaterial(...)`；`ApplicationIntakeCacheStaleException` 改名 `ApplicationExistenceMismatchException`，用例层的内部重试信号不再借用缓存词汇；`tenantprotocol/tenant` 改名 `tenantprotocol/tenants`；`OutboxRelayProperties`、`ApplicationCompensationProperties` 移出 `usecase` 到 `infrastructure/integration/config`，分别实现新增的 `domain/integration/outbox/OutboxRelayPolicy`、`domain/integration/compensation/ApplicationCompensationPolicy`，退避移位与截断算法随适配器从用例迁入；新增 `usecase/intake/command/ApplicationIntakeCommand`，租户协议 `Converter`/`Pipeline`/`AbstractTenantProtocolPipeline`/`PipelineFactory`/`Controller` 全链路改为产出命令，`ClaimApplication.submit(...)` 由 `ApplicationIntakeUseCase` 调用；两个用例测试改打桩策略端口，新增 `OutboxRelayPropertiesTest` 覆盖退避曲线；架构文档第 1～5 节与本 Spec 同步（扫描第 8 项端口数改为 16，新增第 12 项"入站层不引用聚合根"与第 13 项"用例层不绑定配置"）。
- 完成条件：`interfaces` 对 `ClaimApplication` 的引用为 0（第 12 项为 0）；`usecase` 无 `@ConfigurationProperties` 且不 import `infrastructure`（第 4 项为 0）；`domain` 端口 16 个（第 8 项）；聚合工厂调用点只在 `domain` 与 `usecase`；`mvn -B -pl backend/claim-application-service -am test` 通过；经 `claim-gateway` 的黄金路径（首次签收、同号幂等、同号并发单行、未支持租户错误映射、tenant-b 协议、异步链路至 `WAIT_AUDIT`、Outbox 双事件投递、材料失败与重新申请）与归位前逐项一致，落库字段与交互日志取值不变。
