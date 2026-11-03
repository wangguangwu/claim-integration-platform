# 026 包结构标准对齐任务

## T1 `interfaces` 按入站角色分包

- 产物：`interfaces/{web,consumer,dto,scheduler}` 目录及迁移后的类。
- 完成条件：控制器、响应渲染、统一异常处理在 `web`；MQ 消费者与解析器在 `consumer`；请求响应记录在 `dto`；`@Scheduled` 入口在 `scheduler`；无旧 `interfaces/<域>` 目录。

## T2 `application` 按应用角色分包

- 产物：`application/{command,query,port,process,exception}` 目录及迁移后的类。
- 完成条件：命令在 `command`；查询用例与读模型在 `query`；端口与端口模型在 `port`；用例、事务服务、结果与记录装配在 `process`；`ApplicationException` 与异步错误码在 `exception`。

## T3 `domain` 收敛为申请聚合

- 产物：`domain/claim`、`domain/claim/port`、`domain/claim/exception`。
- 完成条件：聚合根、材料、问题件、人员角色与状态枚举扁平位于 `domain/claim`；聚合仓储在 `port`；领域错误码在 `exception`；领域层零框架依赖。

## T4 `infrastructure` 按技术角色分包

- 产物：`infrastructure/{persistence,adapter,acl,mq,config}`。
- 完成条件：DO、ORM Mapper、Converter、仓储与读模型在 `persistence`；Redis、Redisson、发号、锁、熔断与批次号在 `adapter`；审核、承保、文件等外部系统的日志实现在 `acl`；Outbox 发布在 `mq`；配置属性与框架配置在 `config`。

## T5 `shared` 收敛为跨层契约

- 产物：`shared/{exception,message,util}`。
- 完成条件：异常基类与错误码接口在 `exception`；消息契约在 `message`；摘要工具在 `util`；删除旧 `shared/contract` 与 `support`。

## T6 测试与架构规则

- 产物：迁移后的测试类与重写后的 `ClaimApplicationArchitectureTest`。
- 完成条件：测试类与新包同名；架构测试按新包名校验分层、端口契约、查询只读、聚合封装、外部系统实现落位与 restore 限制，全部通过。

## T7 包说明

- 产物：所有业务包的 `package-info.java`。
- 完成条件：每个包说明职责与依赖边界；引用包名与实际一致。

## T8 构建、文档与运行栈验证

- 产物：编译与测试结果、同步后的架构文档、运行栈冒烟记录。
- 完成条件：JDK 21 下主源码与测试编译通过；全量测试通过；`docs/architecture` 路径与新结构一致；服务与网关重启健康检查通过，签收与审核签收黄金路径冒烟通过。
