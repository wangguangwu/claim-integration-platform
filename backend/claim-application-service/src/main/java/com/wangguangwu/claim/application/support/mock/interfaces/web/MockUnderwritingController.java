package com.wangguangwu.claim.application.support.mock.interfaces.web;

import com.wangguangwu.claim.application.domain.underwriting.UnderwritingResult;
import com.wangguangwu.claim.application.support.mock.infrastructure.MockUnderwritingStore;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模拟承保接口，只在模拟开关开启时注册。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@RestController
@ConditionalOnProperty(name = "claim.mock.enabled", havingValue = "true")
public class MockUnderwritingController {

    private final MockUnderwritingStore mockUnderwritingStore;

    /**
     * 登记有效保单，重复登记覆盖。
     *
     * @param request 保单登记请求
     */
    @PostMapping("/mock/underwriting/policies")
    public void registerPolicy(@RequestBody PolicyRegistrationRequest request) {
        mockUnderwritingStore.registerPolicy(request.certificateNo(), request.policyNo());
    }

    /**
     * 清除有效保单。
     *
     * @param certificateNo 被保人证件号
     */
    @DeleteMapping("/mock/underwriting/policies/{certificateNo}")
    public void removePolicy(@PathVariable("certificateNo") String certificateNo) {
        mockUnderwritingStore.removePolicy(certificateNo);
    }

    /**
     * 查看当前保单，不返回人员姓名。
     *
     * @return 保单列表
     */
    @GetMapping("/mock/underwriting/policies")
    public List<MockUnderwritingStore.PolicyRecord> policies() {
        return mockUnderwritingStore.policies();
    }

    /**
     * 保单查询，供业务链路调用。
     *
     * @param request 保单查询请求
     * @return 保单查询结果
     */
    @PostMapping("/mock/underwriting/v1/policy-queries")
    public MockUnderwritingStore.PolicyQueryOutcome queryPolicy(@RequestBody PolicyQueryRequest request) {
        return mockUnderwritingStore.queryPolicy(request.insuredCertificateNo());
    }

    /**
     * 发起自动承保，按平台申请编号幂等。
     *
     * @param request 自动承保请求
     * @return 承保状态
     */
    @PostMapping("/mock/underwriting/v1/auto-underwritings")
    public AutoUnderwritingStatusResponse requestAutoUnderwriting(@RequestBody AutoUnderwritingRequest request) {
        MockUnderwritingStore.AutoUnderwritingRecord record = mockUnderwritingStore.requestAutoUnderwriting(
                new MockUnderwritingStore.AutoUnderwritingCommand(request.applicationNo(), request.tenantCode(),
                        request.insuredCertificateNo()));
        return AutoUnderwritingStatusResponse.of(record);
    }

    /**
     * 查询自动承保结果，供补偿任务使用。
     *
     * @param applicationNo 平台申请编号
     * @return 承保状态；无记录时返回 NOT_FOUND
     */
    @GetMapping("/mock/underwriting/v1/auto-underwritings/{applicationNo}")
    public AutoUnderwritingStatusResponse queryAutoUnderwriting(@PathVariable("applicationNo") String applicationNo) {
        return mockUnderwritingStore.findAutoUnderwriting(applicationNo)
                .map(AutoUnderwritingStatusResponse::of)
                .orElseGet(() -> AutoUnderwritingStatusResponse.notFound(applicationNo));
    }

    /**
     * 手工触发承保完成并发送完成通知，供联调使用。
     *
     * @param applicationNo 平台申请编号
     * @return 承保状态；无记录时返回 NOT_FOUND
     */
    @PostMapping("/mock/underwriting/auto-underwritings/{applicationNo}/complete")
    public AutoUnderwritingStatusResponse completeAutoUnderwriting(
            @PathVariable("applicationNo") String applicationNo) {
        return mockUnderwritingStore.completeAutoUnderwriting(applicationNo)
                .map(AutoUnderwritingStatusResponse::of)
                .orElseGet(() -> AutoUnderwritingStatusResponse.notFound(applicationNo));
    }

    /**
     * 保单登记请求。
     *
     * @param certificateNo 被保人证件号
     * @param policyNo      保单号
     */
    public record PolicyRegistrationRequest(String certificateNo, String policyNo) {
    }

    /**
     * 保单查询请求。
     *
     * @param tenantCode           租户编码
     * @param insuredCertificateNo 被保人证件号
     */
    public record PolicyQueryRequest(String tenantCode, String insuredCertificateNo) {
    }

    /**
     * 自动承保请求。
     *
     * @param applicationNo        平台申请编号
     * @param tenantCode           租户编码
     * @param insuredCertificateNo 被保人证件号
     */
    public record AutoUnderwritingRequest(String applicationNo, String tenantCode, String insuredCertificateNo) {
    }

    /**
     * 自动承保状态响应。
     *
     * @param applicationNo 平台申请编号
     * @param status        承保状态
     */
    public record AutoUnderwritingStatusResponse(String applicationNo, String status) {

        /**
         * 按承保记录构建响应。
         *
         * @param record 承保记录
         * @return 状态响应
         */
        public static AutoUnderwritingStatusResponse of(MockUnderwritingStore.AutoUnderwritingRecord record) {
            return new AutoUnderwritingStatusResponse(record.applicationNo(), record.status().name());
        }

        /**
         * 构建查无记录的响应。
         *
         * @param applicationNo 平台申请编号
         * @return 状态响应
         */
        public static AutoUnderwritingStatusResponse notFound(String applicationNo) {
            return new AutoUnderwritingStatusResponse(applicationNo, UnderwritingResult.NOT_FOUND.name());
        }
    }
}
