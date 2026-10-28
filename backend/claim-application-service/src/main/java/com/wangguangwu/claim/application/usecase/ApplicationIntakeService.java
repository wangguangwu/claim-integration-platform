package com.wangguangwu.claim.application.usecase;

import com.wangguangwu.claim.application.domain.application.ApplicationExistenceQuery;
import com.wangguangwu.claim.application.domain.application.ApplicationIntakeLock;
import com.wangguangwu.claim.application.domain.application.ApplicationNoGenerator;
import com.wangguangwu.claim.application.usecase.command.ApplicationIntakeCommand;
import com.wangguangwu.claim.application.usecase.command.MaterialCommand;
import com.wangguangwu.claim.application.usecase.command.PersonInfo;
import com.wangguangwu.claim.application.usecase.exception.ApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.usecase.exception.ApplicationIntakeException;
import com.wangguangwu.claim.application.usecase.result.ApplicationIntakeResult;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 申请签收用例入口，负责参数校验、并发锁、事务外准备数据与唯一冲突恢复。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Service
public class ApplicationIntakeService implements ApplicationIntakeUseCase {

    private static final int MAX_UNIQUE_KEY_RETRY_TIMES = 3;

    private final ApplicationIntakeLock applicationIntakeLock;
    private final ApplicationExistenceQuery applicationExistenceQuery;
    private final ApplicationNoGenerator applicationNoGenerator;
    private final ApplicationIntakeTransactionService transactionService;

    public ApplicationIntakeService(ApplicationIntakeLock applicationIntakeLock,
                                    ApplicationExistenceQuery applicationExistenceQuery,
                                    ApplicationNoGenerator applicationNoGenerator,
                                    ApplicationIntakeTransactionService transactionService) {
        this.applicationIntakeLock = applicationIntakeLock;
        this.applicationExistenceQuery = applicationExistenceQuery;
        this.applicationNoGenerator = applicationNoGenerator;
        this.transactionService = transactionService;
    }

    /**
     * 校验后在同一外部申请号锁内执行签收，成功提交后失效存在性缓存。
     *
     * @param command   标准化申请命令
     * @param requestId 请求标识
     * @param traceId   链路标识
     * @return 签收结果
     */
    @Override
    public ApplicationIntakeResult intake(ApplicationIntakeCommand command, String requestId, String traceId) {
        validate(command);
        return applicationIntakeLock.execute(command.tenantCode(), command.externalApplicationNo(),
                () -> executeInLock(command, requestId, traceId));
    }

    /**
     * 在锁内于事务外完成存在性判断与编号生成，再交由事务服务落库。
     *
     * <p>存在性缓存与实际数据不一致或唯一键冲突时，淘汰缓存并在下一次重试中重新判断；
     * 唯一键冲突且已存在同外部申请号记录时，直接按重复请求处理。</p>
     *
     * @param command   标准化申请命令
     * @param requestId 请求标识
     * @param traceId   链路标识
     * @return 签收结果
     */
    private ApplicationIntakeResult executeInLock(ApplicationIntakeCommand command, String requestId, String traceId) {
        boolean forceCreate = false;
        for (int retryTimes = 0; retryTimes < MAX_UNIQUE_KEY_RETRY_TIMES; retryTimes++) {
            try {
                boolean existed = !forceCreate && applicationExistenceQuery.exists(command.tenantCode(),
                        command.externalApplicationNo());
                String candidateApplicationNo = existed ? null : applicationNoGenerator.nextApplicationNo();
                ApplicationIntakeResult result = transactionService.intake(command, existed, candidateApplicationNo,
                        requestId, traceId);
                evictAfterChanged(result, command);
                return result;
            } catch (ApplicationIntakeCacheStaleException exception) {
                applicationExistenceQuery.evict(command.tenantCode(), command.externalApplicationNo());
                forceCreate = true;
            } catch (DuplicateKeyException exception) {
                Optional<ApplicationIntakeResult> resolvedResult = transactionService.resolveAfterDuplicateKey(command,
                        requestId, traceId);
                if (resolvedResult.isPresent()) {
                    evictAfterChanged(resolvedResult.get(), command);
                    return resolvedResult.get();
                }
                applicationExistenceQuery.evict(command.tenantCode(), command.externalApplicationNo());
                applicationNoGenerator.synchronizeDailyCounter();
                forceCreate = false;
            }
        }
        throw new IllegalStateException("Application 唯一键冲突重试次数超限，tenantCode=" + command.tenantCode()
                + ", externalApplicationNo=" + command.externalApplicationNo());
    }

    /**
     * 仅在创建或重新申请已完成提交后失效存在性缓存。
     *
     * @param result  签收结果
     * @param command 标准化申请命令
     */
    private void evictAfterChanged(ApplicationIntakeResult result, ApplicationIntakeCommand command) {
        if (result.changed()) {
            applicationExistenceQuery.evict(command.tenantCode(), command.externalApplicationNo());
        }
    }

    /**
     * 校验用例层必须具备的标准字段，避免租户私有数据进入领域层。
     *
     * @param command 标准化申请命令
     */
    private void validate(ApplicationIntakeCommand command) {
        if (command == null || !StringUtils.hasText(command.tenantCode())
                || !StringUtils.hasText(command.externalApplicationNo())) {
            throw invalid("租户编码和外部申请编号不能为空");
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
            throw invalid(personRole + "的姓名、性别、证件类型和证件号不能为空");
        }
    }

    /**
     * 校验至少存在一项材料，且每项保留类型和原始文件地址。
     *
     * @param materialCommands 材料命令
     */
    private void validateMaterials(List<MaterialCommand> materialCommands) {
        if (materialCommands == null || materialCommands.isEmpty()) {
            throw invalid("首次签收至少需要一项材料");
        }
        boolean invalidMaterial = materialCommands.stream().anyMatch(material -> material == null
                || !StringUtils.hasText(material.materialType()) || !StringUtils.hasText(material.originalFileUrl()));
        if (invalidMaterial) {
            throw invalid("材料类型和初始文件地址不能为空");
        }
    }

    /**
     * 构建非法签收命令异常。
     *
     * @param message 错误说明
     * @return 业务异常
     */
    private ApplicationIntakeException invalid(String message) {
        return new ApplicationIntakeException(ApplicationIntakeErrorCode.APPLICATION_INTAKE_INVALID, message);
    }
}
