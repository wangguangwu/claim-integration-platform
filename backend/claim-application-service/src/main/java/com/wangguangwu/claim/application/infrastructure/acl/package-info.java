/**
 * 外部系统防腐层。
 *
 * <p>承接审核系统、承保系统与文件服务的端口实现，负责把外部系统交互隔离在本包内；
 * 当前只打印调用日志并返回可继续流程的默认结果，接入真实系统时只替换本包，不向上层扩散外部模型。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
package com.wangguangwu.claim.application.infrastructure.acl;
