package com.wangguangwu.claim.application.domain.claim.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wangguangwu.claim.application.domain.claim.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 问题件领域模型单元测试：验证创建与关闭行为。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@DisplayName("问题件领域模型")
class ClaimApplicationIssueTest {

    private static final String ISSUE_NO = "ISS202610310001";
    private static final String REASON = "缺少病历首页";
    private static final String AUDITOR = "auditor_zhangsan";
    private static final String ISSUE_CONTENT = "{\"missingMaterials\":[\"病历首页\"]}";

    @Test
    @DisplayName("创建问题件：状态为 OPEN")
    void create_initializesStatusAsOpen() {
        ClaimApplicationIssue issue = ClaimApplicationIssue.create(ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT);

        assertThat(issue.getIssueNo()).isEqualTo(ISSUE_NO);
        assertThat(issue.getReason()).isEqualTo(REASON);
        assertThat(issue.getAuditor()).isEqualTo(AUDITOR);
        assertThat(issue.getIssueContent()).isEqualTo(ISSUE_CONTENT);
        assertThat(issue.getStatus()).isEqualTo(ClaimApplicationIssueStatus.OPEN);
        assertThat(issue.isOpen()).isTrue();
        assertThat(issue.isClosed()).isFalse();
        assertThat(issue.getClosedAt()).isNull();
    }

    @Test
    @DisplayName("关闭问题件：状态变更为 CLOSED，记录 closedAt")
    void close_transitionsStatusToClosedAndRecordsClosedAt() {
        ClaimApplicationIssue issue = ClaimApplicationIssue.create(ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT);

        issue.close("auditor_lisi");

        assertThat(issue.getStatus()).isEqualTo(ClaimApplicationIssueStatus.CLOSED);
        assertThat(issue.getAuditor()).isEqualTo("auditor_lisi");
        assertThat(issue.getClosedAt()).isNotNull();
        assertThat(issue.isOpen()).isFalse();
        assertThat(issue.isClosed()).isTrue();
    }

    @Test
    @DisplayName("重复关闭：抛异常")
    void close_onAlreadyClosedIssue_throwsException() {
        ClaimApplicationIssue issue = ClaimApplicationIssue.create(ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT);
        issue.close("auditor_lisi");

        assertThatThrownBy(() -> issue.close("auditor_wangwu"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("问题件状态不允许关闭")
                .hasMessageContaining(ISSUE_NO)
                .extracting("errorCode")
                .isEqualTo(ClaimApplicationIssueErrorCode.ISSUE_STATUS_INVALID);
    }

    @Test
    @DisplayName("创建时参数校验：问题件编号为空")
    void create_withBlankIssueNo_throwsException() {
        assertThatThrownBy(() -> ClaimApplicationIssue.create("", REASON, AUDITOR, ISSUE_CONTENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("问题件编号不能为空");
    }

    @Test
    @DisplayName("创建时参数校验：问题原因超长")
    void create_withReasonExceedingMaxLength_throwsException() {
        String longReason = "a".repeat(513);
        assertThatThrownBy(() -> ClaimApplicationIssue.create(ISSUE_NO, longReason, AUDITOR, ISSUE_CONTENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("问题原因不能超过 512 字符");
    }

    @Test
    @DisplayName("创建时参数校验：审核人员为空")
    void create_withBlankAuditor_throwsException() {
        assertThatThrownBy(() -> ClaimApplicationIssue.create(ISSUE_NO, REASON, "", ISSUE_CONTENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("审核人员不能为空");
    }

    @Test
    @DisplayName("创建时参数校验：问题详情为空")
    void create_withNullIssueContent_throwsException() {
        assertThatThrownBy(() -> ClaimApplicationIssue.create(ISSUE_NO, REASON, AUDITOR, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("问题详情不能为空");
    }

    @Test
    @DisplayName("关闭时参数校验：处理人为空")
    void close_withBlankAuditor_throwsException() {
        ClaimApplicationIssue issue = ClaimApplicationIssue.create(ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT);

        assertThatThrownBy(() -> issue.close(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("处理人不能为空");
    }
}
