package com.wangguangwu.claim.application.domain.claim;

import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationErrorCode;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.claim.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.claim.issue.ClaimApplicationIssueStatus;
import com.wangguangwu.claim.application.domain.claim.material.Material;
import com.wangguangwu.claim.application.domain.claim.material.MaterialProcessingResult;
import com.wangguangwu.claim.application.domain.claim.material.MaterialStatus;
import com.wangguangwu.claim.application.domain.claim.material.MaterialSubmission;
import com.wangguangwu.claim.application.domain.claim.person.AccidentPerson;
import com.wangguangwu.claim.application.domain.claim.person.Applicant;
import com.wangguangwu.claim.application.domain.claim.person.Payee;
import com.wangguangwu.claim.application.domain.claim.person.PrimaryInsured;
import com.wangguangwu.claim.application.shared.exception.DomainException;
import com.wangguangwu.claim.foundation.common.util.AssertUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 理赔申请聚合根，承载平台统一的人员信息、当前材料与问题件历史，并保证生命周期状态的一致性。
 *
 * <p>材料处理结果、问题件打开/关闭都必须在同一事务内与申请状态一起落库，因此材料与问题件是聚合内实体，
 * 不单独提供仓储。租户协议流水线的转换产物即为该模型：提交结果只包含租户口径的业务字段，
 * 平台申请编号由签收用例绑定，乐观锁版本由持久化层回填。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Getter
@Builder(access = AccessLevel.PRIVATE)
public class ClaimApplication {

    /**
     * 租户补材关闭问题件时使用的关闭人标识，区分审核系统关闭行为。
     */
    public static final String SUPPLEMENT_AUDITOR = "TENANT_SUPPLEMENT";

    /** 数据库主键，仅用于持久化更新条件。 */
    private Long id;

    /** 乐观锁版本号，为空表示尚未持久化。 */
    private Long version;

    /** 理赔申请所属租户，用于数据隔离和外部申请幂等。 */
    private String tenantCode;

    /** 平台生成的全局唯一理赔申请编号，对内外关联均使用该值。 */
    private String claimApplicationNo;

    /** 租户侧申请编号，与租户编码共同唯一标识一次外部提交。 */
    private String externalClaimApplicationNo;

    /** 理赔申请当前生命周期状态，约束可执行的业务迁移。 */
    private ClaimApplicationStatus status;

    /** 同一外部申请编号的受理尝试次数，重新申请与租户补材时递增。 */
    private Integer claimApplicationAttempt;

    /** 审核完成时间，仅 {@code COMPLETED} 状态非空。 */
    private LocalDateTime completedAt;

    /** 申请人标准化信息。 */
    private Applicant applicant;

    /** 主被保险人标准化信息，用于保单查询。 */
    private PrimaryInsured primaryInsured;

    /** 出险人标准化信息。 */
    private AccidentPerson accidentPerson;

    /** 领款人标准化信息。 */
    private Payee payee;

    /** 当前有效材料，已逻辑删除的历史材料不随聚合加载。 */
    private List<Material> materials;

    /** 全部问题件历史，含已关闭问题件。 */
    private List<ClaimApplicationIssue> issues;

    /**
     * 返回当前有效材料的只读快照，调用方无法通过集合引用增删聚合内实体。
     *
     * @return 不可变材料列表
     */
    public List<Material> getMaterials() {
        return List.copyOf(materials);
    }

    /**
     * 返回全部问题件的只读快照，调用方无法通过集合引用增删聚合内实体。
     *
     * @return 不可变问题件列表
     */
    public List<ClaimApplicationIssue> getIssues() {
        return List.copyOf(issues);
    }

    /**
     * 创建租户协议提交结果，此时还没有平台申请编号与状态。
     *
     * <p>租户编码、外部申请编号与材料清单是聚合创建的前置条件，非法输入在构造阶段拒绝；
     * 人员字段由 {@code PersonInfo} 自行守卫。</p>
     *
     * @param tenantCode            租户编码
     * @param externalClaimApplicationNo 外部申请编号
     * @param applicant             申请人
     * @param primaryInsured        主被保险人
     * @param accidentPerson        出险人
     * @param payee                 领款人
     * @param materialSubmissions        本次提交的材料
     * @param batchNo                    本次提交共用的材料批次编号
     * @return 待签收的理赔申请
     */
    public static ClaimApplication submit(String tenantCode, String externalClaimApplicationNo, Applicant applicant,
                                          PrimaryInsured primaryInsured, AccidentPerson accidentPerson, Payee payee,
                                          List<MaterialSubmission> materialSubmissions, String batchNo) {
        AssertUtil.notBlank(tenantCode, "租户编码不能为空");
        AssertUtil.notBlank(externalClaimApplicationNo, "外部申请编号不能为空");
        AssertUtil.notNull(applicant, "申请人不能为空");
        AssertUtil.notNull(primaryInsured, "主被保险人不能为空");
        AssertUtil.notNull(accidentPerson, "出险人不能为空");
        AssertUtil.notNull(payee, "领款人不能为空");
        AssertUtil.notEmpty(materialSubmissions, "签收至少需要一项材料");
        AssertUtil.notBlank(batchNo, "材料批次编号不能为空");
        List<Material> materials = new ArrayList<>();
        materialSubmissions.forEach(submission -> materials.add(Material.createPending(batchNo, submission)));
        return ClaimApplication.builder().tenantCode(tenantCode).externalClaimApplicationNo(externalClaimApplicationNo)
                .applicant(applicant).primaryInsured(primaryInsured).accidentPerson(accidentPerson).payee(payee)
                .materials(materials).issues(new ArrayList<>()).build();
    }

    /**
     * 从已落库的数据重建完整聚合，仅供持久化层反水化使用。
     *
     * @param id                         数据库主键
     * @param tenantCode                 租户编码
     * @param claimApplicationNo         平台申请编号
     * @param externalClaimApplicationNo 外部申请编号
     * @param status                     当前生命周期状态
     * @param claimApplicationAttempt    受理尝试次数
     * @param completedAt                审核完成时间，仅 COMPLETED 状态非空
     * @param applicant                  申请人
     * @param primaryInsured             主被保险人
     * @param accidentPerson             出险人
     * @param payee                      领款人
     * @param version                    乐观锁版本号
     * @param materials                  当前有效材料
     * @param issues                     全部问题件历史
     * @return 已落库的理赔申请
     */
    public static ClaimApplication restore(Long id, String tenantCode, String claimApplicationNo,
                                           String externalClaimApplicationNo, ClaimApplicationStatus status,
                                           Integer claimApplicationAttempt, LocalDateTime completedAt,
                                           Applicant applicant, PrimaryInsured primaryInsured,
                                           AccidentPerson accidentPerson, Payee payee, Long version,
                                           List<Material> materials, List<ClaimApplicationIssue> issues) {
        boolean completed = status == ClaimApplicationStatus.COMPLETED;
        boolean hasCompletedAt = completedAt != null;
        if (completed != hasCompletedAt) {
            throw new IllegalStateException("理赔申请状态与完成时间不一致，claimApplicationNo=" + claimApplicationNo
                    + "，状态=" + status + "，完成时间=" + completedAt);
        }
        return ClaimApplication.builder().id(id).tenantCode(tenantCode).claimApplicationNo(claimApplicationNo)
                .externalClaimApplicationNo(externalClaimApplicationNo).status(status)
                .claimApplicationAttempt(claimApplicationAttempt).completedAt(completedAt).applicant(applicant)
                .primaryInsured(primaryInsured).accidentPerson(accidentPerson).payee(payee).version(version)
                .materials(materials == null ? new ArrayList<>() : new ArrayList<>(materials))
                .issues(issues == null ? new ArrayList<>() : new ArrayList<>(issues)).build();
    }

    /**
     * 绑定平台申请编号并进入待受理状态，作为首次签收的领域起点。
     *
     * @param claimApplicationNo 平台申请编号
     */
    public void bindClaimApplicationNo(String claimApplicationNo) {
        AssertUtil.notBlank(claimApplicationNo, "平台申请编号不能为空");
        if (this.claimApplicationNo != null) {
            throw new IllegalStateException("理赔申请平台编号已绑定，不允许重复绑定，claimApplicationNo="
                    + this.claimApplicationNo);
        }
        this.claimApplicationNo = claimApplicationNo;
        this.status = ClaimApplicationStatus.PENDING_ACCEPT;
        this.claimApplicationAttempt = 1;
    }

    /**
     * 回填数据库生成的主键，只允许在插入成功且主键尚未绑定时发生。
     *
     * @param id 数据库主键
     */
    public void bindId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("理赔申请主键已绑定，claimApplicationNo=" + claimApplicationNo + "，id=" + this.id);
        }
        this.id = id;
    }

    /**
     * 同步聚合的乐观锁版本，仅供持久化层在插入或保存成功后回填最新版本。
     *
     * @param version 数据库当前版本号
     */
    public void bindVersion(Long version) {
        AssertUtil.notNull(version, "乐观锁版本不能为空");
        this.version = version;
    }

    /**
     * 使用新一次协议提交的人员信息与材料重新发起受理失败的申请。
     *
     * <p>重新申请允许修改人员：四类人员整体替换；旧材料逻辑删除，新批次材料追加到聚合。
     * 与租户补材的区别只在人员处理规则。业务动作时间由应用层按业务时区提供。</p>
     *
     * @param submitted  新一次提交的理赔申请
     * @param batchNo    本次提交的材料批次编号
     * @param operatedAt 本次业务动作时间
     */
    public void reapply(ClaimApplication submitted, String batchNo, LocalDateTime operatedAt) {
        AssertUtil.notNull(operatedAt, "重新申请时间不能为空");
        if (!isReapplicable()) {
            throw new DomainException(ClaimApplicationErrorCode.CLAIM_APPLICATION_STATUS_INVALID,
                    "仅受理失败的申请允许重新申请，claimApplicationNo=" + claimApplicationNo
                            + "，当前状态=" + status);
        }
        List<Material> adoptedMaterials = adoptMaterials(submitted, batchNo);
        softDeleteCurrentMaterials(operatedAt);
        materials.addAll(adoptedMaterials);
        this.applicant = submitted.applicant;
        this.primaryInsured = submitted.primaryInsured;
        this.accidentPerson = submitted.accidentPerson;
        this.payee = submitted.payee;
        this.claimApplicationAttempt = claimApplicationAttempt + 1;
        status = ClaimApplicationStatus.PENDING_ACCEPT;
    }

    /**
     * 租户补材：关闭当前未关闭问题件、逻辑删除旧材料并追加全新影像件。
     *
     * <p>补材不接收人员变更：四类人员保持聚合原值，提交中的人员字段一律忽略且不做一致性校验。
     * 受理尝试次数加 1，状态由 {@code ISSUE} 迁移到 {@code PENDING_ACCEPT}，随后按首次签收链路异步处理材料。
     * 关闭时间与软删除时间使用同一个业务动作时间。</p>
     *
     * @param submitted  新一次提交的理赔申请
     * @param batchNo    本次提交的材料批次编号
     * @param operatedAt 本次业务动作时间
     */
    public void supplement(ClaimApplication submitted, String batchNo, LocalDateTime operatedAt) {
        AssertUtil.notNull(operatedAt, "租户补材时间不能为空");
        List<ClaimApplicationIssue> openIssues = openIssues();
        if (status != ClaimApplicationStatus.ISSUE || openIssues.isEmpty()) {
            throw new DomainException(ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID,
                    "申请状态不合法，无法受理租户补材，claimApplicationNo=" + claimApplicationNo
                            + "，当前状态=" + status + "，未关闭问题件数=" + openIssues.size());
        }
        List<Material> adoptedMaterials = adoptMaterials(submitted, batchNo);
        openIssues.forEach(issue -> issue.close(SUPPLEMENT_AUDITOR, operatedAt));
        softDeleteCurrentMaterials(operatedAt);
        materials.addAll(adoptedMaterials);
        this.claimApplicationAttempt = claimApplicationAttempt + 1;
        status = ClaimApplicationStatus.PENDING_ACCEPT;
    }

    /**
     * 应用一批材料处理结果，并依据全部材料状态决定受理成功或失败。
     *
     * <p>结果必须与当前全部待处理材料一一对应：不得重复、缺失、包含未知或非待处理材料，
     * 避免部分结果导致申请提前受理成功。</p>
     *
     * @param results 本次材料处理结果
     */
    public void applyMaterialResults(List<MaterialProcessingResult> results) {
        ensureStatus(ClaimApplicationStatus.PENDING_ACCEPT, "应用材料处理结果");
        List<Material> pendingMaterials = pendingMaterials();
        validateResults(results, pendingMaterials);
        for (MaterialProcessingResult result : results) {
            Material material = pendingMaterials.stream()
                    .filter(candidate -> result.materialId().equals(candidate.getId()))
                    .findFirst()
                    .orElseThrow();
            material.applyProcessResult(result.succeeded(), result.uploadedFileUrl());
        }
        boolean allProcessed = materials.stream()
                .allMatch(material -> material.getStatus() == MaterialStatus.PROCESSED);
        status = allProcessed ? ClaimApplicationStatus.ACCEPTED : ClaimApplicationStatus.ACCEPT_FAILED;
    }

    /**
     * 存在有效保单，进入等待审核签收。
     */
    public void waitAudit() {
        transitionTo(ClaimApplicationStatus.ACCEPTED, ClaimApplicationStatus.WAIT_AUDIT);
    }

    /**
     * 不存在有效保单，进入自动承保处理中。
     */
    public void startUnderwriting() {
        transitionTo(ClaimApplicationStatus.ACCEPTED, ClaimApplicationStatus.UNDERWRITING);
    }

    /**
     * 自动承保完成，进入等待审核签收。
     */
    public void finishUnderwriting() {
        transitionTo(ClaimApplicationStatus.UNDERWRITING, ClaimApplicationStatus.WAIT_AUDIT);
    }

    /**
     * 审核系统完成签收，申请进入终态并记录完成时间。
     *
     * <p>只有等待审核的申请可以完成；存在未关闭问题件说明状态已漂移，拒绝完成，
     * 避免带着问题件进入终态。完成时间由应用层按业务时区提供。</p>
     *
     * @param completedAt 审核完成时间
     */
    public void completeAudit(LocalDateTime completedAt) {
        AssertUtil.notNull(completedAt, "审核完成时间不能为空");
        ensureStatus(ClaimApplicationStatus.WAIT_AUDIT, "完成审核签收");
        if (hasOpenIssue()) {
            throw new DomainException(ClaimApplicationErrorCode.CLAIM_APPLICATION_STATUS_INVALID,
                    "存在未关闭问题件，不允许完成审核签收，claimApplicationNo=" + claimApplicationNo);
        }
        status = ClaimApplicationStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    /**
     * 审核系统下发问题件，申请挂起等待租户补材。
     *
     * @param issueNo      问题件编号
     * @param reason       问题原因
     * @param raisedBy     问题件下发人
     * @param issueContent 问题详情 JSON
     */
    public void raiseIssue(String issueNo, String reason, String raisedBy, String issueContent) {
        if (status != ClaimApplicationStatus.WAIT_AUDIT) {
            throw new DomainException(ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID,
                    "申请状态不合法，无法下发问题件，claimApplicationNo=" + claimApplicationNo
                            + "，当前状态=" + status);
        }
        if (!openIssues().isEmpty()) {
            throw new DomainException(ClaimApplicationIssueErrorCode.ISSUE_ALREADY_OPEN,
                    "同一申请已有未关闭问题件，claimApplicationNo=" + claimApplicationNo);
        }
        issues.add(ClaimApplicationIssue.create(issueNo, reason, raisedBy, issueContent));
        status = ClaimApplicationStatus.ISSUE;
    }

    /**
     * 关闭指定问题件；已关闭的问题件直接按幂等返回 {@code false}，不校验申请状态。
     *
     * @param issueNo  问题件编号
     * @param closedBy 问题件关闭人
     * @param closedAt 问题件关闭时间
     * @return 本次是否真正关闭了问题件
     */
    public boolean closeIssue(String issueNo, String closedBy, LocalDateTime closedAt) {
        AssertUtil.notNull(closedAt, "问题件关闭时间不能为空");
        ClaimApplicationIssue issue = issues.stream()
                .filter(candidate -> candidate.getIssueNo().equals(issueNo))
                .findFirst()
                .orElseThrow(() -> new DomainException(ClaimApplicationIssueErrorCode.ISSUE_NOT_FOUND,
                        "问题件不存在，claimApplicationNo=" + claimApplicationNo + "，issueNo=" + issueNo));
        if (issue.isClosed()) {
            return false;
        }
        ensureStatus(ClaimApplicationStatus.ISSUE, "关闭未关闭问题件");
        issue.close(closedBy, closedAt);
        status = ClaimApplicationStatus.WAIT_AUDIT;
        return true;
    }

    /**
     * 判断申请是否处于等待审核，下游据此决定能否下发问题件或交付审核。
     *
     * @return 状态为 WAIT_AUDIT 时为 true
     */
    public boolean isAwaitingAudit() {
        return status == ClaimApplicationStatus.WAIT_AUDIT;
    }

    /**
     * 判断申请是否已完成审核签收。
     *
     * @return 状态为 COMPLETED 时为 true
     */
    public boolean isCompleted() {
        return status == ClaimApplicationStatus.COMPLETED;
    }

    /**
     * 判断申请当前是否存在未关闭问题件。
     *
     * @return 存在未关闭问题件时为 true
     */
    public boolean hasOpenIssue() {
        return !openIssues().isEmpty();
    }

    /**
     * 判断申请是否可被租户重新提交。
     *
     * <p>只有受理失败的申请允许复用原平台编号重新签收，其余状态一律按重复请求复用。</p>
     *
     * @return 状态为 ACCEPT_FAILED 时为 true
     */
    public boolean isReapplicable() {
        return status == ClaimApplicationStatus.ACCEPT_FAILED;
    }

    /**
     * 判断申请是否可由租户补材。
     *
     * @return 状态为 ISSUE 且存在未关闭问题件时为 true
     */
    public boolean isSupplementable() {
        return status == ClaimApplicationStatus.ISSUE && !openIssues().isEmpty();
    }

    /**
     * 判断申请是否已签收但材料尚未处理完成。
     *
     * @return 状态为 PENDING_ACCEPT 时为 true
     */
    public boolean isPendingAccept() {
        return status == ClaimApplicationStatus.PENDING_ACCEPT;
    }

    /**
     * 判断申请是否已通过签收，可以进入承保分流。
     *
     * @return 状态为 ACCEPTED 时为 true
     */
    public boolean isAccepted() {
        return status == ClaimApplicationStatus.ACCEPTED;
    }

    /**
     * 判断申请是否处于自动承保处理中。
     *
     * @return 状态为 UNDERWRITING 时为 true
     */
    public boolean isUnderwriting() {
        return status == ClaimApplicationStatus.UNDERWRITING;
    }

    /**
     * 读取主被保险人证件号，供保单查询与自动承保使用。
     *
     * @return 主被保险人证件号
     */
    public String primaryInsuredCertificateNo() {
        if (primaryInsured == null || primaryInsured.certificateNo() == null
                || primaryInsured.certificateNo().isBlank()) {
            throw new IllegalStateException("主被保险人缺少证件号，claimApplicationNo=" + claimApplicationNo);
        }
        return primaryInsured.certificateNo();
    }

    private List<Material> adoptMaterials(ClaimApplication submitted, String batchNo) {
        AssertUtil.notNull(submitted, "重新提交申请不能为空，claimApplicationNo=" + claimApplicationNo);
        AssertUtil.notEmpty(submitted.getMaterials(), "重新提交至少需要一项材料，claimApplicationNo=" + claimApplicationNo);
        AssertUtil.notBlank(batchNo, "材料批次编号不能为空，claimApplicationNo=" + claimApplicationNo);
        for (Material material : submitted.getMaterials()) {
            if (!batchNo.equals(material.getBatchNo())) {
                throw new IllegalStateException("材料批次编号不一致，claimApplicationNo=" + claimApplicationNo
                        + "，期望批次=" + batchNo + "，实际批次=" + material.getBatchNo());
            }
        }
        return submitted.getMaterials();
    }

    private void softDeleteCurrentMaterials(LocalDateTime deletedAt) {
        materials.forEach(material -> material.softDelete(deletedAt));
    }

    private List<Material> pendingMaterials() {
        return materials.stream().filter(material -> material.getStatus() == MaterialStatus.PENDING_PROCESS).toList();
    }

    private List<ClaimApplicationIssue> openIssues() {
        return issues.stream().filter(ClaimApplicationIssue::isOpen).toList();
    }

    private void validateResults(List<MaterialProcessingResult> results, List<Material> pendingMaterials) {
        AssertUtil.notNull(results, "材料处理结果不能为空，claimApplicationNo=" + claimApplicationNo);
        Set<Long> pendingMaterialIds = new HashSet<>();
        pendingMaterials.forEach(material -> pendingMaterialIds.add(material.getId()));
        Set<Long> resultMaterialIds = new HashSet<>();
        for (MaterialProcessingResult result : results) {
            if (!resultMaterialIds.add(result.materialId())) {
                throw new DomainException(ClaimApplicationErrorCode.CLAIM_APPLICATION_MATERIAL_RESULT_INVALID,
                        "材料处理结果重复，claimApplicationNo=" + claimApplicationNo
                                + "，materialId=" + result.materialId());
            }
            if (!pendingMaterialIds.contains(result.materialId())) {
                throw new DomainException(ClaimApplicationErrorCode.CLAIM_APPLICATION_MATERIAL_RESULT_INVALID,
                        "材料处理结果包含未知或非待处理材料，claimApplicationNo=" + claimApplicationNo
                                + "，materialId=" + result.materialId());
            }
        }
        if (resultMaterialIds.size() != pendingMaterialIds.size()) {
            Set<Long> missingMaterialIds = new HashSet<>(pendingMaterialIds);
            missingMaterialIds.removeAll(resultMaterialIds);
            throw new DomainException(ClaimApplicationErrorCode.CLAIM_APPLICATION_MATERIAL_RESULT_INVALID,
                    "材料处理结果缺失待处理材料，claimApplicationNo=" + claimApplicationNo
                            + "，缺失材料=" + missingMaterialIds);
        }
    }

    private void ensureStatus(ClaimApplicationStatus expectedStatus, String action) {
        if (status != expectedStatus) {
            throw new DomainException(ClaimApplicationErrorCode.CLAIM_APPLICATION_STATUS_INVALID,
                    "申请状态不允许" + action + "，claimApplicationNo=" + claimApplicationNo
                            + "，期望状态=" + expectedStatus + "，实际状态=" + status);
        }
    }

    private void transitionTo(ClaimApplicationStatus expectedStatus, ClaimApplicationStatus targetStatus) {
        ensureStatus(expectedStatus, "迁移");
        status = targetStatus;
    }
}
