# 026 包结构标准对齐

## 1. 功能目标

把 `claim-application-service` 的包目录从"层级优先、层内按业务域平铺"调整为标准 DDD 角色分层模型，使每一层内部按对象角色分包，包名本身即可说明类的职责与允许的依赖方向。

本次只调整目录与包名，不改变任何业务行为、数据库结构、HTTP 路径、消息主题与消息载荷。

## 2. 业务规则

1. 根包仍为 `com.wangguangwu.claim.application`，四层为 `interfaces`、`application`、`domain`、`infrastructure`，另保留 `shared`。
2. 各层内部按角色分包：
   - `interfaces`：`web`、`consumer`、`dto`、`scheduler`。
   - `application`：`command`、`query`、`port`、`process`、`exception`。
   - `domain`：`claim`（聚合根、聚合内实体、值对象、状态枚举）、`claim/port`（聚合仓储）、`claim/exception`（领域错误码）。
   - `infrastructure`：`persistence`、`adapter`（Redis、Redisson、发号、锁、熔断等技术适配）、`acl`（外部系统实现）、`mq`、`config`。
   - `shared`：`exception`、`message`、`util`。
3. 依赖方向为 `interfaces → application → domain ← infrastructure`，`shared` 位于依赖图底部。
4. 领域层只放业务模型与聚合仓储，不依赖 Spring、MyBatis-Plus、Redis、MQ、Jackson 等任何框架。
5. 应用层拥有命令、查询、出站端口、用例编排与应用异常，不依赖 `interfaces` 与 `infrastructure`。
6. 基础设施层实现应用端口与领域仓储，只能依赖 `application/port`、`application/port/model`、`application/query/model`、`domain`、`shared`，不得依赖用例编排、命令、查询用例或 `interfaces`。
7. 接口层只做协议适配，不依赖 `infrastructure`。
8. 外部系统端口实现只放在 `infrastructure/acl`；`infrastructure/adapter` 只放技术适配，不承接外部系统交互。
9. 包结构规则由 `ClaimApplicationArchitectureTest` 在构建阶段校验。

## 3. 正常场景

- 现有签收、材料处理、承保回调、审核交付与签收、问题件、详情查询、Outbox 投递、消费去重与补偿链路在新包结构下编译、启动和运行。
- MQ 消费者、定时任务、控制器、租户协议流水线、读模型查询与仓储实现均能在新包下被 Spring 扫描装配。

## 4. 异常场景

- 任一类出现跨层反向依赖时，ArchUnit 测试失败并阻断构建。
- 包声明与物理目录不一致时，视为结构错误并修正。
- `package-info.java` 缺失于业务包或描述与职责不符时，视为未完成。

## 5. 边界条件

- 只移动类文件、修改 `package` 声明与 `import`，不修改方法体与业务分支。
- 类名、方法名、字段名、注解、Bean 名称、配置键、数据库表与接口路径保持不变。
- 测试类随生产类同步迁移，测试方法内容不变。

## 6. 本次范围

- `backend/claim-application-service/src/main/java` 与 `src/test/java` 下的包结构重排。
- 分层架构测试规则改写。
- `docs/architecture` 下的包结构说明同步。

## 7. 非本次范围

- `claim-gateway`、`claim-foundation-*`、`claim-case-service`、`claim-ai-service` 的目录调整。
- 任何业务规则、状态机、接口契约、消息契约与数据库变更。
- 引入新的框架或依赖。

## 8. 验收标准

1. `mvn -pl backend/claim-application-service -am -DskipTests compile` 通过。
2. `mvn -pl backend/claim-application-service -am test` 全部通过，`ClaimApplicationArchitectureTest` 无违规。
3. 所有业务包均有 `package-info.java`，内容说明职责与依赖边界。
4. 源码中不存在 `usecase`、`support`、`shared/contract` 等旧包引用。
5. 架构、导航文档中的目录路径与新结构一致。
6. 服务与网关重启后健康检查通过，签收与审核签收黄金路径冒烟通过。
