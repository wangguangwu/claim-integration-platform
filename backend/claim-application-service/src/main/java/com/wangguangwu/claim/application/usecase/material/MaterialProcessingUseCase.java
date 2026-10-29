package com.wangguangwu.claim.application.usecase.material;

import com.wangguangwu.claim.application.domain.claim.port.ApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.ApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.integration.consumption.ConsumedEventRepository;
import com.wangguangwu.claim.application.domain.material.FileUploadResult;
import com.wangguangwu.claim.application.domain.material.Material;
import com.wangguangwu.claim.application.domain.material.port.MaterialFileProcessor;
import com.wangguangwu.claim.application.domain.material.port.MaterialRepository;
import com.wangguangwu.claim.application.domain.material.MaterialStatus;
import com.wangguangwu.claim.application.domain.material.MaterialUploadRequest;
import com.wangguangwu.claim.application.usecase.integration.record.ApplicationLifecycleRecordFactory;
import com.wangguangwu.claim.application.usecase.material.command.MaterialProcessCommand;
import com.wangguangwu.claim.application.usecase.material.result.MaterialProcessOutcome;
import com.wangguangwu.claim.application.usecase.underwriting.UnderwritingUseCase;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 材料处理用例：校验事件与申请一致性、逐条处理材料并驱动承保分流。
 *
 * <p>行为轨迹的开始与结束都由事务服务一次写入，用例本身不再单独登记开始事件，
 * 避免外部调用中断后留下没有结束的轨迹。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MaterialProcessingUseCase {

    private static final String FILE_UPLOAD_FAILED_CODE = "FILE_UPLOAD_FAILED";

    private final ApplicationRepository applicationRepository;
    private final ConsumedEventRepository consumedEventRepository;
    private final MaterialRepository materialRepository;
    private final MaterialFileProcessor materialFileProcessor;
    private final MaterialProcessingTransactionService transactionService;
    private final ApplicationLifecycleRecordFactory recordFactory;
    private final UnderwritingUseCase underwritingUseCase;

    /**
     * 处理一次材料处理事件。
     *
     * <p>文件处理在事务外执行：文件系统明确拒绝时材料置为失败并推进受理失败；
     * 文件系统不可用或超时时异常向上抛出，由 binder 重投，材料与申请保持原状态。</p>
     *
     * @param command 材料处理命令
     */
    public void process(MaterialProcessCommand command) {
        if (consumedEventRepository.exists(MaterialProcessingTransactionService.CONSUMER_GROUP, command.eventId())) {
            log.info("材料处理事件已消费，跳过处理，applicationNo={}，eventId={}", command.applicationNo(),
                    command.eventId());
            return;
        }
        ClaimApplication application = applicationRepository.findByApplicationNo(command.applicationNo())
                .orElseThrow(() -> new IllegalStateException("材料处理事件对应的申请不存在，applicationNo="
                        + command.applicationNo()));
        String interactionNo = recordFactory.newInteractionNo();
        if (!isProcessable(application, command.applicationAttempt())) {
            log.info("材料处理事件与当前申请不匹配，按重复事件处理，applicationNo={}，status={}，eventAttempt={}",
                    command.applicationNo(), application.getStatus(), command.applicationAttempt());
            transactionService.recordReused(command, interactionNo);
            return;
        }
        List<Material> materials = loadPendingMaterials(command.applicationNo());
        String errorCode = null;
        String errorMessage = null;
        for (Material material : materials) {
            FileUploadResult uploadResult = materialFileProcessor.upload(new MaterialUploadRequest(
                    material.getApplicationNo(), material.getBatchNo(), material.getMaterialType(),
                    material.getOriginalFileUrl()));
            if (uploadResult.success()) {
                material.markProcessed(uploadResult.uploadedFileUrl());
                continue;
            }
            material.markFailed();
            if (errorCode == null) {
                errorCode = uploadResult.errorCode();
                errorMessage = uploadResult.errorMessage();
            }
        }
        boolean allSucceeded = materials.stream()
                .allMatch(material -> material.getStatus() == MaterialStatus.PROCESSED);
        String failureCode = allSucceeded ? null : (errorCode == null ? FILE_UPLOAD_FAILED_CODE : errorCode);
        String failureMessage = allSucceeded ? null : errorMessage;
        MaterialProcessOutcome outcome = transactionService.recordProcessed(command, interactionNo, application,
                materials, allSucceeded, failureCode, failureMessage);
        if (outcome == MaterialProcessOutcome.ACCEPTED) {
            underwritingUseCase.dispatch(command.applicationNo(), command.traceId());
        }
    }

    private boolean isProcessable(ClaimApplication application, int eventApplicationAttempt) {
        return application.isInStatus(ApplicationStatus.PENDING_ACCEPT)
                && Objects.equals(application.getApplicationAttempt(), eventApplicationAttempt);
    }

    private List<Material> loadPendingMaterials(String applicationNo) {
        return materialRepository.findByApplicationNoAndDeletedFalse(applicationNo).stream()
                .filter(material -> material.getStatus() == MaterialStatus.PENDING_PROCESS)
                .toList();
    }
}
