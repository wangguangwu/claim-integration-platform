#!/usr/bin/env bash

set -euo pipefail

# 脚本可从任意目录执行，因此先计算仓库根目录，不依赖调用方当前路径。
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "${script_dir}/.." && pwd)"
compose_file="${project_root}/deploy/docker-compose.yml"
local_env_file="${project_root}/deploy/.env"
example_env_file="${project_root}/deploy/.env.example"

# 本地 .env 不提交，用于覆盖端口、账号和密码；缺失时使用可提交的默认样例。
if [[ -f "${local_env_file}" ]]; then
    env_file="${local_env_file}"
else
    env_file="${example_env_file}"
fi

if [[ ! -f "${compose_file}" || ! -f "${env_file}" ]]; then
    echo "缺少 Docker 编排或环境变量文件" >&2
    exit 1
fi

# --wait 确保 Broker 与 Dashboard 健康后再配置 Topic，--remove-orphans 清理旧版编排遗留的一次性容器。
docker compose --env-file "${env_file}" -f "${compose_file}" up -d --wait --wait-timeout 120 --remove-orphans

# Broker 禁止自动建 Topic；通过管理命令显式、幂等地初始化 Application Topic，避免创建额外的一次性容器。
docker exec claim-rocketmq-broker sh mqadmin updateTopic \
    -n rocketmq-namesrv:9876 \
    -b 127.0.0.1:10911 \
    -t claim_application_material_process_v1 \
    -r 8 \
    -w 8
docker exec claim-rocketmq-broker sh mqadmin updateTopic \
    -n rocketmq-namesrv:9876 \
    -b 127.0.0.1:10911 \
    -t claim_application_audit_delivery_v1 \
    -r 8 \
    -w 8
docker compose --env-file "${env_file}" -f "${compose_file}" ps
