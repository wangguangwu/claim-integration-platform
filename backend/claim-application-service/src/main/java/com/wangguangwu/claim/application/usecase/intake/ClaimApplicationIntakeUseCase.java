package com.wangguangwu.claim.application.usecase.intake;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationIntakeLookup;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationIntakeLookupKind;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationIntakePolicy;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationIntakeLock;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationIntakeSnapshotQuery;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationNoGenerator;
import com.wangguangwu.claim.application.usecase.intake.command.ClaimApplicationIntakeCommand;
import com.wangguangwu.claim.application.usecase.intake.result.ClaimApplicationIntakeResult;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 理赔申请统一接入签收用例入口，负责并发锁、事务外准备数据与唯一冲突恢复。
 *
 * <p>报文必填由租户 request 上的 Bean Validation 约束负责，领域不变式由聚合与值对象在构造处自守，
 * 本类不重复字段校验，只编排锁、签收判定、编号生成与事务落库。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Service
public class ClaimApplicationIntakeUseCase {

    private final ClaimApplicationIntakeLock claimApplicationIntakeLock;
    private final ClaimApplicationIntakeSnapshotQuery claimApplicationIntakeSnapshotQuery;
    private final ClaimApplicationNoGenerator claimApplicationNoGenerator;
    private final ClaimApplicationIntakePolicy claimApplicationIntakePolicy;
    private final ClaimApplicationIntakeTransactionService transactionService;

    /**
     * 创建理赔申请，并在同一外部申请号锁内完成签收。
     *
     * @param command   租户协议归一化后的受理命令
     * @param requestId 请求标识
     * @param traceId   链路标识
     * @return 签收结果
     */
    public ClaimApplicationIntakeResult intake(ClaimApplicationIntakeCommand command, String requestId, String traceId) {
        ClaimApplication claimApplication = ClaimApplication.submit(command.tenantCode(), command.externalClaimApplicationNo(),
                command.applicant(), command.primaryInsured(), command.accidentPerson(), command.payee(),
                command.materials());
        return claimApplicationIntakeLock.execute(claimApplication.getTenantCode(), claimApplication.getExternalClaimApplicationNo(),
                () -> executeInLock(claimApplication, requestId, traceId));
    }

    /**
     * 在锁内完成签收判定，可复用结论只写轨迹，其余结论交由事务服务落库。
     *
     * <p>唯一键冲突说明并发请求已创建同一外部申请号，先按重复请求解析；解析不到时重新判定并重试，
     * 重试上限由配置给出。判定缓存与实际数据不一致时也不淘汰缓存：安全状态单调不可逆，
     * 最多让本次请求多读一次库。</p>
     *
     * <p>事务内读到的安全状态随产物返回，事务提交后再把存在标记升级为可复用快照，
     * 使后续重复请求不再回源。</p>
     *
     * @param claimApplication 待签收的理赔申请
     * @param requestId        请求标识
     * @param traceId          链路标识
     * @return 签收结果
     */
    private ClaimApplicationIntakeResult executeInLock(ClaimApplication claimApplication, String requestId, String traceId) {
        boolean forceCreate = false;
        for (int retryTimes = 0; retryTimes < claimApplicationIntakePolicy.uniqueKeyRetryTimes(); retryTimes++) {
            try {
                ClaimApplicationIntakeLookup lookup = forceCreate
                        ? ClaimApplicationIntakeLookup.absent()
                        : claimApplicationIntakeSnapshotQuery.lookup(claimApplication.getTenantCode(),
                                claimApplication.getExternalClaimApplicationNo());
                if (lookup.kind() == ClaimApplicationIntakeLookupKind.REUSABLE) {
                    return transactionService.recordReused(lookup.snapshot().claimApplicationNo(), requestId, traceId);
                }
                boolean absent = lookup.kind() == ClaimApplicationIntakeLookupKind.ABSENT;
                String candidateClaimApplicationNo = absent ? claimApplicationNoGenerator.nextClaimApplicationNo() : null;
                ClaimApplicationIntakeOutcome outcome = transactionService.intake(claimApplication, lookup.kind(),
                        candidateClaimApplicationNo, requestId, traceId);
                if (absent) {
                    markMutable(claimApplication);
                }
                return upgradedResult(claimApplication, outcome);
            } catch (ClaimApplicationExistenceMismatchException exception) {
                forceCreate = true;
            } catch (DuplicateKeyException exception) {
                Optional<ClaimApplicationIntakeOutcome> resolvedOutcome = transactionService.resolveAfterDuplicateKey(
                        claimApplication, requestId, traceId);
                if (resolvedOutcome.isPresent()) {
                    markMutable(claimApplication);
                    return upgradedResult(claimApplication, resolvedOutcome.get());
                }
                claimApplicationNoGenerator.synchronizeDailyCounter();
                forceCreate = false;
            }
        }
        throw new IllegalStateException("理赔申请唯一键冲突重试次数超限，tenantCode=" + claimApplication.getTenantCode()
                + ", externalClaimApplicationNo=" + claimApplication.getExternalClaimApplicationNo());
    }

    /**
     * 返回对外结果，并顺带把事务内观察到的可复用快照写入判定缓存。
     *
     * @param claimApplication 本次签收的理赔申请
     * @param outcome          事务产物
     * @return 对外签收结果
     */
    private ClaimApplicationIntakeResult upgradedResult(ClaimApplication claimApplication,
                                                        ClaimApplicationIntakeOutcome outcome) {
        outcome.reusableSnapshot().ifPresent(snapshot -> claimApplicationIntakeSnapshotQuery.markReusable(
                claimApplication.getTenantCode(), claimApplication.getExternalClaimApplicationNo(), snapshot));
        return outcome.result();
    }

    /**
     * 把新创建或并发已创建的申请标记为存在且状态可变，覆盖之前的空标记。
     *
     * @param claimApplication 已存在的理赔申请
     */
    private void markMutable(ClaimApplication claimApplication) {
        claimApplicationIntakeSnapshotQuery.markMutable(claimApplication.getTenantCode(),
                claimApplication.getExternalClaimApplicationNo());
    }
}
