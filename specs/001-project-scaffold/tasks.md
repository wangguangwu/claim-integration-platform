# 001 工程骨架任务

## 已完成

### 构建

- [x] 根 POM：聚合 backend 下 5 个模块，继承 Boot 3.5.16，Java 21
- [x] 依赖版本统一在根 POM 的 `dependencyManagement`，插件统一在 `pluginManagement`
- [x] 各服务 POM 启用 `spring-boot-maven-plugin` 并声明 `mainClass`，产出可执行包
- [x] `mvn -DskipTests package` 全量通过，产出 7 个可执行 jar

### 后端

- [x] `claim-foundation` 及其 `common` / `tenant` / `observability` 三个子模块
- [x] `claim-foundation-tenant` 自动配置：Tenant 上下文与 `OncePerRequestFilter` 注册，仅 SERVLET 环境生效
- [x] `claim-foundation-observability` 提供 Trace 头常量
- [x] `claim-gateway` 启动类、路由配置、TraceId 过滤器、Tenant 识别过滤器
- [x] `claim-application-service` 启动类与 `application.yml`
- [x] `claim-case-service` 启动类与 `application.yml`
- [x] `claim-ai-service` 启动类与 `application.yml`
- [x] 本地 profile 关闭 Nacos 注册与配置中心，网关同时关闭 Sentinel

### 前端

- [x] `claim-web` 构建链：`vite.config.ts`、`tsconfig.json`、入口与路由
- [x] 统一布局与 5 个占位页面
- [x] `npm run build` 通过

### 其他

- [x] `deploy` 占位
- [x] 根 `.gitignore`
- [x] 总架构文档同步：specs 目录编号化、Case 服务包名改为 `cases`

## 验证记录

- 网关在本地 profile 下健康检查返回 200
- 缺少租户头返回 400 与 `TENANT_HEADER_MISSING`
- 携带租户头时三条路由均命中（下游未启动返回 503）
- application-service / case-service 在 MySQL、Redis 未配置时健康检查为 DOWN，属本地环境预期
- 运行需 JDK 21 及以上，PATH 上的 `java` 若为 17 会报 `UnsupportedClassVersionError`
