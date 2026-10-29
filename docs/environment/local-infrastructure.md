# 本地基础服务使用说明

## 服务范围

本编排仅供本地开发和集成测试使用，提供 `claim-application-service` 所需的 MySQL、Redis 和 RocketMQ。Nacos 在 local profile 中已关闭，XXL-Job、文件系统、承保系统和审核系统不在本编排范围内。

| 服务 | 宿主机端口 | 用途 |
|---|---:|---|
| MySQL 8.4.13 | `3306` | 理赔申请私有数据库 `claim_application_db` |
| Redis 7.4.11 | `6379` | 缓存、协调与幂等辅助能力 |
| RocketMQ NameServer 5.5.0 | `9876` | RocketMQ 路由发现 |
| RocketMQ Broker 5.5.0 | `10911`、`10909` | 理赔申请异步命令 Topic |
| RocketMQ Dashboard 2.1.0 | `8082` | 查看 NameServer、Broker、Topic 和消费者状态 |

## Apple Silicon（M 系列芯片）

本编排使用的 MySQL、Redis、RocketMQ 和 RocketMQ Dashboard 均提供 `linux/arm64` 镜像。不要在 `deploy/docker-compose.yml` 中添加 `platform: linux/amd64`，否则 Docker Desktop 会显示 AMD64 标识并使用 Rosetta/QEMU 模拟，启动速度和资源效率都会下降。

如果此前拉取过 AMD64 Dashboard 镜像，在仓库根目录执行以下命令后重新启动：

```bash
docker pull --platform linux/arm64 apacherocketmq/rocketmq-dashboard:2.1.0
docker compose --env-file deploy/.env.example -f deploy/docker-compose.yml up -d --force-recreate rocketmq-dashboard
```

本地 RocketMQ 不持久化 Broker 数据，重启容器后 `scripts/docker-start.sh` 会通过 `mqadmin` 重新创建两个理赔申请Topic。这样不需要额外的初始化容器；该取舍仅适用于本地开发，生产环境必须独立规划 Broker 数据持久化与权限管理。

## 启动与停止

在仓库根目录执行：

```bash
./scripts/docker-start.sh
```

脚本优先读取未提交的 `deploy/.env`；不存在时使用 `deploy/.env.example`。如需改端口、账号或密码，可先复制并修改本地环境文件：

```bash
cp deploy/.env.example deploy/.env
```

停止服务并保留数据卷：

```bash
./scripts/docker-stop.sh
```

停止脚本仅移除本项目容器和网络，保留数据卷。

删除本地所有基础服务数据后重新初始化：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down -v
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d
```

第二组命令会删除 `claim-mysql-data` 与 `claim-redis-data`，仅应在不需要保留本地开发数据时执行。

## MySQL 初始化

MySQL 首次创建数据卷时按文件名顺序执行 `deploy/mysql/init/` 下的 SQL：

- `01-create-database.sql`：创建 `claim_application_db` 与 `claim_app` 应用账号。
- `02-application-schema.sql`：创建理赔申请域七张 `t_` 前缀基础表、索引和唯一约束。

服务默认连接信息：

| 配置项 | 默认值 |
|---|---|
| 数据库 | `claim_application_db` |
| 用户名 | `claim_app` |
| 密码 | `claim_app` |
| Root 密码 | `root` |
| Redis 应用账号 | `claim_app` |
| Redis 应用密码 | `claim_redis` |

MySQL 初始化脚本仅在空数据卷时自动执行。已有数据卷需要新增表结构时，应新增版本化 SQL 并显式执行，不得修改已执行脚本来覆盖数据。

## RocketMQ Topic

`scripts/docker-start.sh` 在 Broker 健康后通过 `mqadmin` 幂等创建以下 Topic，每个 Topic 均为 8 个读队列、8 个写队列：

| Topic | 生产事件 | 消费者组 |
|---|---|---|
| `claim_application_material_process_v1` | `APPLICATION_CREATED`、`APPLICATION_REAPPLIED` | `claim-application-material-processor-v1` |
| `claim_application_audit_delivery_v1` | `AUDIT_DELIVERY_REQUESTED` | `claim-application-audit-delivery-v1` |

Broker 已关闭自动建 Topic。若 Topic 初始化失败，重新执行启动脚本可直接查看 `mqadmin` 的错误上下文：

```bash
./scripts/docker-start.sh
```

## 理赔申请本地配置

`claim-application-service` 在 local profile 下默认使用本编排提供的地址。以下环境变量可覆盖默认连接信息：

| 环境变量 | 默认值 |
|---|---|
| `CLAIM_APPLICATION_DB_URL` | `jdbc:mysql://127.0.0.1:3306/claim_application_db?...` |
| `CLAIM_APPLICATION_DB_USERNAME` | `claim_app` |
| `CLAIM_APPLICATION_DB_PASSWORD` | `claim_app` |
| `CLAIM_APPLICATION_REDIS_HOST` | `127.0.0.1` |
| `CLAIM_APPLICATION_REDIS_PORT` | `6379` |
| `CLAIM_APPLICATION_REDIS_USERNAME` | `claim_app` |
| `CLAIM_APPLICATION_REDIS_PASSWORD` | `claim_redis` |
| `CLAIM_APPLICATION_ROCKETMQ_NAME_SERVER` | `127.0.0.1:9876` |

Docker 服务端口可通过 `MYSQL_PORT`、`REDIS_PORT`、`ROCKETMQ_NAMESRV_PORT`、`ROCKETMQ_BROKER_PORT`、`ROCKETMQ_BROKER_FAST_PORT` 覆盖。若宿主机连接 RocketMQ 需要非默认网卡地址，可设置 `ROCKETMQ_BROKER_IP1` 后再启动编排。

Broker 默认以 `127.0.0.1` 注册，宿主机客户端可直连；Dashboard 运行在容器内，因此编排附带 `rocketmq-dashboard-broker-proxy` 把 Dashboard 自身的 `127.0.0.1:10911` 转发到 Broker，Dashboard 才能查询 Broker 与 Topic。

Dashboard 启动时按 `ROCKETMQ_DASHBOARD_USERNAME`、`ROCKETMQ_DASHBOARD_PASSWORD` 写入 `users.properties`，修改账号后需要重新创建容器才生效。

各服务的容器、命令行与 Dashboard 检查方式见 [本地基础服务检查说明](local-infrastructure-health-check.md)。
