# 001 工程骨架任务

本 spec 不含业务逻辑，因此不新增单元测试；验证以构建、启动与网关行为为主。

## T1 根 POM 与版本管理

- 状态：已完成
- 对应 design：§1、§9、§10、§14
- 产物：根 `pom.xml`（聚合、parent、`dependencyManagement`、`pluginManagement`）
- 完成条件：`mvn -DskipTests package` 从根目录一次通过；子模块不出现 `<version>`；依赖方向与依赖图一致

## T2 foundation 三模块与自动装配

- 状态：已完成
- 对应 design：§3、§11
- 产物：`claim-foundation` 聚合模块及 `common` / `tenant` / `observability` 子模块；`TenantInfo`、`TenantContext`、`TenantHeaders`、`TenantContextFilter`、`TenantAutoConfiguration`、`AutoConfiguration.imports`、`TraceHeaders`
- 完成条件：Servlet 服务启动后过滤器生效并填充租户上下文；网关（WebFlux）启动不加载该自动配置

## T3 网关启动类、路由与过滤器

- 状态：已完成
- 对应 design：§2、§5
- 产物：`GatewayApplication`、`TenantGlobalFilter`、`TraceIdGlobalFilter`、网关 `application.yml`
- 完成条件：`/actuator/health` 返回 200；缺租户头返回 400 且 code 为 `TENANT_HEADER_MISSING`；带租户头时三条路由均命中

## T4 三个业务服务启动类与本地配置

- 状态：已完成
- 对应 design：§4、§6
- 产物：`ApplicationServiceApplication`、`CaseServiceApplication`、`AiServiceApplication` 及各自 `application.yml`
- 完成条件：各服务在 `local` profile 下按约定端口启动；不连接 Nacos；MySQL、Redis 不可用时进程仍能启动

## T5 包结构与占位目录

- 状态：已完成
- 对应 design：§11、§12
- 产物：四个服务的分层包目录；前端 `views` / `layouts` / `api` / `router` 等目录
- 完成条件：目录与 design 目录树逐项一致，不出现未登记目录与假租户目录

## T6 前端骨架与构建

- 状态：已完成
- 对应 design：§13
- 产物：`package.json`、`vite.config.ts`、`tsconfig.json`、`index.html`、`main.ts`、`App.vue`、`router/index.ts`、`DefaultLayout.vue`、`api/request.ts`、5 个占位页
- 完成条件：`npm install && npm run build` 通过并产出 `dist`

## T7 构建与回归验证

- 状态：已完成
- 对应 design：§14
- 产物：构建与启动验证记录（见下）
- 完成条件：7 个可执行 jar 产出；四个服务启动成功；网关三条路由与租户拒绝行为符合 requirements §8

## T8 文档同步

- 状态：已完成
- 对应 design：§8
- 产物：`docs/system-architecture.md` 的 spec 目录结构与 `cases` 包名
- 完成条件：文档与代码目录、包名一致，无残留旧编号或旧包名

## 验证记录

- 网关 `local` profile 下 `/actuator/health` 返回 200
- 缺少租户头返回 400 与 `TENANT_HEADER_MISSING`
- 携带租户头时 `/api/application/**`、`/api/case/**`、`/api/diagnosis/**` 均命中路由，下游未启动返回 503
- `mvn -B -DskipTests package` BUILD SUCCESS，产出 7 个可执行 jar
- `npm run build` 通过，仅提示 Element Plus 全量引入导致的 chunk 体积警告
- application / case 服务健康检查在 MySQL、Redis 未配置时为 DOWN，属本地环境预期
- 运行需 JDK 21 及以上，PATH 上的 `java` 为 17 时报 `UnsupportedClassVersionError`
