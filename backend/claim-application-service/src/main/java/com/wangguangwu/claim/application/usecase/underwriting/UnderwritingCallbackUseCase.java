package com.wangguangwu.claim.application.usecase.underwriting;

import com.wangguangwu.claim.application.domain.claim.ApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionAction;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionLogRepository;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionType;
import com.wangguangwu.claim.application.shared.contract.ApplicationAsyncErrorCode;
import com.wangguangwu.claim.application.shared.contract.ApplicationAsyncException;
import com.wangguangwu.claim.application.shared.contract.UnderwritingCompletedCommand;
import com.wangguangwu.claim.application.usecase.integration.record.ApplicationLifecycleRecordFactory;
import com.wangguangwu.claim.application.usecase.underwriting.result.UnderwritingCallbackResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 承保完成通知用例，HTTP 回调与进程内模拟承保共用同一套幂等规则。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Service
public class UnderwritingCallbackUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnderwritingCallbackUseCase.class);

    private final ApplicationRepository applicationRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final ApplicationLifecycleRecordFactory recordFactory;
    private final UnderwritingCallbackTransactionService transactionService;

    public UnderwritingCallbackUseCase(ApplicationRepository applicationRepository,
                                       InteractionLogRepository interactionLogRepository,
                                       ApplicationLifecycleRecordFactory recordFactory,
                                       UnderwritingCallbackTransactionService transactionService) {
        this.applicationRepository = applicationRepository;
        this.interactionLogRepository = interactionLogRepository;
        this.recordFactory = recordFactory;
        this.transactionService = transactionService;
    }

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
        String interactionNo = recordFactory.newInteractionNo();
        interactionLogRepository.insert(recordFactory.interaction(InteractionType.UNDERWRITING_CALLBACK,
                command.applicationNo(), interactionNo, InteractionAction.STARTED, command.requestId(),
                command.traceId(), null, null));
        UnderwritingCallbackResult result = transactionService.complete(command, application, interactionNo);
        LOGGER.info("承保完成通知处理完成，applicationNo={}, disposition={}, traceId={}", command.applicationNo(),
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
