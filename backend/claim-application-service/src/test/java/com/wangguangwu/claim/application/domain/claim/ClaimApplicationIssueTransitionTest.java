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
 * 申请状态流转单元测试：验证问题件相关的状态迁移。
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
    @DisplayName("WAIT_AUDIT → ISSUE：合法流转")
    void transitionToIssue_fromWaitAudit_succeeds() {
        ClaimApplication application = createApplicationInStatus(ClaimApplicationStatus.WAIT_AUDIT);

        application.transitionToIssue();

        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.ISSUE);
    }

    @Test
    @DisplayName("ISSUE → WAIT_AUDIT：合法流转")
    void transitionToWaitAudit_fromIssue_succeeds() {
        ClaimApplication application = createApplicationInStatus(ClaimApplicationStatus.ISSUE);

        application.transitionToWaitAudit();

        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.WAIT_AUDIT);
    }

    @Test
    @DisplayName("isWaitAudit：状态为 WAIT_AUDIT 时返回 true")
    void isWaitAudit_whenStatusIsWaitAudit_returnsTrue() {
        ClaimApplication application = createApplicationInStatus(ClaimApplicationStatus.WAIT_AUDIT);

        assertThat(application.isWaitAudit()).isTrue();
    }

    @Test
    @DisplayName("isWaitAudit：状态不为 WAIT_AUDIT 时返回 false")
    void isWaitAudit_whenStatusIsNotWaitAudit_returnsFalse() {
        ClaimApplication application = createApplicationInStatus(ClaimApplicationStatus.ISSUE);

        assertThat(application.isWaitAudit()).isFalse();
    }

    @Test
    @DisplayName("WAIT_AUDIT → ISSUE 后再 → WAIT_AUDIT：完整流转")
    void fullTransition_waitAudit_to_issue_to_waitAudit_succeeds() {
        ClaimApplication application = createApplicationInStatus(ClaimApplicationStatus.WAIT_AUDIT);

        application.transitionToIssue();
        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.ISSUE);

        application.transitionToWaitAudit();
        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.WAIT_AUDIT);
    }

    @Test
    @DisplayName("非法流转：PENDING_ACCEPT → ISSUE 抛异常")
    void transitionToIssue_fromPendingAccept_throwsException() {
        ClaimApplication application = createApplicationInStatus(ClaimApplicationStatus.PENDING_ACCEPT);

        assertThatThrownBy(application::transitionToIssue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("申请状态不允许迁移")
                .hasMessageContaining("期望状态=WAIT_AUDIT")
                .hasMessageContaining("实际状态=PENDING_ACCEPT");
    }

    @Test
    @DisplayName("非法流转：WAIT_AUDIT → WAIT_AUDIT 抛异常")
    void transitionToWaitAudit_fromWaitAudit_throwsException() {
        ClaimApplication application = createApplicationInStatus(ClaimApplicationStatus.WAIT_AUDIT);

        assertThatThrownBy(application::transitionToWaitAudit)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("申请状态不允许迁移")
                .hasMessageContaining("期望状态=ISSUE")
                .hasMessageContaining("实际状态=WAIT_AUDIT")
                .hasMessageContaining("WAIT_AUDIT");
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
        
        // 通过状态迁移方法达到目标状态
        if (status == ClaimApplicationStatus.PENDING_ACCEPT) {
            // 已经是 PENDING_ACCEPT
        } else if (status == ClaimApplicationStatus.ACCEPTED) {
            application.accept();
        } else if (status == ClaimApplicationStatus.WAIT_AUDIT) {
            application.accept();
            application.waitAudit();
        } else if (status == ClaimApplicationStatus.ISSUE) {
            application.accept();
            application.waitAudit();
            application.transitionToIssue();
        }
        
        return application;
    }
}
