# 001 工程骨架设计

## 1. 改动模块

| 模块 | 职责 | 主要依赖 |
| ------------------------------ | -------------------------------------- | ----------------------------------- |
| `claim-foundation` | foundation 聚合模块 | 无 |
| `claim-foundation-common` | 返回模型、异常、枚举、基础工具 | Jackson、Validation |
| `claim-foundation-tenant` | Tenant 上下文、租户配置、协议版本 | common、Servlet API(provided) |
| `claim-foundation-observability` | Trace、Metric、操作日志、审计日志 | common、Actuator、Micrometer Tracing |
| `claim-gateway` | 认证、Tenant 识别、路由、流量治理、TraceId | foundation 三件套、Gateway WebFlux、Nacos、Sentinel、LoadBalancer |
| `claim-application-service` | Application 业务域 | foundation 三件套、Web、Redis、MyBatis-Plus、MySQL、Nacos、RocketMQ、XXL-JOB |
| `claim-case-service` | Case 业务域 | 同上 |
| `claim-ai-service` | AI 异常诊断 | foundation 三件套、Web、Spring AI、Nacos、OpenFeign、LoadBalancer |

网关只做入口层能力，不做租户协议转换与业务规则。服务之间不直连数据库。

## 2. 核心链路

请求链路（本 spec 落地的部分到过滤器为止）：

```
外部请求
  → claim-gateway: TraceIdGlobalFilter (生成/透传 X-Trace-Id)
  → claim-gateway: TenantGlobalFilter (校验租户头，缺失即 400)
  → claim-gateway: 路由匹配 /api/{application|case|diagnosis}/**
  → 业务服务: TenantContextFilter (请求头 → TenantContext)
  → 业务服务: usecase 及后续流程（本 spec 不实现）
```

启动链路：

```
SpringApplication
  → 读取 META-INF/spring/...AutoConfiguration.imports
  → TenantAutoConfiguration（仅 SERVLET 环境，响应式网关自动跳过）
  → 注册 TenantContextFilter 到 Servlet 容器
```

构建链路：

```
根 POM(聚合 + 版本) → reactor 按依赖排序 → 各模块 jar
  → 服务模块 spring-boot-maven-plugin:repackage → 可执行 jar
```

## 3. 主要类职责

| 类 | 职责 |
| ------------------------------- | -------------------------------------------------------------------- |
| `TenantHeaders` | 租户请求头常量，网关与业务服务共用同一份定义 |
| `TraceHeaders` | TraceId 请求头常量 |
| `TenantInfo` | 租户编码与协议版本的记录类型 |
| `TenantContext` | ThreadLocal 持有当前请求的租户信息，提供写入、读取与清理 |
| `TenantContextFilter` | 把请求头写入 `TenantContext`，在 `finally` 中清理，避免线程复用污染 |
| `TenantAutoConfiguration` | 注册上述过滤器；`SERVLET` 条件保证响应式网关不加载 |
| `TenantGlobalFilter` | 网关侧校验租户头，缺失直接写 400 JSON 并终止链路 |
| `TraceIdGlobalFilter` | 透传或生成 TraceId，写入下游请求头与响应头 |
| `GatewayApplication` 等四个启动类 | 服务入口；`AiServiceApplication` 额外开启 Feign |

## 4. 数据模型与数据库变更

- 不新增表、不新增 DDL，本次不落地任何业务实体。
- `claim-application-service` 与 `claim-case-service` 只在本地 profile 声明 datasource 地址与连接池参数，`initialization-fail-timeout: -1` 保证库不可用时不影响启动。
- 表结构由各业务域 spec 定义。

## 5. 接口设计

网关路由（`spring.cloud.gateway.server.webflux.routes`）：

| 路径 | 目标 |
| ------------------------ | -------------------------------- |
| `/api/application/**` | `lb://claim-application-service` |
| `/api/case/**` | `lb://claim-case-service` |
| `/api/diagnosis/**` | `lb://claim-ai-service` |

请求头约定：

| 请求头 | 必填 | 说明 |
| ---------------------- | ---- | ---------------------------------- |
| `X-Tenant-Code` | 是 | 租户编码，缺失返回 400 |
| `X-Protocol-Version` | 是 | 协议版本，缺失返回 400 |
| `X-Trace-Id` | 否 | 缺失时网关生成，写回响应头 |

错误响应体（网关拒绝）：

```json
{"code":"TENANT_HEADER_MISSING","message":"缺少租户标识或协议版本"}
```

健康检查：四个服务均暴露 `/actuator/health`、`/actuator/info`。

## 6. 基础设施变更

- **Nacos**：各服务引入注册与配置依赖；`local` profile 关闭注册、配置拉取与 import 校验。
- **Sentinel**：网关引入，`local` profile 关闭。
- **RocketMQ**：application / case 引入 stream binder，`local` 指向 `127.0.0.1:9876`，本 spec 不建 topic、不发消息。
- **Redis**：application / case 引入客户端，`local` 指向 `127.0.0.1:6379`，本 spec 不读写。
- **MySQL**：application / case 引入驱动与连接池，`local` 指向本地库，无 DDL。
- **Spring AI**：ai 服务引入 `spring-ai-client-chat`，未配置模型与密钥。
- **前端**：vite dev server 端口 5173，`/api` 代理到 `127.0.0.1:8080`。

## 7. 幂等、事务与异常处理

- 骨架不含业务写操作，不建立事务边界，也不实现业务幂等；两者由各业务域 spec 定义。
- 网关租户校验是无状态纯判断，重复请求结果一致；下游 503 不缓存。
- `TenantContext` 在 `finally` 中清理，异常路径不残留租户信息。
- 过滤器不捕获并吞掉异常，`IOException` / `ServletException` 原样抛出，由容器统一处理。
- 过滤器行为由单元测试覆盖：租户头齐全放行、缺失拒绝且重复请求结果一致、TraceId 生成与透传、上下文绑定与清理。

## 8. 对现有功能的影响

- 本次为新增工程，无存量功能与数据，不存在兼容性处理。
- 后续实现只能沿用本文档确定的分层、包名与租户目录约定，结构调整需先改 spec。

## 9. 依赖图

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

## 10. POM 分层

| 层 | 作用 |
| -------------- | -------------------------------------------------------------------------------- |
| 根 POM | 聚合全部模块，继承 `spring-boot-starter-parent`，固定 Java 21，导入 Spring Cloud、SCA、Spring AI 三个 BOM，管理 MyBatis-Plus、XXL-JOB 与内部模块版本 |
| 服务模块 POM | 只声明自己需要的 starter，不写版本号 |

根 POM 本身就是 BOM：它已是所有模块的 parent，`dependencyManagement` 中的版本被全部子模块继承，子模块无需再 import 任何东西。只有需要给平台外项目提供版本清单时，才值得单独抽一个 BOM 模块。

`spring-boot-maven-plugin` 统一在根 POM 的 `pluginManagement` 中配置，服务模块引入时只指定 `mainClass`。

## 11. 后端包结构

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

已落地的二级目录：

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
├─ tenant/{context, protocol, filter, config}
└─ observability/{trace, metric, log, audit}
```

## 12. 租户适配目录约定

Application 接入适配与 Case 交付适配都按 `{tenantCode}/{protocolVersion}` 建子包，公共逻辑上提到 `Abstract*Adapter`：

```
infrastructure/adapter/{tenantCode}/{protocolVersion}/
infrastructure/delivery/adapter/{tenantCode}/{protocolVersion}/
```

具体租户目录在接入该租户时创建。骨架不预置 tenant-a / tenant-b 之类的示例目录，避免占位目录被误当成真实实现。

## 13. 前端结构

```
frontend/claim-web
├─ index.html
├─ package.json
├─ tsconfig.json
├─ vite.config.ts
├─ public
└─ src
   ├─ main.ts
   ├─ App.vue
   ├─ env.d.ts
   ├─ router/index.ts
   ├─ layouts/DefaultLayout.vue
   ├─ api/request.ts
   ├─ components
   ├─ stores
   ├─ utils
   └─ views/{portal, application, case, diagnosis, tenant}
```

版本对齐根文档：Vue 3.5.44、TypeScript 5.9.3、Vite 6.4.5、Element Plus 2.14.7。已安装依赖并验证 `npm run build`。

Axios 实例封装在 `api/request.ts`，从 localStorage 读取租户信息注入 `X-Tenant-Code`、`X-Protocol-Version`，为后续页面接入预留唯一出口。

## 14. 构建与版本管理

- 技术版本来源：根 POM（Java、Boot、第三方 BOM 与单构件）、前端 `package.json`。
- 新增第三方依赖：版本写在根 POM，服务模块只引坐标。
- 构建命令：`mvn -DskipTests package`，在根目录执行，模块顺序由依赖关系推算。
- 每次调整模块或依赖，同步更新本文档的模块表与目录树。

## 15. 设计取舍

- **先骨架再实现**：先把依赖方向和目录钉死，后续每个域只填自己的内容，避免边写边改结构。
- **版本只放根 POM**：子模块天然继承，不需要额外 import；多一层 BOM 模块反而带来版本号两处维护。
- **不预置假租户目录**：占位目录会误导后续实现，约定写在文档里即可。
- **AI 服务不持有业务库权限**：结构上就用 OpenFeign 调业务服务，从部署层面杜绝越权写库。
- **租户校验放网关**：入口即拒绝，避免无效请求打到业务服务再做一次判断。
