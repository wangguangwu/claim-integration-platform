package com.wangguangwu.claim.application.domain.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wangguangwu.claim.application.domain.claim.person.AccidentPerson;
import com.wangguangwu.claim.application.domain.claim.person.Applicant;
import com.wangguangwu.claim.application.domain.claim.person.Gender;
import com.wangguangwu.claim.application.domain.claim.person.Payee;
import com.wangguangwu.claim.application.domain.claim.person.PrimaryInsured;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 申请状态流转单元测试：验证问题件相关的状态迁移与业务语义判定。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@DisplayName("申请状态流转")
class ClaimApplicationIssueTransitionTest {

    private static final String TENANT_CODE = "tenant-a";
    private static final String EXTERNAL_NO = "EXT-20261031-001";
    private static final String CLAIM_APPLICATION_NO = "A2026103100000001";

    @Test
    @DisplayName("等待审核 → 进入问题件：合法流转")
    void enterIssue_fromAwaitingAudit_succeeds() {
        ClaimApplication application = createApplicationInStatus(ClaimApplicationStatus.WAIT_AUDIT);

        application.enterIssue();

        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.ISSUE);
    }

    @Test
    @DisplayName("问题件 → 恢复审核：合法流转")
    void resumeAudit_fromIssue_succeeds() {
        ClaimApplication application = createApplicationInStatus(ClaimApplicationStatus.ISSUE);

        application.resumeAudit();

        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.WAIT_AUDIT);
    }

    @Test
    @DisplayName("isAwaitingAudit：状态为 WAIT_AUDIT 时返回 true")
    void isAwaitingAudit_whenStatusIsAwaitingAudit_returnsTrue() {
        ClaimApplication application = createApplicationInStatus(ClaimApplicationStatus.WAIT_AUDIT);

        assertThat(application.isAwaitingAudit()).isTrue();
    }

    @Test
    @DisplayName("isAwaitingAudit：状态不为 WAIT_AUDIT 时返回 false")
    void isAwaitingAudit_whenStatusIsIssue_returnsFalse() {
        ClaimApplication application = createApplicationInStatus(ClaimApplicationStatus.ISSUE);

        assertThat(application.isAwaitingAudit()).isFalse();
    }

    @Test
    @DisplayName("完整流转：等待审核 → 问题件 → 恢复审核")
    void fullTransition_awaitingAudit_to_issue_to_awaitingAudit_succeeds() {
        ClaimApplication application = createApplicationInStatus(ClaimApplicationStatus.WAIT_AUDIT);

        application.enterIssue();
        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.ISSUE);

        application.resumeAudit();
        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.WAIT_AUDIT);
    }

    @Test
    @DisplayName("非法流转：待受理 → 问题件 抛异常")
    void enterIssue_fromPendingAccept_throwsException() {
        ClaimApplication application = createApplicationInStatus(ClaimApplicationStatus.PENDING_ACCEPT);

        assertThatThrownBy(application::enterIssue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("申请状态不允许迁移")
                .hasMessageContaining("期望状态=WAIT_AUDIT")
                .hasMessageContaining("实际状态=PENDING_ACCEPT");
    }

    @Test
    @DisplayName("非法流转：等待审核 → 恢复审核 抛异常")
    void resumeAudit_fromAwaitingAudit_throwsException() {
        ClaimApplication application = createApplicationInStatus(ClaimApplicationStatus.WAIT_AUDIT);

        assertThatThrownBy(application::resumeAudit)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("申请状态不允许迁移")
                .hasMessageContaining("期望状态=ISSUE")
                .hasMessageContaining("实际状态=WAIT_AUDIT");
    }

    @Test
    @DisplayName("isReapplicable：只有受理失败允许重新提交")
    void isReapplicable_onlyWhenAcceptFailed() {
        assertThat(createApplicationInStatus(ClaimApplicationStatus.PENDING_ACCEPT).isReapplicable()).isFalse();
        assertThat(createApplicationInStatus(ClaimApplicationStatus.ACCEPT_FAILED).isReapplicable()).isTrue();
        assertThat(createApplicationInStatus(ClaimApplicationStatus.WAIT_AUDIT).isReapplicable()).isFalse();
    }

    @Test
    @DisplayName("isPendingAccept / isAccepted / isUnderwriting：按状态返回")
    void statusPredicates_matchCurrentStatus() {
        ClaimApplication pendingAccept = createApplicationInStatus(ClaimApplicationStatus.PENDING_ACCEPT);
        ClaimApplication accepted = createApplicationInStatus(ClaimApplicationStatus.ACCEPTED);
        ClaimApplication underwriting = createApplicationInStatus(ClaimApplicationStatus.UNDERWRITING);

        assertThat(pendingAccept.isPendingAccept()).isTrue();
        assertThat(accepted.isAccepted()).isTrue();
        assertThat(underwriting.isUnderwriting()).isTrue();
        assertThat(accepted.isPendingAccept()).isFalse();
    }

    private ClaimApplication createApplicationInStatus(ClaimApplicationStatus status) {
        ClaimApplication application = ClaimApplication.submit(
                TENANT_CODE,
                EXTERNAL_NO,
                new Applicant("张三", Gender.MALE, "身份证", "110101199001011234"),
                new PrimaryInsured("李四", Gender.MALE, "身份证", "110101199002022345"),
                new AccidentPerson("王五", Gender.MALE, "身份证", "110101199003033456"),
                new Payee("赵六", Gender.FEMALE, "身份证", "110101199004044567"),
                List.of(new MaterialSubmission("POLICY", "https://example.com/policy.pdf"))
        );
        application.bindClaimApplicationNo(CLAIM_APPLICATION_NO);
        if (status == ClaimApplicationStatus.ACCEPT_FAILED) {
            application.failAccept();
        } else if (status == ClaimApplicationStatus.ACCEPTED) {
            application.accept();
        } else if (status == ClaimApplicationStatus.WAIT_AUDIT) {
            application.accept();
            application.waitAudit();
        } else if (status == ClaimApplicationStatus.UNDERWRITING) {
            application.accept();
            application.startUnderwriting();
        } else if (status == ClaimApplicationStatus.ISSUE) {
            application.accept();
            application.waitAudit();
            application.enterIssue();
        }
        return application;
    }
}
