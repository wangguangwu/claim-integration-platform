# Case 实施任务

## 已完成（本次骨架）

- [x] `backend/claim-case-service/pom.xml`：Web、Validation、Redis、MyBatis-Plus、MySQL、Nacos、RocketMQ、XXL-JOB、foundation 三件套
- [x] 包结构：`interfaces/{rest,dto,mq}`、`usecase/{intake,orchestrate,callback,query}`、`domain/{model,valueobject,stage,event,service,repository,statemachine}`、`infrastructure/{persistence/{mapper,po,converter},delivery/{adapter,codec,client},mq,redis}`
- [x] 包名采用 `com.wangguangwu.claim.cases`，待总架构文档同步修订

## 阶段一：基础底座

- [ ] 服务启动类与 `application.yml`，本地 profile 关闭 Nacos 注册与配置中心
- [ ] MyBatis-Plus 配置：分页、乐观锁、自动填充
- [ ] Redis 分布式锁组件与等待回调索引（ZSet：score = 超时时间）
- [ ] 外部调用 HTTP 客户端封装：连接 / 读取超时、重试、traceId 透传

## 阶段二：领域模型

- [ ] `domain/valueobject`：`CaseNo`、`SourceSignNo`、`StageCode`、`StageResult`、`CallbackKey`
- [ ] `domain/model`：`ClaimCase` 聚合根、`CaseStatus`
- [ ] `domain/model`：`StageRecord`、`DeliveryCall`、`CallbackRecord`、`RetryRecord`、`ExceptionRecord`
- [ ] `domain/statemachine`：案件状态迁移表与校验
- [ ] `domain/stage`：`StageHandler`、`StageContext`、`StageHandlerRegistry` 与阶段注册校验
- [ ] `domain/event`：案件领域事件
- [ ] `domain/service`：重试策略、回调过期判定
- [ ] `domain/repository`：`ClaimCaseRepository`
- [ ] 状态机与阶段编排单测

## 阶段三：持久化

- [ ] 6 张表的 PO、Mapper（只继承 `BaseMapper`）、Converter
- [ ] `ClaimCaseRepositoryImpl`
- [ ] 建表脚本落到 `deploy`，库名 `claim_case_db`

## 阶段四：建案与规则判断

- [ ] 消费 `claim-application-delivered` 消息，消息幂等
- [ ] `usecase/intake`：建案用例、caseNo 生成
- [ ] 推送规则判断：阶段链产出、无需推送直接终止
- [ ] 阶段链与 Handler 注册表一致性校验

## 阶段五：阶段执行

- [ ] `usecase/orchestrate`：阶段推进编排器（同步继续、异步暂停）
- [ ] `domain/stage`：`ReportHandler`、`ImageHandler`、`SettlementHandler` 骨架
- [ ] `infrastructure/delivery`：`TenantDeliveryAdapter` 接口、注册表、`AbstractDeliveryAdapter`
- [ ] `infrastructure/delivery/codec`：加解密与签名
- [ ] `infrastructure/delivery/client`：外部接口调用与返回码映射
- [ ] 首个租户交付适配实现（`{tenantCode}/{protocolVersion}/`）

## 阶段六：异步回调

- [ ] `interfaces/rest`：回调入口，原始报文留存
- [ ] `usecase/callback`：回调解析、归属校验、幂等生效、推进阶段链
- [ ] 过期回调与乱序回调的处理策略
- [ ] Redis 等待索引的写入与移除

## 阶段七：重试与恢复

- [ ] `usecase/orchestrate`：阶段失败重试调度
- [ ] 回调超时任务：回执反查 → 重投 → 转人工
- [ ] XXL-JOB 执行器接入与停滞案件扫描任务
- [ ] 人工重新提交、人工恢复接口

## 阶段八：交付验证

- [ ] 同步阶段链路集成测试：报案 → 影像一站式推进
- [ ] 异步阶段链路集成测试：提交 → 等待 → 回调 → 推进
- [ ] 回调幂等与并发测试
- [ ] 回调超时恢复测试
- [ ] 一申请多案件的建案验证
