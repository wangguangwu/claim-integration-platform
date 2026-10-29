# 006 全项目评审整改任务

任务与追踪系统的 `P*` 编号一一对应，轮内可并行，轮间有门禁。

## 第 1 轮 · 启动与运行风险

| 编号 | 任务 | 产物 | 完成条件 | 追踪 |
|---|---|---|---|---|
| T1 | 假件开关与外部地址默认值改造 | `application.yml` 基础文档、`*Properties` 校验 | 未配外部地址时启动失败并报出键名；`claim.mock.enabled` 未显式开启时假件接口不注册 | #45 |
| T2 | 运行时必需配置移出 local profile | 基础文档 + local 覆盖文档 | `--spring.profiles.active=prod` 启动成功、两个调度按配置间隔触发；local 黄金路径不变 | #44（依赖 T1） |
| T3 | 租户报文解析保留 cause | `ApplicationBusinessException` cause 构造、两处 Parser | 投递畸形 JSON 时日志出现原始 Jackson 异常类型与申请号，响应错误码不变 | #46 |
| T4 | 交互轨迹 STARTED 入事务 | 三个 `*TransactionService` | 人为让外部调用抛错后，`t_claim_interaction_log` 无只有 STARTED 的残留；黄金路径轨迹条数与尝试次数一致 | #48 |

## 第 2 轮 · 死代码与依赖

| 编号 | 任务 | 产物 | 完成条件 | 追踪 |
|---|---|---|---|---|
| T5 | 删除租户响应渲染 SPI | 单一渲染入口 | `web/render` 剩 1 个类；两类租户与未支持租户响应逐字段不变 | #49 |
| T6 | 删除零引用枚举值与伪端口 | 领域与端口签名收敛 | 引用计数归零或按新签名收敛；`PolicySnapshot` 不存在 | #50 |
| T7 | 删除五个零引用依赖 | 根与模块 `pom.xml` | 各 pom 中上述 artifact 的直接声明计数 0（`spring-boot-starter-validation` 由 spring-cloud-stream 与 openfeign 传递引入，依赖树计数无法归零）；`clean package` 通过、jar 体积下降 |

> 2026-10-29 修订：`spring-boot-starter-validation` 已由 [008 签收必填校验注解化](../008-intake-annotation-validation/design.md) 恢复为 `claim-application-service` 的直接依赖。T7 的删除依据是该依赖零引用，008 引入 `jakarta.validation` 注解后该前提失效；其余四个依赖仍保持删除。 #51 |

## 第 3 轮 · 语义收敛（先立本 Spec 语义，再改码）

第 3 轮开工前先完成 [007 命名全链路收敛](../007-claim-application-naming/tasks.md) 的 N1–N8：T8/T11/T12 与本 Spec 第 3 轮之外的改名会落在同一批类上，先后顺序颠倒会产生一次全量二次 diff。

| 编号 | 任务 | 产物 | 完成条件 | 追踪 |
|---|---|---|---|---|
| T8 | 业务异常与错误码合一 | `shared/contract` 单一异常与枚举、两个 advice、集成契约错误码表 | 异常类型数=1、错误码枚举数=1；既有错误响应逐条比对不变 | #57 |
| T9 | 不变式失败映射业务码 | 领域抛业务异常、advice 映射 | 状态机违例经网关返回 4xx/业务码，不再 500；重试超限返回 `INTAKE_BUSY` | #47 |
| T10 | 必填校验与状态判定单一化 | `PersonInfo`/`ClaimApplication` 自守、Validator 收缩、仓储去判定 | 重复实现点计数 0；非法输入在领域构造即失败；重新申请判定只出现在聚合 | #56（依赖 T9；必填校验部分由 [008](../008-intake-annotation-validation/design.md) 交付，本行状态以 008 完成为准） |
| T11 | 租户接入实现去重 | 泛型 Parser、配置化 Pipeline、`toPerson` 装配 | `tenants` 行数 471→≤260；两类租户响应逐字段不变 | #55 |
| T12 | 事件载荷类型化与 MQ 名称收口 | `shared/contract` 载荷 record、`MqContract`、tag 设置 | 载荷字符串键名非定义处引用 0；主题字面量 23→1 处定义；消费组双写消除 | #58 |
| T13 | 契约路径外置与时间/映射风格统一 | Feign 路径配置化、`BusinessClock`、MapStruct 转换 | `Asia/Shanghai` 字面量 1 处；Feign 注解无 `/mock` 字面量；仓储内联转换 0；domain 无列宽常量 | #59 |

## 第 4 轮 · 数据层

| 编号 | 任务 | 产物 | 完成条件 | 追踪 |
|---|---|---|---|---|
| T14 | 补 Outbox 索引、处置死表与逻辑删除 | DDL 迁移脚本、`@TableLogic` | 两条查询执行计划走索引且无 filesort；DDL 与 `@TableName` 集合一致 | #60 |
| T15 | 引入 Flyway 与环境变量对齐 | `db/migration`、compose、`.env.example` | 空卷自动建表；已有卷迁移幂等且有 schema 版本记录；`.env` 与 yml 占位符 1:1 | #61 |

## 第 5 轮 · 定位能力与验证网

| 编号 | 任务 | 产物 | 完成条件 | 追踪 |
|---|---|---|---|---|
| T16 | traceId 贯穿与指标暴露 | `logback-spring.xml`、MDC filter、MQ 消息头 | 同一 traceId 可 grep 出网关→应用→调度→消费全链；`metrics` 可访问 | #62 |
| T17 | 黄金路径与契约静态检查脚本 | `scripts/smoke/golden-path.sh`、`scripts/check-contract.sh`、`scripts/loadtest.sh` | 干净库一次跑通输出 PASS；故意改坏一处能让检查失败；文档绕过网关的 curl 计数 0 | #63 |

## 第 6 轮 · 结构收敛与文档

| 编号 | 任务 | 产物 | 完成条件 | 追踪 |
|---|---|---|---|---|
| T18 | 修正历史 Spec 空头验收口径与文档事实源 | `specs/001`、`specs/002`、`docs/` 修订 | `grep '9 个用例|clean test'` 在 specs 命中 0；docs 与代码逐条一致 | #64 |
| T19 | 删除案件与 AI 空壳模块 | 根 pom、网关路由、docs/specs/前端连带清理 | Maven 模块数 3；`grep claim-case-service|claim-ai-service` 命中 0；无指向不存在服务的路由 | #53（依赖 T18） |
| T20 | foundation 三子模块合一 | 单模块 jar + `AutoConfiguration.imports` | 零类 artifact 不存在；8 处 pom 引用清理；头名常量仍为两端共用来源 | #52 |
| T21 | 假件剥离为独立模块 | `backend/claim-local-mock` 模块与启动说明 | 生产 jar 内假件类计数 0；本地仍能跑材料失败与承保超时链路 | #54 |

## 每轮统一收口

1. `mvn -B -pl backend/claim-application-service -am clean package` 构建通过。
2. 重启运行栈（应用与网关），日志无新增 ERROR。
3. `scripts/smoke/golden-path.sh` 全绿；受影响场景（材料失败→重新申请、历史数据还原、未支持租户、同号并发）逐项比对。
4. 架构文档与受影响 Spec 同步；提交信息中文，未获授权不推送。

## 已回退任务

- 单元测试相关任务一律不安排：2026-10-29 决定功能定稿前不保留单元测试，验证由 T17 的脚本承担。
