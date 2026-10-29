package com.wangguangwu.claim.application.domain.claim;

import com.wangguangwu.claim.application.domain.claim.person.AccidentPerson;
import com.wangguangwu.claim.application.domain.claim.person.Applicant;
import com.wangguangwu.claim.application.domain.claim.person.Payee;
import com.wangguangwu.claim.application.domain.claim.person.PrimaryInsured;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 理赔申请聚合根，承载平台统一的人员信息、提交材料与生命周期状态。
 *
 * <p>租户协议流水线的转换产物即为该模型：提交结果只包含租户口径的业务字段，
 * 平台申请编号与状态由签收用例绑定。字段装配只通过本类的领域工厂完成。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Getter
@Builder(access = AccessLevel.PRIVATE)
public class ClaimApplication {

    /** 数据库主键，仅用于持久化更新条件。 */
    private Long id;

    /** 理赔申请所属租户，用于数据隔离和外部申请幂等。 */
    private String tenantCode;

    /** 平台生成的全局唯一理赔申请编号，对内外关联均使用该值。 */
    private String applicationNo;

    /** 租户侧申请编号，与租户编码共同唯一标识一次外部提交。 */
    private String externalApplicationNo;

    /** 理赔申请当前生命周期状态，约束可执行的业务迁移。 */
    private ApplicationStatus status;

    /** 同一外部申请编号的受理尝试次数，重新申请时递增。 */
    private Integer applicationAttempt;

    /** 申请人标准化信息。 */
    private Applicant applicant;

    /** 主被保险人标准化信息，用于保单查询。 */
    private PrimaryInsured primaryInsured;

    /** 出险人标准化信息。 */
    private AccidentPerson accidentPerson;

    /** 领款人标准化信息。 */
    private Payee payee;

    /** 本次提交的材料，落库后由材料仓储独立管理。 */
    private List<MaterialSubmission> materials;

    /**
     * 创建租户协议提交结果，此时还没有平台申请编号与状态。
     *
     * @param tenantCode            租户编码
     * @param externalApplicationNo 外部申请编号
     * @param applicant             申请人
     * @param primaryInsured        主被保险人
     * @param accidentPerson        出险人
     * @param payee                 领款人
     * @param materials             本次提交的材料
     * @return 待签收的理赔申请
     */
    public static ClaimApplication submit(String tenantCode, String externalApplicationNo, Applicant applicant,
                                          PrimaryInsured primaryInsured, AccidentPerson accidentPerson, Payee payee,
                                          List<MaterialSubmission> materials) {
        return ClaimApplication.builder().tenantCode(tenantCode).externalApplicationNo(externalApplicationNo)
                .applicant(applicant).primaryInsured(primaryInsured).accidentPerson(accidentPerson).payee(payee)
                .materials(materials).build();
    }

    /**
     * 从已落库的数据重建聚合，仅供持久化层反水化使用。
     *
     * <p>本次提交的材料只在签收时产生，重建时不随申请返回，因此本工厂不含材料入参。</p>
     *
     * @param id                  数据库主键
     * @param tenantCode          租户编码
     * @param applicationNo       平台申请编号
     * @param externalApplicationNo 外部申请编号
     * @param status              当前生命周期状态
     * @param applicationAttempt  受理尝试次数
     * @param applicant           申请人
     * @param primaryInsured      主被保险人
     * @param accidentPerson      出险人
     * @param payee               领款人
     * @return 已落库的理赔申请
     */
    public static ClaimApplication restore(Long id, String tenantCode, String applicationNo,
                                           String externalApplicationNo, ApplicationStatus status,
                                           Integer applicationAttempt, Applicant applicant,
                                           PrimaryInsured primaryInsured, AccidentPerson accidentPerson, Payee payee) {
        return ClaimApplication.builder().id(id).tenantCode(tenantCode).applicationNo(applicationNo)
                .externalApplicationNo(externalApplicationNo).status(status).applicationAttempt(applicationAttempt)
                .applicant(applicant).primaryInsured(primaryInsured).accidentPerson(accidentPerson).payee(payee)
                .build();
    }

    /**
     * 绑定平台申请编号并进入待受理状态，作为首次签收的领域起点。
     *
     * @param applicationNo 平台申请编号
     */
    public void bindApplicationNo(String applicationNo) {
        this.applicationNo = applicationNo;
        this.status = ApplicationStatus.PENDING_ACCEPT;
        this.applicationAttempt = 1;
    }

    /**
     * 回填数据库生成的主键，只允许在插入成功且主键尚未绑定时发生。
     *
     * @param id 数据库主键
     */
    public void bindId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("理赔申请主键已绑定，applicationNo=" + applicationNo + "，id=" + this.id);
        }
        this.id = id;
    }

    /**
     * 使用新一次协议提交的人员信息与材料重新发起失败申请。
     *
     * @param submitted 新一次提交的理赔申请
     */
    public void reapply(ClaimApplication submitted) {
        if (status != ApplicationStatus.ACCEPT_FAILED) {
            throw new IllegalStateException("仅受理失败的申请允许重新申请");
        }
        status = ApplicationStatus.PENDING_ACCEPT;
        applicationAttempt = applicationAttempt + 1;
        this.applicant = submitted.applicant;
        this.primaryInsured = submitted.primaryInsured;
        this.accidentPerson = submitted.accidentPerson;
        this.payee = submitted.payee;
        this.materials = submitted.materials;
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
     * 读取主被保险人证件号，供保单查询与自动承保使用。
     *
     * @return 主被保险人证件号
     */
    public String primaryInsuredCertificateNo() {
        if (primaryInsured == null || primaryInsured.certificateNo() == null
                || primaryInsured.certificateNo().isBlank()) {
            throw new IllegalStateException("主被保险人缺少证件号，applicationNo=" + applicationNo);
        }
        return primaryInsured.certificateNo();
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
