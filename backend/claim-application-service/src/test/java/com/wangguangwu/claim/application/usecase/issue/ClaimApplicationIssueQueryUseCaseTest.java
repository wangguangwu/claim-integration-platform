package com.wangguangwu.claim.application.usecase.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.claimcase.port.ClaimCaseRelationRepository;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.issue.port.ClaimApplicationIssueRepository;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssueStatus;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.shared.contract.BusinessException;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueQueryResult;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 问题件查询用例单元测试：验证案件反查与三种查询结果。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@DisplayName("问题件查询用例")
class ClaimApplicationIssueQueryUseCaseTest {

    private static final String TENANT_CODE = "tenant-a";
    private static final String CASE_NO = "CASE-2026-0001";
    private static final String CLAIM_APPLICATION_NO = "A2026110200000001";
    private static final LocalDateTime RAISED_AT = LocalDateTime.of(2026, 11, 2, 10, 5);

    private ClaimCaseRelationRepository claimCaseRelationRepository;
    private ClaimApplicationIssueRepository claimApplicationIssueRepository;

    private ClaimApplicationIssueQueryUseCase useCase;

    @BeforeEach
    void setUp() {
        claimCaseRelationRepository = mock(ClaimCaseRelationRepository.class);
        claimApplicationIssueRepository = mock(ClaimApplicationIssueRepository.class);

        useCase = new ClaimApplicationIssueQueryUseCase(claimCaseRelationRepository, claimApplicationIssueRepository);
    }

    @Test
    @DisplayName("存在未关闭问题件：返回问题件编号、原因与下发时间")
    void queryByCaseNo_openIssue_returnsIssueDetails() {
        when(claimCaseRelationRepository.findClaimApplicationNo(TENANT_CODE, CASE_NO))
                .thenReturn(Optional.of(CLAIM_APPLICATION_NO));
        when(claimApplicationIssueRepository.findOpenByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(openIssue()));

        ClaimApplicationIssueQueryResult result = useCase.queryByCaseNo(TENANT_CODE, CASE_NO);

        assertThat(result.claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        assertThat(result.caseNo()).isEqualTo(CASE_NO);
        assertThat(result.hasOpenIssue()).isTrue();
        assertThat(result.issueNo()).isEqualTo("ISSUE-2026-0001");
        assertThat(result.reason()).isEqualTo("医疗票据缺失");
        assertThat(result.raisedAt()).isEqualTo(RAISED_AT);
    }

    @Test
    @DisplayName("无未关闭问题件：hasOpenIssue 为 false，问题件字段为空")
    void queryByCaseNo_noOpenIssue_returnsEmptyIssueFields() {
        when(claimCaseRelationRepository.findClaimApplicationNo(TENANT_CODE, CASE_NO))
                .thenReturn(Optional.of(CLAIM_APPLICATION_NO));
        when(claimApplicationIssueRepository.findOpenByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.empty());

        ClaimApplicationIssueQueryResult result = useCase.queryByCaseNo(TENANT_CODE, CASE_NO);

        assertThat(result.claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        assertThat(result.hasOpenIssue()).isFalse();
        assertThat(result.issueNo()).isNull();
        assertThat(result.reason()).isNull();
        assertThat(result.raisedAt()).isNull();
    }

    @Test
    @DisplayName("案件关系不存在：抛 CASE_RELATION_NOT_FOUND，不查询问题件")
    void queryByCaseNo_caseRelationMissing_throwsBusinessException() {
        when(claimCaseRelationRepository.findClaimApplicationNo(TENANT_CODE, CASE_NO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.queryByCaseNo(TENANT_CODE, CASE_NO))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.CASE_RELATION_NOT_FOUND)
                .hasMessageContaining(CASE_NO);

        verify(claimApplicationIssueRepository, never()).findOpenByClaimApplicationNo(anyString());
        verify(claimApplicationIssueRepository, never()).insert(any());
    }

    private ClaimApplicationIssue openIssue() {
        return ClaimApplicationIssue.restore(null, CLAIM_APPLICATION_NO, "ISSUE-2026-0001", "医疗票据缺失",
                "auditor-01", "{}", ClaimApplicationIssueStatus.OPEN, null, RAISED_AT, RAISED_AT);
    }
}
