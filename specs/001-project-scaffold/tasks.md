# 001 工程骨架任务

## 已完成

- [x] 根 POM：聚合 8 个模块，继承 Boot 3.5.16，Java 21，统一插件管理
- [x] `claim-bom`：Spring Cloud 2025.0.3、Spring Cloud Alibaba 2025.0.0.0、Spring AI 1.1.8、MyBatis-Plus 3.5.18、XXL-JOB 3.5.0
- [x] `claim-foundation` 及其 `common` / `tenant` / `observability` 三个子模块
- [x] `claim-gateway` POM 与包目录
- [x] `claim-application-service` POM 与包目录
- [x] `claim-case-service` POM 与包目录
- [x] `claim-ai-service` POM 与包目录
- [x] 前端 `claim-web` 目录与 `package.json`
- [x] `deploy` 占位
- [x] 根 `.gitignore`
- [x] `mvn -DskipTests package` 全量通过，7 个模块产出 jar
- [x] 总架构文档同步：specs 目录编号化、Case 服务包名改为 `cases`

## 待办（仍属骨架范畴）

- [ ] 各服务启动类与 `application.yml`：本地 profile 关闭 Nacos 注册与配置中心
- [ ] 公共 starter 装配：`claim-foundation-*` 提供自动配置，服务只需引依赖
- [ ] 网关路由配置与 Tenant 识别过滤器骨架
- [ ] 前端 `vite.config.ts`、`tsconfig.json`、入口文件，跑通 `npm run build`
- [ ] CI 构建脚本与统一格式检查

## 后续 spec 划分

| 编号 | 主题 | 何时开始 |
| ---- | ---------------------- | ------------------------ |
| 002 | 平台底座（配置、租户上下文、可观测） | 服务能启动之后 |
| 003 | Application 业务域 | 底座就绪后 |
| 004 | Case 业务域 | Application 签收链路可用后 |
| 005 | AI 异常诊断 | 前两个域有真实异常数据后 |
| 006 | 前端页面 | 各域接口稳定后 |

编号只作规划，实际以开工时的 spec 为准。
