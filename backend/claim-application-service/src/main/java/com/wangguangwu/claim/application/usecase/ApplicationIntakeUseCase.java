package com.wangguangwu.claim.application.usecase;

import com.wangguangwu.claim.application.usecase.command.ApplicationIntakeCommand;
import com.wangguangwu.claim.application.usecase.result.ApplicationIntakeResult;

/**
 * 理赔申请统一接入签收用例。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface ApplicationIntakeUseCase {

    /**
     * 签收标准化申请命令。
     *
     * @param command 标准化申请命令
     * @param requestId 请求标识
     * @param traceId 链路标识
     * @return 签收结果
     */
    ApplicationIntakeResult intake(ApplicationIntakeCommand command, String requestId, String traceId);
}
