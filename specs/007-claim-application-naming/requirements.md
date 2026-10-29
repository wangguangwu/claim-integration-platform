# 007 理赔申请命名全链路收敛需求

## 功能目标

- 让"理赔申请"这个业务对象在类型名、Java 标识符、数据库列名与对外字段名上统一使用 `claimApplication` / `ClaimApplication` / `claim_application_no`，消除与 DDD 应用层概念、Spring `application` 词汇的歧义。
- 只改名、不改行为：状态机、幂等键、事务边界、异步链路语义逐字段保持不变。
- 改名后全项目可用一条 grep 判定是否还有裸 `Application` 命名残留，命名规则可被后续开发机械遵守。

## 决策记录

- 2026-10-29 服务负责人裁定按"全链路到底"执行：类型名、Java 标识符、数据库列名与索引名、HTTP 响应字段、MQ 信封字段、回调入参字段一并改名，不等功能定稿。
- 本次裁定解除的基线约束见 design §一 冲突表，其中包含 002 的"信封字段名不允许修改"与 006 的"既有列与载荷字段名不变"两条边界。
- 三类名字明确排除在外（服务身份名、运行时数据键、租户私有入参），理由见"非本次范围"，其中运行时数据键的排除依据是改 Redis 键等于把当日申请编号流水计数清零。

## 业务规则

- 凡指代理赔申请业务对象的名字必须带 `claim` 限定：类型为 `ClaimApplication*`，标识符为 `claimApplicationNo`，列为 `claim_application_no`，对外字段为 `claimApplicationNo`。
- 指代服务身份的名字维持 `claim-application-service` 体系：Maven artifact、`spring.application.name`、Java 基础包 `com.wangguangwu.claim.application`、REST 前缀 `/api/application/v1`、MQ 主题与消费组、Redis 键命名空间。
- 指代业务对象的错误码字面量随对象改名：`APPLICATION_INTAKE_*` → `CLAIM_APPLICATION_INTAKE_*`，同一错误码不得存在新旧两个值。
- 租户私有报文字段名由租户定义，平台不得单方面更改：`requestNo`、`applicant`、`insured`、`accident`、`payee`、`attachments`、`type`、`sourceUrl` 全部维持原样。
- 数据库改名必须以列改名实现，不得新建列再迁数据；历史行在新列名下必须可读，语义、类型、索引覆盖关系不变。
- Redis 键、数据库名、MQ 主题名、申请编号前缀 `A` 不改：这些是已产生数据的运行时键，改名代价是数据迁移，不带来命名收益。
- 文档与本 Spec 映射表必须逐条一致；对外集成契约文档是租户与承保、审核系统的对接事实源，改名后必须同步并在版本号处标注该字段为 breaking 变更。

## 正常场景

1. 经网关提交租户A申请，响应结构与此前一致，仅业务编号字段名为 `data.claimApplicationNo`。
2. 同一租户外部申请号重复提交，返回同一个 `claimApplicationNo`，不产生重复行。
3. 异步链路经材料处理、自动承保、完成通知抵达 `WAIT_AUDIT`，Outbox 事件投递成功，信封字段名为 `claimApplicationNo`。
4. 承保完成回调、审核签收回调按新字段名提交时被正常受理。
5. 历史数据行（改名前落库的申请、材料、轨迹、事件）在新代码下完整还原。

## 异常场景

| 场景 | 期望处理 |
|---|---|
| 回调仍按旧字段名 `applicationNo` 提交 | 返回本域业务错误码"缺少理赔申请编号"，不得静默当成新申请 |
| 租户报文缺必填人员节点 | 与改名前同错误码与同 HTTP 状态，仅字段名与码名变化 |
| 状态机违例（对非受理失败申请重新提交） | 语义不变，错误码取新名 |
| 旧键名在 Redis 或 MQ 中被读到 | 不存在该情况：Redis 键与 MQ 主题名本次不改 |

## 边界条件

- 改名不得改变任何列的类型、可空性、默认值、生成列表达式语义与索引覆盖列顺序。
- 交互轨迹与 Outbox 的既有行必须在改名后仍可配对，孤儿 STARTED 数量保持 0。
- 假件与生产代码两侧的信封、回调字段名必须同步改，否则本地黄金路径会因键名不一致而假失败。
- 已发布的在途消息按新键名解析：本服务为唯一生产方与消费方，改名窗口内不得残留在途旧键消息。

## 本次范围

- 25 个 `Application*` 类型与启动类的改名。
- Java 标识符 `applicationNo`、`externalApplicationNo`、`applicationAttempt` 与局部变量 `application` 的改名。
- 7 张表 15 处列名、6 处索引名与 `@TableField` 映射的改名，含本地数据卷的一次性列改名迁移。
- 对外字段改名：HTTP 响应业务编号键、MQ 信封字段、事件载荷字符串键、三类回调入参键、材料补充与撤销的路径段与路径变量。
- 错误码枚举常量字面量改名。
- `docs/integration`、`docs/testing`、`docs/architecture`、`scripts/jmeter` 压测断言与 specs 002/003/004/006 中被改名触及的条目同步。

## 非本次范围

- DDD 分层包名（`interfaces`/`usecase`/`domain`/`infrastructure`/`shared`）与 Spring 配置文件名 `application.yml`、`application-local.yml`。
- 服务身份名体系：artifact、`spring.application.name`、Java 基础包、REST 前缀 `/api/application/v1`、网关路由里的服务段。
- 运行时数据键：Redis 键前缀、数据库名 `claim_application_db`、MQ 主题与消费组名、申请编号 `A` 前缀。
- 租户私有入参字段名。
- 新增业务功能、调整状态机语义、恢复单元测试。

## 验收标准

- `grep -rEw` 命中数为 0：backend 内 `ApplicationStatus|ApplicationRepository|ApplicationExistenceQuery|ApplicationIntakeLock|ApplicationNoGenerator|ApplicationIntakeException|ApplicationIntakeErrorCode|ApplicationIntakeController|ApplicationIntakeExceptionHandler|ApplicationIntakeResponse|ApplicationResponseRenderer|ApplicationEventType|ApplicationEventEnvelope|ApplicationAsyncException|ApplicationAsyncErrorCode|ApplicationCompensationScheduler|ApplicationCompensationUseCase|ApplicationCompensationTransactionService|ApplicationCompensationProperties|ApplicationCompensationPolicy|ApplicationLifecycleRecordFactory|ApplicationExistenceMismatchException|ApplicationDO|ApplicationDOMapper|ApplicationDataConverter|ApplicationServiceApplication`。
- backend 内 `applicationNo`、`externalApplicationNo`、`applicationAttempt` 三个标识符与字符串键的命中数为 0；错误码字面量 `APPLICATION_INTAKE_` 命中数为 0。
- DDL、`@TableName`/`@TableField` 与运行库 `information_schema` 中 `application_no`、`external_application_no`、`application_attempt`、`open_application_no` 列命中数为 0，新列名索引与原覆盖列顺序一致。
- `mvn -B clean package` 全反应堆通过；应用与网关重启日志无新增 ERROR。
- 黄金路径六场景（无保单、有保单、租户B、材料失败→重新申请、同号幂等、畸形 JSON）逐字段比对，除业务编号键名与错误码字面量外全部一致。
- 改名前落库的历史申请行在新代码下可读，交互轨迹 STARTED 与终态行数相等、孤儿组为 0，Outbox 事件仍可被投递与消费。
- 集成契约文档与 002/003/004/006 中被触及的字段名、列名、路径与本 Spec 映射表逐条一致。
