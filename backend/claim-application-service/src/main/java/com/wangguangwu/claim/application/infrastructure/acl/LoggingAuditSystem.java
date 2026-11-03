package com.wangguangwu.claim.application.infrastructure.acl;

import com.wangguangwu.claim.application.application.port.AuditSystemPort;
import com.wangguangwu.claim.application.application.port.model.AuditDeliveryRequest;
import com.wangguangwu.claim.application.application.port.model.AuditSignedStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 审核系统的日志实现：未接入真实系统，只打印调用日志并返回可继续流程的默认结果。
 *
 * <p>交付只记录日志；签收状态按"处理中"返回，补偿任务保持等待审核状态，不做虚假完成。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@Slf4j
@Component
public class LoggingAuditSystem implements AuditSystemPort {

    /**
     * 交付申请；未接入审核系统，只记录调用日志。
     *
     * @param request 交付入参
     */
    @Override
    public void deliver(AuditDeliveryRequest request) {
        log.info("审核系统未接入，交付仅记录日志，claimApplicationNo={}，traceId={}", request.claimApplicationNo(),
                request.traceId());
    }

    /**
     * 查询审核签收状态；未接入审核系统，固定按处理中返回。
     *
     * @param claimApplicationNo 平台申请编号
     * @return 处理中状态
     */
    @Override
    public AuditSignedStatus querySignedStatus(String claimApplicationNo) {
        log.info("审核系统未接入，签收状态按处理中返回，claimApplicationNo={}", claimApplicationNo);
        return new AuditSignedStatus.Processing();
    }
}
