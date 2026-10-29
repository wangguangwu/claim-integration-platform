# 003 理赔申请统一接入与签收任务

> 2026-10-29 起功能定稿前不保留单元测试：下文任务提到的测试类已随该决定删除，任务正文按当时交付记录保留。

## T1 Spec 评审与接入契约冻结

- 状态：已确认
- 对应 design：§总体架构、§核心链路设计、§接口设计
- 产物：评审通过的 `requirements.md`、`design.md`、`tasks.md`
- 完成条件：已确认首次材料至少一项、人员字段为姓名/性别/证件类型/证件号、理赔申请/批次编号规则及“Redis 主计数、MySQL 最大申请号兜底”规则；变更先同步三份 Spec

## T2 MapStruct、Lombok 与分层模型骨架

- 状态：已完成
- 对应 design：§改动模块、§主要类职责、§MapStruct 映射设计
- 产物：MapStruct、Lombok 与注解处理器绑定配置，理赔申请/Material DO、领域模型、状态枚举、Gender 枚举、PersonInfo 人员基类与四个角色值对象、MapStruct Mapper 接口
- 完成条件：DO 字段与 002 DDL 一致；领域层不依赖 Spring、MyBatis-Plus；Lombok 生成的访问器可被 MapStruct 正确识别，映射实现可生成并通过编译

## T3 租户协议流水线与协议路由

- 状态：已完成
- 对应 design：§总体架构、§主要类职责、§示例租户协议适配、§接口设计
- 产物：TenantProtocolPipeline、AbstractTenantProtocolPipeline、TenantProtocolPipelineRegistry、TenantProtocolPipelineFactory、TenantProtocolParser/Validator/Converter 接口、TenantProtocolFieldGuard、租户 A 与租户 B 的 request record 及其解析/校验/转换组件与流水线
- 完成条件：工厂按 TenantContext 的租户编码和协议版本从注册中心选择唯一流水线；流水线固定按验签、解密、解析、校验、转换执行，示例协议使用验签和明文直通默认实现；`tenant-a/v1` 与 `tenant-b/v1` 均正确归一化为同一份 `ClaimApplicationIntakeCommand` 受理命令；缺失或重复流水线返回明确错误；租户私有 DTO 不进入用例层；报文转换不使用 Jackson JsonNode

## T4 理赔申请与 Material 持久化

- 状态：已完成
- 对应 design：§核心数据模型、§并发控制与旁路缓存设计、§并发控制、事务与异常处理
- 产物：理赔申请、Material、InteractionLog、OutboxEvent Repository 端口与 MyBatis-Plus BaseMapper + Wrapper 实现，Redis 编号缓存及 MySQL 最大申请号兜底、冲突后计数器校准、Redisson 锁（30 秒等待、watchdog、故障降级）、Redisson 延迟连接与超时配置、旁路缓存查询和批次编号生成器
- 完成条件：可创建、状态条件更新重新申请、逻辑删除旧材料、创建新批次、写入交互事件和 OutboxEvent；Mapper 只继承 BaseMapper，不保留 `@Select`/`@Update` 手写 SQL；Redis 正常和降级路径的日序列均不重复；锁等待超时返回 `APPLICATION_INTAKE_BUSY`；Redis 停机时锁、存在查询和编号生成均走 MySQL 降级路径，单次降级调用在 2 秒内完成；Redis 恢复后计数器自动抬升到 MySQL 最大尾号

## T5 申请签收用例

- 状态：已完成
- 对应 design：§核心链路设计、§并发控制、事务与异常处理
- 产物：ClaimApplicationIntakeUseCase（锁与事务外准备，原单实现接口与应用类已在 005 T8 合并为同名具体类）、ClaimApplicationIntakeMaterialFactory（材料批次装配）、ApplicationLifecycleRecordFactory（交互与 Outbox 记录装配，005 收敛后的单一入口）、ClaimApplicationIntakeTransactionService（数据库事务）、首次申请、重复请求、重新申请与冲突重试编排
- 完成条件：事务内不访问 Redis；存在性判断和编号生成在锁内事务外完成；首次创建、重复复用、失败后重申请完全符合 requirements；理赔申请、Material、InteractionLog、OutboxEvent 的核心数据保持事务一致

## T6 统一接入接口

- 状态：已完成
- 对应 design：§接口设计
- 产物：ApplicationIntakeController、租户响应渲染 SPI 与路由、统一结构渲染器、请求响应模型、统一错误映射
- 完成条件：接口正确传递租户上下文和原始协议至适配器，不在 Controller 中实现幂等、状态或数据库逻辑；未定制响应协议的租户返回统一结构并携带 `applicationNo`；错误码按统一映射返回 400、500、503

## T7 单元测试与集成测试

- 状态：已回退（2026-10-29 决定：功能未定稿前不保留单元测试，`src/test` 已全部删除，经网关的 JMeter 脚本保留；功能定稿后按本任务产物与完成条件补齐）
- 对应 design：§核心链路设计、§并发控制与旁路缓存设计、§并发控制、事务与异常处理
- 产物：领域、用例、协议流水线路由、示例协议流水线、锁降级、编号降级与计数器校准、响应渲染单元测试；经 `claim-gateway` 对外接口执行的 JMeter 吞吐与幂等并发压测脚本
- 完成条件：覆盖首次签收、重复请求、重新申请、并发重新申请降级为复用、流水线缺失、非法材料、两种协议全链路转换、锁等待超时、Redis 降级、缓存陈旧重试、计数器落后校准；JMeter 脚本可经网关验证唯一申请吞吐和同申请号幂等并发，以 HTTP 响应、`X-Trace-Id` 和响应编号一致性验收，不直接访问数据库

## T8 构建与回归验证

- 状态：已完成
- 对应 design：§对现有功能的影响
- 产物：构建、测试、Docker MySQL/Redis 接入验证记录
- 完成条件：已通过 JDK 21 下 `mvn -B test`（44 个测试）与 `mvn -B -DskipTests package`；本地验证首次签收、重复提交、10 路同号并发、`tenant-a/v1` 与 `tenant-b/v1` 双协议、未支持租户与非法材料错误映射；`t_` 前缀表名与 MyBatis-Plus Wrapper 持久化可用；Redis 停机时服务可启动、就绪探针为 UP、签收走 MySQL 降级并约 1 秒返回；Redis 恢复后计数器自动对齐并继续发号
