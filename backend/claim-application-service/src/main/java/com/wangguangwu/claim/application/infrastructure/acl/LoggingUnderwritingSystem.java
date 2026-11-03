package com.wangguangwu.claim.application.infrastructure.acl;

import com.wangguangwu.claim.application.application.port.UnderwritingSystemPort;
import com.wangguangwu.claim.application.application.port.model.UnderwritingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 承保系统的日志实现：未接入真实系统，只打印调用日志并模拟承保完成。
 *
 * <p>保单查询按"无有效保单"处理，流程进入自动承保；自动承保发起后立即返回承保完成，
 * 申请可直接推进到等待审核。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@Slf4j
@Component
public class LoggingUnderwritingSystem implements UnderwritingSystemPort {

    /**
     * 查询有效保单；未接入承保系统，固定按无有效保单返回。
     *
     * @param tenantCode           租户编码
     * @param insuredCertificateNo 被保人证件号
     * @return 固定返回 false
     */
    @Override
    public boolean hasValidPolicy(String tenantCode, String insuredCertificateNo) {
        log.info("承保系统未接入，有效保单查询按无有效保单处理，tenantCode={}", tenantCode);
        return false;
    }

    /**
     * 发起自动承保；未接入承保系统，只记录发起日志。
     *
     * @param claimApplicationNo   平台申请编号
     * @param tenantCode           租户编码
     * @param insuredCertificateNo 被保人证件号
     */
    @Override
    public void requestAutoUnderwriting(String claimApplicationNo, String tenantCode, String insuredCertificateNo) {
        log.info("保单发起承保，claimApplicationNo={}，tenantCode={}", claimApplicationNo, tenantCode);
    }

    /**
     * 查询承保结果；未接入承保系统，固定按承保完成返回。
     *
     * @param claimApplicationNo 平台申请编号
     * @return 固定返回 {@link UnderwritingResult#COMPLETED}
     */
    @Override
    public UnderwritingResult queryUnderwritingResult(String claimApplicationNo) {
        log.info("保单承保完成，claimApplicationNo={}", claimApplicationNo);
        return UnderwritingResult.COMPLETED;
    }
}
