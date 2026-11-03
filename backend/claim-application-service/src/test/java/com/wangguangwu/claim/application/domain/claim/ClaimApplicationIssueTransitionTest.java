package com.wangguangwu.claim.application.domain.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationErrorCode;
import com.wangguangwu.claim.application.domain.claim.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.claim.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.claim.material.Material;
import com.wangguangwu.claim.application.domain.claim.material.MaterialProcessingResult;
import com.wangguangwu.claim.application.domain.claim.material.MaterialStatus;
import com.wangguangwu.claim.application.domain.claim.person.AccidentPerson;
import com.wangguangwu.claim.application.domain.claim.person.Applicant;
import com.wangguangwu.claim.application.domain.claim.person.Gender;
import com.wangguangwu.claim.application.domain.claim.person.Payee;
import com.wangguangwu.claim.application.domain.claim.person.PrimaryInsured;
import com.wangguangwu.claim.application.domain.exception.DomainException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 理赔申请聚合根单元测试：验证状态机、材料结果、问题件与重新提交规则。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@DisplayName("理赔申请聚合根")
class ClaimApplicationIssueTransitionTest {

    private static final String TENANT_CODE = "tenant-a";
    private static final String EXTERNAL_NO = "EXT-20261031-001";
    private static final String CLAIM_APPLICATION_NO = "A2026103100000001";
    private static final String BATCH_NO = "B2026110300000001";
    private static final long MATERIAL_ID = 10L;

    @Test
    @DisplayName("下发问题件：等待审核迁移到问题件并追加未关闭问题件")
    void raiseIssue_fromAwaitingAudit_succeeds() {
        ClaimApplication application = persisted(ClaimApplicationStatus.WAIT_AUDIT, List.of(pendingMaterial()),
                List.of());

        application.raiseIssue("ISSUE-001", "缺少发票", "auditor-1", "{}");

        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.ISSUE);
        assertThat(application.getIssues()).hasSize(1);
        assertThat(application.getIssues().get(0).isOpen()).isTrue();
        assertThat(application.isSupplementable()).isTrue();
    }

    @Test
    @DisplayName("下发问题件：非等待审核状态被拒绝")
    void raiseIssue_fromPendingAccept_throwsDomainException() {
        ClaimApplication application = persisted(ClaimApplicationStatus.PENDING_ACCEPT, List.of(pendingMaterial()),
                List.of());

        assertThatThrownBy(() -> application.raiseIssue("ISSUE-001", "缺少发票", "auditor-1", "{}"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("当前状态=PENDING_ACCEPT")
                .extracting("errorCode")
                .isEqualTo(ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID);
    }

    @Test
    @DisplayName("下发问题件：已有未关闭问题件时被拒绝")
    void raiseIssue_withOpenIssue_throwsDomainException() {
        ClaimApplication application = persisted(ClaimApplicationStatus.WAIT_AUDIT, List.of(pendingMaterial()),
                List.of(openIssue()));

        assertThatThrownBy(() -> application.raiseIssue("ISSUE-002", "缺少发票", "auditor-1", "{}"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode")
                .isEqualTo(ClaimApplicationIssueErrorCode.ISSUE_ALREADY_OPEN);
    }

    @Test
    @DisplayName("关闭问题件：未关闭问题件关闭后恢复等待审核")
    void closeIssue_openIssue_succeeds() {
        ClaimApplication application = persisted(ClaimApplicationStatus.ISSUE, List.of(pendingMaterial()),
                List.of(openIssue()));

        boolean closed = application.closeIssue("ISSUE-001", "auditor-2");

        assertThat(closed).isTrue();
        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.WAIT_AUDIT);
        assertThat(application.getIssues().get(0).isClosed()).isTrue();
    }

    @Test
    @DisplayName("关闭问题件：已关闭问题件幂等返回 false 且不校验申请状态")
    void closeIssue_closedIssue_returnsFalse() {
        ClaimApplicationIssue closedIssue = openIssue();
        closedIssue.close("auditor-1");
        ClaimApplication application = persisted(ClaimApplicationStatus.WAIT_AUDIT, List.of(pendingMaterial()),
                List.of(closedIssue));

        boolean closed = application.closeIssue("ISSUE-001", "auditor-2");

        assertThat(closed).isFalse();
        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.WAIT_AUDIT);
    }

    @Test
    @DisplayName("关闭问题件：问题件不存在抛出业务异常")
    void closeIssue_missingIssue_throwsDomainException() {
        ClaimApplication application = persisted(ClaimApplicationStatus.ISSUE, List.of(pendingMaterial()),
                List.of(openIssue()));

        assertThatThrownBy(() -> application.closeIssue("ISSUE-404", "auditor-2"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode")
                .isEqualTo(ClaimApplicationIssueErrorCode.ISSUE_NOT_FOUND);
    }

    @Test
    @DisplayName("关闭问题件：未关闭问题件要求申请处于问题件状态")
    void closeIssue_openIssueWithWrongStatus_throwsDomainException() {
        ClaimApplication application = persisted(ClaimApplicationStatus.WAIT_AUDIT, List.of(pendingMaterial()),
                List.of(openIssue()));

        assertThatThrownBy(() -> application.closeIssue("ISSUE-001", "auditor-2"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("期望状态=ISSUE")
                .extracting("errorCode")
                .isEqualTo(ClaimApplicationErrorCode.CLAIM_APPLICATION_STATUS_INVALID);
    }

    @Test
    @DisplayName("材料结果：全部成功迁移到已受理")
    void applyMaterialResults_allSucceeded_movesToAccepted() {
        ClaimApplication application = persisted(ClaimApplicationStatus.PENDING_ACCEPT, List.of(pendingMaterial()),
                List.of());

        application.applyMaterialResults(List.of(new MaterialProcessingResult(MATERIAL_ID, true, "https://f/1.pdf")));

        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.ACCEPTED);
        assertThat(application.getMaterials().get(0).getStatus()).isEqualTo(MaterialStatus.PROCESSED);
        assertThat(application.getMaterials().get(0).getUploadedFileUrl()).isEqualTo("https://f/1.pdf");
    }

    @Test
    @DisplayName("材料结果：存在失败迁移到受理失败")
    void applyMaterialResults_withFailure_movesToAcceptFailed() {
        Material secondMaterial = Material.restore(11L, BATCH_NO, "INVOICE", "https://o/2.pdf", null,
                MaterialStatus.PENDING_PROCESS, false, null);
        ClaimApplication application = persisted(ClaimApplicationStatus.PENDING_ACCEPT,
                List.of(pendingMaterial(), secondMaterial), List.of());

        application.applyMaterialResults(List.of(
                new MaterialProcessingResult(MATERIAL_ID, true, "https://f/1.pdf"),
                new MaterialProcessingResult(11L, false, null)));

        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.ACCEPT_FAILED);
        assertThat(application.getMaterials().get(0).getStatus()).isEqualTo(MaterialStatus.PROCESSED);
        assertThat(application.getMaterials().get(1).getStatus()).isEqualTo(MaterialStatus.FAILED);
    }

    @Test
    @DisplayName("材料结果：重复结果被拒绝")
    void applyMaterialResults_duplicateResult_throwsException() {
        ClaimApplication application = persisted(ClaimApplicationStatus.PENDING_ACCEPT, List.of(pendingMaterial()),
                List.of());

        assertThatThrownBy(() -> application.applyMaterialResults(List.of(
                new MaterialProcessingResult(MATERIAL_ID, true, "https://f/1.pdf"),
                new MaterialProcessingResult(MATERIAL_ID, true, "https://f/2.pdf"))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("重复")
                .extracting("errorCode")
                .isEqualTo(ClaimApplicationErrorCode.CLAIM_APPLICATION_MATERIAL_RESULT_INVALID);
    }

    @Test
    @DisplayName("材料结果：未知材料被拒绝")
    void applyMaterialResults_unknownResult_throwsException() {
        ClaimApplication application = persisted(ClaimApplicationStatus.PENDING_ACCEPT, List.of(pendingMaterial()),
                List.of());

        assertThatThrownBy(() -> application.applyMaterialResults(
                List.of(new MaterialProcessingResult(99L, true, "https://f/1.pdf"))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("未知或非待处理材料")
                .extracting("errorCode")
                .isEqualTo(ClaimApplicationErrorCode.CLAIM_APPLICATION_MATERIAL_RESULT_INVALID);
    }

    @Test
    @DisplayName("材料结果：缺失材料被拒绝")
    void applyMaterialResults_missingResult_throwsException() {
        ClaimApplication application = persisted(ClaimApplicationStatus.PENDING_ACCEPT, List.of(pendingMaterial()),
                List.of());

        assertThatThrownBy(() -> application.applyMaterialResults(List.of()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("缺失")
                .extracting("errorCode")
                .isEqualTo(ClaimApplicationErrorCode.CLAIM_APPLICATION_MATERIAL_RESULT_INVALID);
    }

    @Test
    @DisplayName("材料结果：非待受理状态被拒绝")
    void applyMaterialResults_wrongStatus_throwsException() {
        ClaimApplication application = persisted(ClaimApplicationStatus.WAIT_AUDIT, List.of(pendingMaterial()),
                List.of());

        assertThatThrownBy(() -> application.applyMaterialResults(
                List.of(new MaterialProcessingResult(MATERIAL_ID, true, "https://f/1.pdf"))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("期望状态=PENDING_ACCEPT")
                .extracting("errorCode")
                .isEqualTo(ClaimApplicationErrorCode.CLAIM_APPLICATION_STATUS_INVALID);
    }

    @Test
    @DisplayName("租户补材：保留原人员、关闭问题件、替换材料并回到待受理")
    void supplement_keepsPersonsAndReplacesMaterials() {
        ClaimApplication application = persisted(ClaimApplicationStatus.ISSUE, List.of(pendingMaterial()),
                List.of(openIssue()));
        ClaimApplication submitted = submitted("B2026110300000002", "孙七");

        application.supplement(submitted, "B2026110300000002");

        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.PENDING_ACCEPT);
        assertThat(application.getClaimApplicationAttempt()).isEqualTo(2);
        assertThat(application.getApplicant().name()).isEqualTo("张三");
        assertThat(application.getMaterials()).hasSize(2);
        assertThat(application.getMaterials().get(0).isDeleted()).isTrue();
        assertThat(application.getMaterials().get(1).getBatchNo()).isEqualTo("B2026110300000002");
        assertThat(application.getIssues().get(0).isClosed()).isTrue();
        assertThat(application.getIssues().get(0).getAuditor()).isEqualTo(ClaimApplication.SUPPLEMENT_AUDITOR);
    }

    @Test
    @DisplayName("租户补材：非问题件状态被拒绝")
    void supplement_wrongStatus_throwsDomainException() {
        ClaimApplication application = persisted(ClaimApplicationStatus.WAIT_AUDIT, List.of(pendingMaterial()),
                List.of(openIssue()));
        ClaimApplication submitted = submitted("B2026110300000002", "孙七");

        assertThatThrownBy(() -> application.supplement(submitted, "B2026110300000002"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode")
                .isEqualTo(ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID);
    }

    @Test
    @DisplayName("重新申请：替换人员与材料并回到待受理")
    void reapply_replacesPersonsAndMaterials() {
        ClaimApplication application = persisted(ClaimApplicationStatus.ACCEPT_FAILED, List.of(pendingMaterial()),
                List.of());
        ClaimApplication submitted = submitted("B2026110300000002", "孙七");

        application.reapply(submitted, "B2026110300000002");

        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.PENDING_ACCEPT);
        assertThat(application.getClaimApplicationAttempt()).isEqualTo(2);
        assertThat(application.getApplicant().name()).isEqualTo("孙七");
        assertThat(application.getMaterials()).hasSize(2);
        assertThat(application.getMaterials().get(0).isDeleted()).isTrue();
        assertThat(application.getMaterials().get(1).getBatchNo()).isEqualTo("B2026110300000002");
    }

    @Test
    @DisplayName("重新申请：仅受理失败状态允许")
    void reapply_wrongStatus_throwsDomainException() {
        ClaimApplication application = persisted(ClaimApplicationStatus.PENDING_ACCEPT, List.of(pendingMaterial()),
                List.of());
        ClaimApplication submitted = submitted("B2026110300000002", "孙七");

        assertThatThrownBy(() -> application.reapply(submitted, "B2026110300000002"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("仅受理失败的申请允许重新申请")
                .extracting("errorCode")
                .isEqualTo(ClaimApplicationErrorCode.CLAIM_APPLICATION_STATUS_INVALID);
    }

    @Test
    @DisplayName("isReapplicable：只有受理失败允许重新提交")
    void isReapplicable_onlyWhenAcceptFailed() {
        assertThat(persisted(ClaimApplicationStatus.PENDING_ACCEPT, List.of(), List.of()).isReapplicable()).isFalse();
        assertThat(persisted(ClaimApplicationStatus.ACCEPT_FAILED, List.of(), List.of()).isReapplicable()).isTrue();
        assertThat(persisted(ClaimApplicationStatus.WAIT_AUDIT, List.of(), List.of()).isReapplicable()).isFalse();
    }

    @Test
    @DisplayName("聚合封装：材料与问题件集合只读")
    void collectionViews_areImmutable() {
        ClaimApplication application = persisted(ClaimApplicationStatus.ISSUE, List.of(pendingMaterial()),
                List.of(openIssue()));

        assertThatThrownBy(() -> application.getMaterials().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> application.getIssues().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("平台编号：已绑定后不允许重复绑定")
    void bindClaimApplicationNo_whenAlreadyBound_throwsIllegalStateException() {
        ClaimApplication application = ClaimApplication.submit(TENANT_CODE, EXTERNAL_NO,
                new Applicant("张三", Gender.MALE, "身份证", "110101199001011234"),
                new PrimaryInsured("李四", Gender.MALE, "身份证", "110101199002022345"),
                new AccidentPerson("王五", Gender.MALE, "身份证", "110101199003033456"),
                new Payee("赵六", Gender.FEMALE, "身份证", "110101199004044567"),
                List.of(new MaterialSubmission("INVOICE", "https://example.com/invoice.pdf")), BATCH_NO);
        application.bindClaimApplicationNo(CLAIM_APPLICATION_NO);

        assertThatThrownBy(() -> application.bindClaimApplicationNo("A2026103100000002"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不允许重复绑定");
    }

    @Test
    @DisplayName("乐观锁版本：只接受插入版本或保存后的下一版本")
    void bindVersion_acceptsOnlyInitialOrNextVersion() {
        ClaimApplication application = persisted(ClaimApplicationStatus.PENDING_ACCEPT, List.of(pendingMaterial()),
                List.of());
        application.bindVersion(1L);

        assertThat(application.getVersion()).isEqualTo(1L);
        assertThatThrownBy(() -> application.bindVersion(3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("只能回填插入版本或保存后的下一版本");
    }

    @Test
    @DisplayName("提交：四类人员缺失时拒绝创建")
    void submit_withoutApplicant_throwsException() {
        assertThatThrownBy(() -> ClaimApplication.submit(TENANT_CODE, EXTERNAL_NO, null,
                new PrimaryInsured("李四", Gender.MALE, "身份证", "110101199002022345"),
                new AccidentPerson("王五", Gender.MALE, "身份证", "110101199003033456"),
                new Payee("赵六", Gender.FEMALE, "身份证", "110101199004044567"),
                List.of(new MaterialSubmission("INVOICE", "https://example.com/invoice.pdf")), BATCH_NO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("申请人不能为空");
    }

    @Test
    @DisplayName("状态判定：按当前状态返回")
    void statusPredicates_matchCurrentStatus() {
        ClaimApplication pendingAccept = persisted(ClaimApplicationStatus.PENDING_ACCEPT, List.of(), List.of());
        ClaimApplication accepted = persisted(ClaimApplicationStatus.ACCEPTED, List.of(), List.of());
        ClaimApplication underwriting = persisted(ClaimApplicationStatus.UNDERWRITING, List.of(), List.of());

        assertThat(pendingAccept.isPendingAccept()).isTrue();
        assertThat(accepted.isAccepted()).isTrue();
        assertThat(underwriting.isUnderwriting()).isTrue();
        assertThat(accepted.isPendingAccept()).isFalse();
    }

    private ClaimApplication persisted(ClaimApplicationStatus status, List<Material> materials,
                                       List<ClaimApplicationIssue> issues) {
        return ClaimApplication.restore(1L, TENANT_CODE, CLAIM_APPLICATION_NO, EXTERNAL_NO, status, 1,
                new Applicant("张三", Gender.MALE, "身份证", "110101199001011234"),
                new PrimaryInsured("李四", Gender.MALE, "身份证", "110101199002022345"),
                new AccidentPerson("王五", Gender.MALE, "身份证", "110101199003033456"),
                new Payee("赵六", Gender.FEMALE, "身份证", "110101199004044567"),
                0L, materials, issues);
    }

    private ClaimApplication submitted(String batchNo, String applicantName) {
        return ClaimApplication.submit(TENANT_CODE, EXTERNAL_NO,
                new Applicant(applicantName, Gender.MALE, "身份证", "110101199005055678"),
                new PrimaryInsured("周八", Gender.MALE, "身份证", "110101199006066789"),
                new AccidentPerson("吴九", Gender.MALE, "身份证", "110101199007077890"),
                new Payee("郑十", Gender.FEMALE, "身份证", "110101199008088901"),
                List.of(new MaterialSubmission("INVOICE", "https://example.com/invoice.pdf")), batchNo);
    }

    private Material pendingMaterial() {
        return Material.restore(MATERIAL_ID, BATCH_NO, "POLICY", "https://example.com/policy.pdf", null,
                MaterialStatus.PENDING_PROCESS, false, null);
    }

    private ClaimApplicationIssue openIssue() {
        return ClaimApplicationIssue.create("ISSUE-001", "缺少发票", "auditor-1", "{}");
    }
}
