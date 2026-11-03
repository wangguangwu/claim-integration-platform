/**
 * 技术适配器。
 *
 * <p>把应用端口适配到本地技术设施：Redis 快照缓存、Redis 与 MySQL 联合发号、Redisson 分布式锁、
 * Redis 熔断与本地批次号生成；外部系统实现统一放在 {@code infrastructure.acl}。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.application.infrastructure.adapter;
