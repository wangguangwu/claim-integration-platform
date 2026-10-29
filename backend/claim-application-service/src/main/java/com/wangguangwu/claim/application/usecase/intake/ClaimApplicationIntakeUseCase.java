package com.wangguangwu.claim.application.usecase.intake;

import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationExistenceQuery;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationIntakeLock;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationNoGenerator;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.MaterialSubmission;
import com.wangguangwu.claim.application.domain.claim.person.PersonInfo;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeException;
import com.wangguangwu.claim.application.usecase.intake.command.ClaimApplicationIntakeCommand;
import com.wangguangwu.claim.application.usecase.intake.result.ClaimApplicationIntakeResult;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 理赔申请统一接入签收用例入口，负责参数校验、并发锁、事务外准备数据与唯一冲突恢复。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Service
public class ClaimApplicationIntakeUseCase {

    private static final int MAX_UNIQUE_KEY_RETRY_TIMES = 3;

    private final ClaimApplicationIntakeLock claimApplicationIntakeLock;
    private final ClaimApplicationExistenceQuery claimApplicationExistenceQuery;
    private final ClaimApplicationNoGenerator claimApplicationNoGenerator;
    private final ClaimApplicationIntakeTransactionService transactionService;

    /**
     * 校验受理命令后创建理赔申请，并在同一外部申请号锁内签收，成功提交后失效存在性缓存。
     *
     * @param command   租户协议归一化后的受理命令
     * @param requestId 请求标识
     * @param traceId   链路标识
     * @return 签收结果
     */
    public ClaimApplicationIntakeResult intake(ClaimApplicationIntakeCommand command, String requestId, String traceId) {
        validate(command);
        ClaimApplication claimApplication = ClaimApplication.submit(command.tenantCode(), command.externalClaimApplicationNo(),
                command.applicant(), command.primaryInsured(), command.accidentPerson(), command.payee(),
                command.materials());
        return claimApplicationIntakeLock.execute(claimApplication.getTenantCode(), claimApplication.getExternalClaimApplicationNo(),
                () -> executeInLock(claimApplication, requestId, traceId));
    }

    /**
     * 在锁内于事务外完成存在性判断与编号生成，再交由事务服务落库。
     *
     * <p>存在性缓存与实际数据不一致或唯一键冲突时，淘汰缓存并在下一次重试中重新判断；
     * 唯一键冲突且已存在同外部申请号记录时，直接按重复请求处理。</p>
     *
     * @param claimApplication 待签收的理赔申请
     * @param requestId   请求标识
     * @param traceId     链路标识
     * @return 签收结果
     */
    private ClaimApplicationIntakeResult executeInLock(ClaimApplication claimApplication, String requestId, String traceId) {
        boolean forceCreate = false;
        for (int retryTimes = 0; retryTimes < MAX_UNIQUE_KEY_RETRY_TIMES; retryTimes++) {
            try {
                boolean existed = !forceCreate && claimApplicationExistenceQuery.exists(claimApplication.getTenantCode(),
                        claimApplication.getExternalClaimApplicationNo());
                String candidateClaimApplicationNo = existed ? null : claimApplicationNoGenerator.nextClaimApplicationNo();
                ClaimApplicationIntakeResult result = transactionService.intake(claimApplication, existed,
                        candidateClaimApplicationNo, requestId, traceId);
                evictAfterChanged(result, claimApplication);
                return result;
            } catch (ClaimApplicationExistenceMismatchException exception) {
                claimApplicationExistenceQuery.evict(claimApplication.getTenantCode(), claimApplication.getExternalClaimApplicationNo());
                forceCreate = true;
            } catch (DuplicateKeyException exception) {
                Optional<ClaimApplicationIntakeResult> resolvedResult = transactionService.resolveAfterDuplicateKey(
                        claimApplication, requestId, traceId);
                if (resolvedResult.isPresent()) {
                    evictAfterChanged(resolvedResult.get(), claimApplication);
                    return resolvedResult.get();
                }
                claimApplicationExistenceQuery.evict(claimApplication.getTenantCode(), claimApplication.getExternalClaimApplicationNo());
                claimApplicationNoGenerator.synchronizeDailyCounter();
                forceCreate = false;
            }
        }
        throw new IllegalStateException("理赔申请唯一键冲突重试次数超限，tenantCode=" + claimApplication.getTenantCode()
                + ", externalClaimApplicationNo=" + claimApplication.getExternalClaimApplicationNo());
    }

    /**
     * 仅在创建或重新申请已完成提交后失效存在性缓存。
     *
     * @param result      签收结果
     * @param claimApplication 待签收的理赔申请
     */
    private void evictAfterChanged(ClaimApplicationIntakeResult result, ClaimApplication claimApplication) {
        if (result.changed()) {
            claimApplicationExistenceQuery.evict(claimApplication.getTenantCode(), claimApplication.getExternalClaimApplicationNo());
        }
    }

    /**
     * 校验用例层必须具备的标准字段，避免租户私有数据进入领域层。
     *
     * @param command 租户协议归一化后的受理命令
     */
    private void validate(ClaimApplicationIntakeCommand command) {
        if (command == null || !StringUtils.hasText(command.tenantCode())
                || !StringUtils.hasText(command.externalClaimApplicationNo())) {
            throw ClaimApplicationIntakeException.invalid("租户编码和外部申请编号不能为空");
        }
        validatePerson(command.applicant(), "申请人");
        validatePerson(command.primaryInsured(), "主被保险人");
        validatePerson(command.accidentPerson(), "出险人");
        validatePerson(command.payee(), "领款人");
        validateMaterials(command.materials());
    }

    /**
     * 校验四类人员的基础字段完整性。
     *
     * @param personInfo 人员信息
     * @param personRole 人员角色名称
     */
    private void validatePerson(PersonInfo personInfo, String personRole) {
        if (personInfo == null || !StringUtils.hasText(personInfo.name()) || personInfo.gender() == null
                || !StringUtils.hasText(personInfo.certificateType())
                || !StringUtils.hasText(personInfo.certificateNo())) {
            throw ClaimApplicationIntakeException.invalid(personRole + "的姓名、性别、证件类型和证件号不能为空");
        }
    }

    /**
     * 校验至少存在一项材料，且每项保留类型和原始文件地址。
     *
     * @param materials 本次提交的材料
     */
    private void validateMaterials(List<MaterialSubmission> materials) {
        if (materials == null || materials.isEmpty()) {
            throw ClaimApplicationIntakeException.invalid("首次签收至少需要一项材料");
        }
        boolean invalidMaterial = materials.stream().anyMatch(material -> material == null
                || !StringUtils.hasText(material.materialType()) || !StringUtils.hasText(material.originalFileUrl()));
        if (invalidMaterial) {
            throw ClaimApplicationIntakeException.invalid("材料类型和初始文件地址不能为空");
        }
    }
}
