package com.wangguangwu.claim.application.usecase;

import com.wangguangwu.claim.application.domain.application.Application;
import com.wangguangwu.claim.application.domain.application.ApplicationRepository;
import com.wangguangwu.claim.application.domain.application.ApplicationStatus;
import com.wangguangwu.claim.application.domain.interaction.InteractionLogRepository;
import com.wangguangwu.claim.application.domain.material.MaterialRepository;
import com.wangguangwu.claim.application.domain.outbox.OutboxEventRepository;
import com.wangguangwu.claim.application.usecase.command.ApplicationIntakeCommand;
import com.wangguangwu.claim.application.usecase.result.ApplicationIntakeDisposition;
import com.wangguangwu.claim.application.usecase.result.ApplicationIntakeResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 申请签收的数据库事务服务，只负责持久化编排，不访问 Redis。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Service
public class ApplicationIntakeTransactionService {

    private final ApplicationRepository applicationRepository;
    private final MaterialRepository materialRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationIntakeRecordFactory recordFactory;

    public ApplicationIntakeTransactionService(ApplicationRepository applicationRepository,
                                               MaterialRepository materialRepository,
                                               InteractionLogRepository interactionLogRepository,
                                               OutboxEventRepository outboxEventRepository,
                                               ApplicationIntakeRecordFactory recordFactory) {
        this.applicationRepository = applicationRepository;
        this.materialRepository = materialRepository;
        this.interactionLogRepository = interactionLogRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.recordFactory = recordFactory;
    }

    /**
     * 在本地事务中完成首次签收、重复复用或受理失败后的重新申请。
     *
     * @param command                标准化申请命令
     * @param existed                存在性提示：true 表示数据库中已有同外部申请号记录
     * @param candidateApplicationNo 存在性提示为 false 时必须提供的候选平台申请编号
     * @param requestId              请求标识
     * @param traceId                链路标识
     * @return 签收结果
     */
    @Transactional
    public ApplicationIntakeResult intake(ApplicationIntakeCommand command, boolean existed,
                                          String candidateApplicationNo, String requestId, String traceId) {
        if (!existed) {
            return create(command, candidateApplicationNo, requestId, traceId);
        }
        return applicationRepository.findByTenantAndExternalApplicationNo(command.tenantCode(),
                        command.externalApplicationNo())
                .map(application -> processExisting(application, command, requestId, traceId))
                .orElseThrow(ApplicationIntakeCacheStaleException::new);
    }

    /**
     * 唯一键冲突后在新事务中解析同一外部申请号是否已由并发请求创建。
     *
     * @param command   标准化申请命令
     * @param requestId 请求标识
     * @param traceId   链路标识
     * @return 已存在申请的结果；不存在时返回空
     */
    @Transactional
    public Optional<ApplicationIntakeResult> resolveAfterDuplicateKey(ApplicationIntakeCommand command,
                                                                      String requestId, String traceId) {
        return applicationRepository.findByTenantAndExternalApplicationNo(command.tenantCode(),
                        command.externalApplicationNo())
                .map(application -> processExisting(application, command, requestId, traceId));
    }

    /**
     * 创建首次 Application、材料、交互日志与 Outbox 事件。
     *
     * @param command               标准化申请命令
     * @param candidateApplicationNo 候选平台申请编号
     * @param requestId             请求标识
     * @param traceId               链路标识
     * @return 新建结果
     */
    private ApplicationIntakeResult create(ApplicationIntakeCommand command, String candidateApplicationNo,
                                           String requestId, String traceId) {
        Application application = Application.createPending(command.tenantCode(), candidateApplicationNo,
                command.externalApplicationNo(), recordFactory.serialize(command.applicant()),
                recordFactory.serialize(command.primaryInsured()), recordFactory.serialize(command.accidentPerson()),
                recordFactory.serialize(command.payee()));
        applicationRepository.insert(application);

        String interactionNo = newInteractionNo();
        recordStarted(candidateApplicationNo, interactionNo, requestId, traceId);
        materialRepository.insertAll(recordFactory.createMaterials(candidateApplicationNo, command.materials()));
        outboxEventRepository.insert(recordFactory.applicationCreatedEvent(application));
        recordFinished(candidateApplicationNo, interactionNo, requestId, traceId, true);
        return new ApplicationIntakeResult(candidateApplicationNo, ApplicationIntakeDisposition.CREATED);
    }

    /**
     * 根据当前状态处理重复请求或重新申请。
     *
     * <p>重新申请使用状态条件更新：并发请求已先完成重新申请时更新行数为 0，
     * 本次请求按重复请求复用已有申请，不覆盖对方写入的人员与材料。</p>
     *
     * @param application 当前 Application
     * @param command     标准化申请命令
     * @param requestId   请求标识
     * @param traceId     链路标识
     * @return 复用或重新申请结果
     */
    private ApplicationIntakeResult processExisting(Application application, ApplicationIntakeCommand command,
                                                    String requestId, String traceId) {
        String applicationNo = application.getApplicationNo();
        String interactionNo = newInteractionNo();
        recordStarted(applicationNo, interactionNo, requestId, traceId);
        if (application.getStatus() != ApplicationStatus.ACCEPT_FAILED) {
            recordFinished(applicationNo, interactionNo, requestId, traceId, false);
            return new ApplicationIntakeResult(applicationNo, ApplicationIntakeDisposition.REUSED);
        }

        application.reapply(recordFactory.serialize(command.applicant()),
                recordFactory.serialize(command.primaryInsured()),
                recordFactory.serialize(command.accidentPerson()), recordFactory.serialize(command.payee()));
        if (!applicationRepository.updateForReapply(application)) {
            recordFinished(applicationNo, interactionNo, requestId, traceId, false);
            return new ApplicationIntakeResult(applicationNo, ApplicationIntakeDisposition.REUSED);
        }

        materialRepository.softDeleteByApplicationNo(applicationNo);
        materialRepository.insertAll(recordFactory.createMaterials(applicationNo, command.materials()));
        outboxEventRepository.insert(recordFactory.applicationReappliedEvent(application));
        recordFinished(applicationNo, interactionNo, requestId, traceId, true);
        return new ApplicationIntakeResult(applicationNo, ApplicationIntakeDisposition.REAPPLIED);
    }

    private String newInteractionNo() {
        return UUID.randomUUID().toString();
    }

    private void recordStarted(String applicationNo, String interactionNo, String requestId, String traceId) {
        interactionLogRepository.insert(recordFactory.interactionStarted(applicationNo, interactionNo, requestId,
                traceId));
    }

    private void recordFinished(String applicationNo, String interactionNo, String requestId, String traceId,
                                boolean completed) {
        interactionLogRepository.insert(recordFactory.interactionFinished(applicationNo, interactionNo, requestId,
                traceId, completed));
    }
}
