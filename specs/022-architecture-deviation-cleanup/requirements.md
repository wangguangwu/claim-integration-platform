# 022 架构偏差清零

## 背景与目标

021 已把 `claim-application-service` 的分层基线统一为 `domain / usecase / interfaces / infrastructure`，但 [标准 DDD 架构说明](../../docs/architecture/ddd-standard-architecture.md) 的可执行自检仍有 5 项在 021 之前就已存在的偏差：

1. `IssueDomainService` 带 `@Service`，是 `domain` 内唯一的框架注解。
2. `interfaces/issue` 的两个控制器引用 `interfaces/intake/web/render/ClaimApplicationIssueResponseRenderer`，渲染器归属错位。
3. `ClaimApplicationIssueMapper`、`ClaimCaseRelationMapper` 未按既有 `*DOMapper` 口径命名。
4. 自检第 15 项把"有装配逻辑的构造器"误算成样板构造器，文档与现实的期望值不一致。
5. 自检第 16 项把 Javadoc 文本里的 `PersonInfo` 误判为代码引用。

本次目标是把这 5 项清零，让架构自检从"带已知偏差的基线"变成"可全绿执行的基线"。

## 清理规则

1. `domain` 内不得出现任何框架注解；领域服务由 `infrastructure` 的配置类装配为 Spring Bean，领域类本身保持零框架依赖。
2. 入站响应渲染器必须与它服务的业务域同包归属；`interfaces/<域>` 不得引用其他业务域的 `interfaces` 包。
3. `infrastructure` 中继承 MyBatis-Plus `BaseMapper` 的接口统一以 `DOMapper` 结尾，与既有 `*DOMapper` 命名一致。
4. 自检第 15 项只约束"纯赋值依赖注入构造器"；带 `super(...)`、配置换算、信号量初始化、休眠或失败策略等装配逻辑的构造器必须逐类列入白名单，不得为了凑数改成 `@RequiredArgsConstructor`。
5. 自检第 16 项只检查代码中的类型引用，注释与 Javadoc 中的类名不算违规。
6. 本次只做归属、命名与自检口径调整，不改变任何业务行为、事务边界、消息语义、HTTP 响应、数据库表与 Redis 键。

## 正常场景

- 应用启动后 `IssueDomainService` 仍按原类型注入到问题件下发与关闭用例，问题件下发、关闭、查询行为不变。
- 问题件响应仍由同一个渲染器输出，JSON 字段与 HTTP 状态码不变。
- 两个 Mapper 重命名后，问题件与案件关联的查询、插入、幂等与唯一约束行为不变。
- 全量构建、ArchUnit 规则与 16 项架构自检通过；第 8 项为 6、第 8b 项为 2，其余为 0。
- 运行栈上问题件下发、查询、关闭，以及一次完整签收与审核签收链路行为不变。

## 异常场景与边界条件

- 移除 `@Service` 后若配置类未被扫描，应用启动失败或用例注入不到 Bean；必须通过启动验证，不能只靠编译通过。
- 渲染器移动后若仍有旧包引用，编译失败；必须全仓清零。
- Mapper 重命名后若存在按 Bean 名注入或 XML 映射，启动失败；必须先确认只有按类型构造器注入，且无 XML。
- 自检第 15 项新增配置类的 `@Bean` 方法不得被误算为构造器；执行后仍应为 15。
- 自检第 16 项排除注释后，若代码中真的出现 `PersonInfo` 装配入口，结果必须大于 0。
- 新增配置类会使生产类数、文件数与目录数各增加 1，文档统计必须同步，不能沿用 021 的数字。

## 本次范围

- `IssueDomainService` 去框架化，新增 `infrastructure/issue/config/IssueDomainServiceConfiguration`。
- `ClaimApplicationIssueResponseRenderer` 从 `interfaces/intake/web/render` 移到 `interfaces/issue/web/render`。
- `ClaimApplicationIssueMapper`、`ClaimCaseRelationMapper` 重命名为 `*DOMapper`。
- `docs/architecture/ddd-standard-architecture.md` 的自检口径、统计数字与已知偏差段落。
- `docs/architecture/claim-application-architecture.md` 的渲染器路径说明。
- 全量构建、ArchUnit、16 项自检与运行栈回归。

## 非本次范围

- 不修改任何 HTTP 路径、请求/响应字段、错误码与状态机。
- 不修改 MQ Topic、消费组、事件类型、Outbox 与补偿逻辑。
- 不修改数据库表、索引、唯一约束、Redis 键与 MyBatis SQL。
- 不重排 `interfaces`、`infrastructure` 的其他包，不重命名其他类。
- 不把领域服务移到 `usecase`，不改成由用例直接 `new`。
- 不新增业务功能、不新增框架依赖、不重写历史 spec。

## 验收标准

1. `domain` 内 `import org.springframework`、`com.baomidou` 等框架依赖为 0，`IssueDomainService` 无 `@Service`。
2. 应用能启动，问题件下发、关闭、查询用例正常注入 `IssueDomainService`，行为与 022 前一致。
3. `interfaces/issue` 不再引用 `interfaces/intake`，渲染器位于 `interfaces/issue/web/render`，响应内容不变。
4. `infrastructure` 下所有 MyBatis-Plus Mapper 均以 `DOMapper` 结尾，问题件与案件关联读写正常。
5. 16 项自检全部达到预期：第 8 项 6、第 8b 项 2、第 15 项 15，其余 0；第 16 项排除 Javadoc 后为 0。
6. 全量 Maven 构建、既有测试与 7 条 ArchUnit 规则通过。
7. 文档统计与代码一致：生产类 186、文件 196、目录 108，`infrastructure/issue` 为 6 个类。
