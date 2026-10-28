package com.wangguangwu.claim.application.domain.application;

import lombok.Getter;
import lombok.Setter;

/**
 * 理赔申请聚合根，维护申请编号、人员信息与生命周期状态。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Getter
@Setter
public class Application {

    private Long id;
    private String tenantCode;
    private String applicationNo;
    private String externalApplicationNo;
    private ApplicationStatus status;
    private Integer applicationAttempt;
    private String applicantJson;
    private String primaryInsuredJson;
    private String accidentPersonJson;
    private String payeeJson;

    /**
     * 创建待受理申请。
     *
     * @param tenantCode            租户编码
     * @param applicationNo         平台申请编号
     * @param externalApplicationNo 外部申请编号
     * @param applicantJson         申请人信息
     * @param primaryInsuredJson    主被保险人信息
     * @param accidentPersonJson    出险人信息
     * @param payeeJson             领款人信息
     * @return 待受理申请
     */
    public static Application createPending(String tenantCode, String applicationNo, String externalApplicationNo,
                                            String applicantJson, String primaryInsuredJson, String accidentPersonJson,
                                            String payeeJson) {
        Application application = new Application();
        application.tenantCode = tenantCode;
        application.applicationNo = applicationNo;
        application.externalApplicationNo = externalApplicationNo;
        application.status = ApplicationStatus.PENDING_ACCEPT;
        application.applicationAttempt = 1;
        application.applicantJson = applicantJson;
        application.primaryInsuredJson = primaryInsuredJson;
        application.accidentPersonJson = accidentPersonJson;
        application.payeeJson = payeeJson;
        return application;
    }

    /**
     * 使用新的人员信息重新发起失败申请。
     *
     * @param applicantJson      申请人信息
     * @param primaryInsuredJson 主被保险人信息
     * @param accidentPersonJson 出险人信息
     * @param payeeJson          领款人信息
     */
    public void reapply(String applicantJson, String primaryInsuredJson, String accidentPersonJson, String payeeJson) {
        if (status != ApplicationStatus.ACCEPT_FAILED) {
            throw new IllegalStateException("仅受理失败的申请允许重新申请");
        }
        status = ApplicationStatus.PENDING_ACCEPT;
        applicationAttempt = applicationAttempt + 1;
        this.applicantJson = applicantJson;
        this.primaryInsuredJson = primaryInsuredJson;
        this.accidentPersonJson = accidentPersonJson;
        this.payeeJson = payeeJson;
    }

    /**
     * 材料处理成功，本次申请受理通过。
     */
    public void accept() {
        transitionTo(ApplicationStatus.PENDING_ACCEPT, ApplicationStatus.ACCEPTED);
    }

    /**
     * 材料处理失败，本次申请尝试结束，等待租户重新申请。
     */
    public void failAccept() {
        transitionTo(ApplicationStatus.PENDING_ACCEPT, ApplicationStatus.ACCEPT_FAILED);
    }

    /**
     * 存在有效保单，进入等待审核签收。
     */
    public void waitAudit() {
        transitionTo(ApplicationStatus.ACCEPTED, ApplicationStatus.WAIT_AUDIT);
    }

    /**
     * 不存在有效保单，进入自动承保处理中。
     */
    public void startUnderwriting() {
        transitionTo(ApplicationStatus.ACCEPTED, ApplicationStatus.UNDERWRITING);
    }

    /**
     * 自动承保完成，进入等待审核签收。
     */
    public void finishUnderwriting() {
        transitionTo(ApplicationStatus.UNDERWRITING, ApplicationStatus.WAIT_AUDIT);
    }

    /**
     * 判断当前状态是否为期望状态，供用例在调用外部系统前做前置校验。
     *
     * @param expectedStatus 期望状态
     * @return 状态一致时为 true
     */
    public boolean isInStatus(ApplicationStatus expectedStatus) {
        return status == expectedStatus;
    }

    private void transitionTo(ApplicationStatus expectedStatus, ApplicationStatus targetStatus) {
        if (status != expectedStatus) {
            throw new IllegalStateException("申请状态不允许迁移，applicationNo=" + applicationNo
                    + "，期望状态=" + expectedStatus + "，实际状态=" + status);
        }
        status = targetStatus;
    }
}
