# 001 工程骨架设计

## 1. 模块划分

| 模块 | 职责 | 主要依赖 |
| ------------------------------ | -------------------------------------- | ----------------------------------- |
| `claim-foundation-common` | 返回模型、异常、枚举、基础工具 | Jackson、Validation |
| `claim-foundation-tenant` | Tenant 上下文、租户配置、协议版本 | common |
| `claim-foundation-observability` | Trace、Metric、操作日志、审计日志 | common、Actuator、Micrometer Tracing |
| `claim-gateway` | 认证、Tenant 识别、路由、流量治理、TraceId | foundation 三件套、Gateway WebFlux、Nacos、Sentinel、LoadBalancer |
| `claim-application-service` | Application 业务域 | foundation 三件套、Web、Redis、MyBatis-Plus、MySQL、Nacos、RocketMQ、XXL-JOB |
| `claim-case-service` | Case 业务域 | 同上 |
| `claim-ai-service` | AI 异常诊断 | foundation 三件套、Web、Spring AI、Nacos、OpenFeign、LoadBalancer |

网关只做入口层能力，不做租户协议转换与业务规则。服务之间不直连数据库。

## 2. 依赖图

```
claim-gateway ──┐
claim-application-service ──┼──> claim-foundation-common
claim-case-service ─────────┤    claim-foundation-tenant ──> common
claim-ai-service ───────────┘    claim-foundation-observability ──> common
```

约束：

- foundation 不反向依赖任何服务模块。
- 服务模块之间没有 Maven 依赖，协作走消息或 HTTP 服务接口。
- 网关不依赖业务服务模块，只做转发。
- 第三方版本全部在根 POM 的 `dependencyManagement`，不单独建 BOM 模块。

## 3. POM 分层

| 层 | 作用 |
| -------------- | -------------------------------------------------------------------------------- |
| 根 POM | 聚合全部模块，继承 `spring-boot-starter-parent`，固定 Java 21，导入 Spring Cloud、SCA、Spring AI 三个 BOM，管理 MyBatis-Plus、XXL-JOB 与内部模块版本 |
| 服务模块 POM | 只声明自己需要的 starter，不写版本号 |

根 POM 本身就是 BOM：它已是所有模块的 parent，`dependencyManagement` 中的版本被全部子模块继承，子模块无需再 import 任何东西。只有需要给平台外项目提供版本清单时，才值得单独抽一个 BOM 模块。

`spring-boot-maven-plugin` 统一在根 POM 的 `pluginManagement` 中配置，服务模块引入时只指定 `mainClass`。

## 4. 后端包结构

统一分层，四个一级包：

```
com.wangguangwu.claim.<service>
├─ interfaces          接入层：REST、DTO、MQ 监听、回调入口
├─ usecase             用例层：事务边界、跨域协作
├─ domain              领域层：聚合、值对象、领域服务、状态机、Repository 接口
└─ infrastructure      基础设施层：持久化、适配器、文件、MQ、Redis
```

分层规则：

- 依赖方向 `interfaces → usecase → domain ← infrastructure`，domain 不依赖 Spring Web、MyBatis-Plus、Redis。
- 所有外部系统差异收敛在 `infrastructure` 下的 adapter。
- 具体聚合、表结构和状态机不在本 spec 定义，由对应业务域 spec 决定。

各服务已落地的二级目录：

```
claim-application-service
├─ interfaces/{rest, dto, mq}
├─ usecase/{sign, accept, underwrite, deliver, query}
├─ domain/{model, valueobject, event, service, repository, statemachine}
└─ infrastructure/{persistence/{mapper, po, converter}, adapter, file, mq}

claim-case-service
├─ interfaces/{rest, dto, mq}
├─ usecase/{intake, orchestrate, callback, query}
├─ domain/{model, valueobject, stage, event, service, repository, statemachine}
└─ infrastructure/{persistence/{mapper, po, converter}, delivery/{adapter, codec, client}, mq, redis}

claim-ai-service
├─ agent         诊断流程、假设生成、Tool 调度
├─ tool          只读查询实时业务数据
├─ evidence      证据标准化与关联
├─ knowledge     协议、错误码、历史故障检索
├─ policy        动作授权
└─ remediation   受控处置

claim-gateway
└─ {auth, tenant, filter, route, config}

claim-foundation
├─ common/{model, exception, enums, util}
├─ tenant/{context, config, protocol}
└─ observability/{trace, metric, log, audit}
```

## 5. 租户适配目录约定

Application 接入适配与 Case 交付适配都按 `{tenantCode}/{protocolVersion}` 建子包，公共逻辑上提到 `Abstract*Adapter`：

```
infrastructure/adapter/{tenantCode}/{protocolVersion}/
infrastructure/delivery/adapter/{tenantCode}/{protocolVersion}/
```

具体租户目录在接入该租户时创建。骨架不预置 tenant-a / tenant-b 之类的示例目录，避免占位目录被误当成真实实现。

## 6. 前端结构

```
frontend/claim-web
├─ public
├─ src
│  ├─ views/{portal, application, case, diagnosis, tenant}
│  ├─ layouts
│  ├─ components
│  ├─ api
│  ├─ stores
│  ├─ router
│  └─ utils
└─ package.json
```

版本对齐根文档：Vue 3.5.44、TypeScript 5.9.3、Vite 6.4.5、Element Plus 2.14.7。本次只落目录与 `package.json`，未安装依赖、未验证构建。

## 7. 构建与版本管理

- 技术版本来源：根 POM（Java、Boot、第三方 BOM 与单构件）、前端 `package.json`。
- 新增第三方依赖：版本写在根 POM，服务模块只引坐标。
- 构建命令：`mvn -DskipTests package`，在根目录执行，模块顺序由依赖关系推算。
- 每次调整模块或依赖，同步更新本文档的模块表与目录树。

## 8. 设计取舍

- **先骨架再实现**：先把依赖方向和目录钉死，后续每个域只填自己的内容，避免边写边改结构。
- **版本只放根 POM**：子模块天然继承，不需要额外 import；多一层 BOM 模块反而带来版本号两处维护。
- **不预置假租户目录**：占位目录会误导后续实现，约定写在文档里即可。
- **AI 服务不持有业务库权限**：结构上就用 OpenFeign 调业务服务，从部署层面杜绝越权写库。
