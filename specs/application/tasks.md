# Application 实施任务

## 已完成（本次骨架）

- [x] `backend/claim-application-service/pom.xml`：Web、Validation、Redis、MyBatis-Plus、MySQL、Nacos、RocketMQ、XXL-JOB、foundation 三件套
- [x] 包结构：`interfaces/{rest,dto,mq}`、`usecase/{sign,accept,underwrite,deliver,query}`、`domain/{model,valueobject,event,service,repository,statemachine}`、`infrastructure/{persistence/{mapper,po,converter},adapter,file,mq}`
- [x] 根 POM 统一管理版本，服务模块待引入 `spring-boot-maven-plugin` 产出可执行包

## 阶段一：基础底座

- [ ] `claim-foundation-common`：统一返回模型、业务异常基类、错误码枚举、金额与时间工具
- [ ] `claim-foundation-tenant`：`TenantContext`（可传递）、租户配置模型、`TenantProtocolKey`
- [ ] `claim-foundation-observability`：TraceId 注入过滤器、操作日志与审计日志切面
- [ ] 服务启动类与 `application.yml`，本地 profile 关闭 Nacos 注册与配置中心
- [ ] MyBatis-Plus 配置：分页插件、乐观锁插件、自动填充 createdAt/updatedAt

## 阶段二：领域模型

- [ ] `domain/valueobject`：`SignNo`、`TenantProtocolKey`、`ExternalRequestNo`、`ApplicantInfo`、`InsuredInfo`、`Money`
- [ ] `domain/model`：`Application` 聚合根与 `ApplicationStatus` 枚举
- [ ] `domain/model`：`ApplicationMaterial`、`UnderwritingResult`、`DeliveryTask`、`TraceRecord`、`ExceptionRecord`
- [ ] `domain/statemachine`：迁移表 + 迁移校验，覆盖 design 中的状态机表
- [ ] `domain/event`：8 个领域事件
- [ ] `domain/service`：`SignNoGenerator`、`IdempotencyGuard`、`AcceptancePolicy`、`UnderwritingPolicy`、`DeliveryRetryPolicy`
- [ ] `domain/repository`：`ApplicationRepository` 接口
- [ ] 状态机与领域行为单测（覆盖全部合法迁移与终态拒绝）

## 阶段三：持久化

- [ ] `infrastructure/persistence/po`：6 张表的 PO
- [ ] `infrastructure/persistence/mapper`：只继承 `BaseMapper`，不写手写 SQL
- [ ] `infrastructure/persistence/converter`：PO ↔ 聚合转换
- [ ] `ApplicationRepositoryImpl`：唯一键冲突转换为幂等命中
- [ ] 建表脚本落到 `deploy`，库名 `claim_application_db`

## 阶段四：同步签收

- [ ] `interfaces/rest`：签收接口与错误码转换
- [ ] `usecase/sign`：`SignApplicationUseCase`，含幂等、signNo 生成、落库、事件投递
- [ ] `infrastructure/adapter`：`TenantAdapter` 接口、注册表、`AbstractTenantAdapter`
- [ ] 首个租户适配实现（`{tenantCode}/{protocolVersion}/`）与验签解密
- [ ] Redis 幂等键与数据库唯一键兜底联调

## 阶段五：异步受理

- [ ] `infrastructure/mq`：RocketMQ 生产者与消费者配置、消息体规范、消费幂等表
- [ ] `usecase/accept`：`AcceptApplicationUseCase`，影像逐份处理与失败续跑
- [ ] `infrastructure/file`：`FileStorage` 接口与本地实现，业务侧不感知存储产品
- [ ] 受理失败异常记录与退避重试

## 阶段六：承保与交付

- [ ] `usecase/underwrite`：`UnderwriteApplicationUseCase`，规则判定与结论落库
- [ ] 交付适配器：加解密、签名、报文转换、返回码映射
- [ ] `usecase/deliver`：交付、重试调度、签收完成
- [ ] `claim-application-delivered` 事件投递给 Case 域

## 阶段七：撤销、补材与查询

- [ ] `usecase/query`：详情、列表、轨迹、异常查询
- [ ] `usecase/sign`：撤销与补材用例
- [ ] 列表筛选：租户、状态、时间范围、外部单号
- [ ] 详情聚合关联 Case（通过 Case 服务接口查询，不跨库）

## 阶段八：补偿与运维

- [ ] XXL-JOB 执行器接入与三个补偿任务：受理超时、交付超时、待补材超时
- [ ] 关键节点审计日志
- [ ] 核心链路集成测试：签收 → 受理 → 承保 → 交付 → 签收完成
- [ ] 幂等并发测试：同外部单号并发签收只产生一条记录
