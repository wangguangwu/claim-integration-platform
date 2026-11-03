package com.wangguangwu.claim.application.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationQueryErrorCode;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationIssueStatus;
import com.wangguangwu.claim.application.domain.claim.MaterialStatus;
import com.wangguangwu.claim.application.application.exception.ApplicationException;
import com.wangguangwu.claim.application.application.port.ClaimApplicationDetailQuery;
import com.wangguangwu.claim.application.application.query.model.CaseView;
import com.wangguangwu.claim.application.application.query.model.ClaimApplicationSummary;
import com.wangguangwu.claim.application.application.query.model.ClaimApplicationWithCases;
import com.wangguangwu.claim.application.application.query.model.ClaimApplicationWithIssues;
import com.wangguangwu.claim.application.application.query.model.ClaimApplicationWithMaterials;
import com.wangguangwu.claim.application.application.query.model.IssueView;
import com.wangguangwu.claim.application.application.query.model.MaterialView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 理赔申请详情查询用例单元测试：验证不存在、跨租户、空集合与读模型透传。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@DisplayName("理赔申请详情查询用例")
class ClaimApplicationDetailQueryUseCaseTest {

    private static final String TENANT_CODE = "tenant-a";
    private static final String OTHER_TENANT_CODE = "tenant-b";
    private static final String CLAIM_APPLICATION_NO = "A2026110300000001";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 10, 0);

    private ClaimApplicationDetailQuery claimApplicationDetailQuery;

    private ClaimApplicationDetailQueryUseCase useCase;

    @BeforeEach
    void setUp() {
        claimApplicationDetailQuery = mock(ClaimApplicationDetailQuery.class);
        useCase = new ClaimApplicationDetailQueryUseCase(claimApplicationDetailQuery);
    }

    @Test
    @DisplayName("申请不存在：返回查询不存在")
    void findWithMaterials_missing_throwsNotFound() {
        when(claimApplicationDetailQuery.findWithMaterials(TENANT_CODE, CLAIM_APPLICATION_NO))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.findWithMaterials(TENANT_CODE, CLAIM_APPLICATION_NO))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationQueryErrorCode.CLAIM_APPLICATION_QUERY_NOT_FOUND)
                .hasMessageContaining(CLAIM_APPLICATION_NO);
    }

    @Test
    @DisplayName("跨租户查询：读模型按不存在返回，统一抛出查询不存在")
    void findWithMaterials_otherTenant_throwsNotFound() {
        when(claimApplicationDetailQuery.findWithMaterials(OTHER_TENANT_CODE, CLAIM_APPLICATION_NO))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.findWithMaterials(OTHER_TENANT_CODE, CLAIM_APPLICATION_NO))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationQueryErrorCode.CLAIM_APPLICATION_QUERY_NOT_FOUND);
        verify(claimApplicationDetailQuery).findWithMaterials(OTHER_TENANT_CODE, CLAIM_APPLICATION_NO);
    }

    @Test
    @DisplayName("申请存在但无材料：返回空材料集合")
    void findWithMaterials_noMaterials_returnsEmptyList() {
        ClaimApplicationWithMaterials view = new ClaimApplicationWithMaterials(summary(), List.of());
        when(claimApplicationDetailQuery.findWithMaterials(TENANT_CODE, CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(view));

        ClaimApplicationWithMaterials result = useCase.findWithMaterials(TENANT_CODE, CLAIM_APPLICATION_NO);

        assertThat(result.application().claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        assertThat(result.materials()).isEmpty();
    }

    @Test
    @DisplayName("申请存在但无问题件：返回空问题件集合")
    void findWithIssues_noIssues_returnsEmptyList() {
        when(claimApplicationDetailQuery.findWithIssues(TENANT_CODE, CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(new ClaimApplicationWithIssues(summary(), List.of())));

        ClaimApplicationWithIssues result = useCase.findWithIssues(TENANT_CODE, CLAIM_APPLICATION_NO);

        assertThat(result.issues()).isEmpty();
    }

    @Test
    @DisplayName("申请存在但无案件关联：返回空案件集合")
    void findWithCases_noCases_returnsEmptyList() {
        when(claimApplicationDetailQuery.findWithCases(TENANT_CODE, CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(new ClaimApplicationWithCases(summary(), List.of())));

        ClaimApplicationWithCases result = useCase.findWithCases(TENANT_CODE, CLAIM_APPLICATION_NO);

        assertThat(result.cases()).isEmpty();
    }

    @Test
    @DisplayName("查询材料：读模型内容原样返回")
    void findWithMaterials_existing_returnsView() {
        MaterialView material = new MaterialView("B2026110300000001", "POLICY",
                "https://example.com/policy.pdf", "https://f/policy.pdf", MaterialStatus.PROCESSED, NOW);
        when(claimApplicationDetailQuery.findWithMaterials(TENANT_CODE, CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(new ClaimApplicationWithMaterials(summary(), List.of(material))));

        ClaimApplicationWithMaterials result = useCase.findWithMaterials(TENANT_CODE, CLAIM_APPLICATION_NO);

        assertThat(result.materials()).containsExactly(material);
    }

    @Test
    @DisplayName("查询问题件：关闭时间等信息原样返回")
    void findWithIssues_existing_returnsView() {
        IssueView issue = new IssueView("ISSUE-001", "缺少发票", "auditor-1", "{}",
                ClaimApplicationIssueStatus.CLOSED, "auditor-2", NOW, NOW);
        when(claimApplicationDetailQuery.findWithIssues(TENANT_CODE, CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(new ClaimApplicationWithIssues(summary(), List.of(issue))));

        ClaimApplicationWithIssues result = useCase.findWithIssues(TENANT_CODE, CLAIM_APPLICATION_NO);

        assertThat(result.issues()).containsExactly(issue);
    }

    @Test
    @DisplayName("查询案件关联：案件号与创建时间原样返回")
    void findWithCases_existing_returnsView() {
        CaseView caseView = new CaseView("CASE-001", NOW);
        when(claimApplicationDetailQuery.findWithCases(TENANT_CODE, CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(new ClaimApplicationWithCases(summary(), List.of(caseView))));

        ClaimApplicationWithCases result = useCase.findWithCases(TENANT_CODE, CLAIM_APPLICATION_NO);

        assertThat(result.cases()).containsExactly(caseView);
    }

    private ClaimApplicationSummary summary() {
        return new ClaimApplicationSummary(TENANT_CODE, CLAIM_APPLICATION_NO, "EXT-20261103-001",
                ClaimApplicationStatus.WAIT_AUDIT, 1, NOW, NOW, null);
    }
}
