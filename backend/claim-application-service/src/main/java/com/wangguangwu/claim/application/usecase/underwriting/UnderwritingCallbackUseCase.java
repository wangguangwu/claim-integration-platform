package com.wangguangwu.claim.application.usecase.underwriting;

import com.wangguangwu.claim.application.domain.claim.port.ApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.shared.contract.ApplicationAsyncErrorCode;
import com.wangguangwu.claim.application.shared.contract.ApplicationAsyncException;
import com.wangguangwu.claim.application.shared.contract.UnderwritingCompletedCommand;
import com.wangguangwu.claim.application.usecase.integration.record.ApplicationLifecycleRecordFactory;
import com.wangguangwu.claim.application.usecase.underwriting.result.UnderwritingCallbackResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 承保完成通知用例，HTTP 回调与进程内模拟承保共用同一套幂等规则。
 *
 * <p>开始与结束轨迹由事务服务一次写入，用例只负责校验与取申请。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UnderwritingCallbackUseCase {

    private final ApplicationRepository applicationRepository;
    private final ApplicationLifecycleRecordFactory recordFactory;
    private final UnderwritingCallbackTransactionService transactionService;

    /**
     * 处理承保完成通知。
     *
     * @param command 承保完成通知命令
     * @return 处理结果；重复、迟到或状态不匹配时返回复用结果
     */
    public UnderwritingCallbackResult complete(UnderwritingCompletedCommand command) {
        validate(command);
        ClaimApplication application = applicationRepository.findByApplicationNo(command.applicationNo())
                .orElseThrow(() -> new ApplicationAsyncException(ApplicationAsyncErrorCode.APPLICATION_NOT_FOUND,
                        "承保完成通知对应的申请不存在，applicationNo=" + command.applicationNo()));
        UnderwritingCallbackResult result = transactionService.complete(command, application,
                recordFactory.newInteractionNo());
        log.info("承保完成通知处理完成，applicationNo={}, disposition={}, traceId={}", command.applicationNo(),
                result.disposition(), command.traceId());
        return result;
    }

    private void validate(UnderwritingCompletedCommand command) {
        if (isBlank(command.applicationNo())) {
            throw new ApplicationAsyncException(ApplicationAsyncErrorCode.CALLBACK_INVALID, "回调缺少 applicationNo");
        }
        if (isBlank(command.requestId())) {
            throw new ApplicationAsyncException(ApplicationAsyncErrorCode.CALLBACK_INVALID, "回调缺少 X-Request-Id");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
