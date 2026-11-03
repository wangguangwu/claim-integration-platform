package com.wangguangwu.claim.application.application.process;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.material.Material;
import com.wangguangwu.claim.application.domain.claim.material.MaterialProcessingResult;
import com.wangguangwu.claim.application.domain.claim.material.MaterialStatus;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.application.port.ConsumedEventRepository;
import com.wangguangwu.claim.application.application.command.MaterialProcessCommand;
import com.wangguangwu.claim.application.application.port.model.FileUploadResult;
import com.wangguangwu.claim.application.application.port.model.MaterialUploadRequest;
import com.wangguangwu.claim.application.application.port.MaterialFileProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 材料处理用例：校验事件与申请一致性、逐条上传材料并驱动承保分流。
 *
 * <p>文件处理在事务外执行，只负责生成材料处理结果；申请状态与材料结果是聚合内的同一份事实，
 * 由事务服务重新加载聚合并一次保存，避免并发请求基于过期快照推进状态。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MaterialProcessingUseCase {

    private static final String FILE_UPLOAD_FAILED_CODE = "FILE_UPLOAD_FAILED";

    private final ClaimApplicationRepository claimApplicationRepository;
    private final ConsumedEventRepository consumedEventRepository;
    private final MaterialFileProcessor materialFileProcessor;
    private final MaterialProcessingTransactionService transactionService;
    private final ClaimApplicationLifecycleRecordFactory recordFactory;
    private final UnderwritingUseCase underwritingUseCase;

    /**
     * 处理一次材料处理事件。
     *
     * <p>文件系统明确拒绝时材料结果记为失败并推进受理失败；文件系统不可用或超时时异常向上抛出，
     * 由 binder 重投，材料与申请保持原状态。</p>
     *
     * @param command 材料处理命令
     */
    public void process(MaterialProcessCommand command) {
        if (consumedEventRepository.exists(MaterialProcessingTransactionService.CONSUMER_GROUP, command.eventId())) {
            log.info("材料处理事件已消费，跳过处理，claimApplicationNo={}，eventId={}", command.claimApplicationNo(),
                    command.eventId());
            return;
        }
        ClaimApplication claimApplication = claimApplicationRepository.findByClaimApplicationNo(command.claimApplicationNo())
                .orElseThrow(() -> new IllegalStateException("材料处理事件对应的申请不存在，claimApplicationNo="
                        + command.claimApplicationNo()));
        String interactionNo = recordFactory.newInteractionNo();
        if (!isProcessable(claimApplication, command.claimApplicationAttempt())) {
            log.info("材料处理事件与当前申请不匹配，按重复事件处理，claimApplicationNo={}，status={}，eventAttempt={}",
                    command.claimApplicationNo(), claimApplication.getStatus(), command.claimApplicationAttempt());
            transactionService.recordReused(command, interactionNo);
            return;
        }
        List<MaterialProcessingResult> results = new ArrayList<>();
        String errorCode = null;
        String errorMessage = null;
        for (Material material : pendingMaterials(claimApplication)) {
            FileUploadResult uploadResult = materialFileProcessor.upload(new MaterialUploadRequest(
                    command.claimApplicationNo(), material.getBatchNo(), material.getMaterialType(),
                    material.getOriginalFileUrl()));
            results.add(new MaterialProcessingResult(material.getId(), uploadResult.success(),
                    uploadResult.success() ? uploadResult.uploadedFileUrl() : null));
            if (!uploadResult.success() && errorCode == null) {
                errorCode = uploadResult.errorCode();
                errorMessage = uploadResult.errorMessage();
            }
        }
        boolean allSucceeded = results.stream().allMatch(MaterialProcessingResult::succeeded);
        String failureCode = allSucceeded ? null : (errorCode == null ? FILE_UPLOAD_FAILED_CODE : errorCode);
        String failureMessage = allSucceeded ? null : errorMessage;
        MaterialProcessOutcome outcome = transactionService.recordProcessed(command, interactionNo, results,
                failureCode, failureMessage);
        if (outcome == MaterialProcessOutcome.ACCEPTED) {
            underwritingUseCase.dispatch(command.claimApplicationNo(), command.traceId());
        }
    }

    private boolean isProcessable(ClaimApplication claimApplication, int eventClaimApplicationAttempt) {
        return claimApplication.isPendingAccept()
                && Objects.equals(claimApplication.getClaimApplicationAttempt(), eventClaimApplicationAttempt);
    }

    private List<Material> pendingMaterials(ClaimApplication claimApplication) {
        return claimApplication.getMaterials().stream()
                .filter(material -> material.getStatus() == MaterialStatus.PENDING_PROCESS)
                .toList();
    }
}
