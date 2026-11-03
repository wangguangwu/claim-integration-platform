package com.wangguangwu.claim.application.infrastructure.query.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.issue.ClaimApplicationIssueStatus;
import com.wangguangwu.claim.application.domain.claim.material.MaterialStatus;
import com.wangguangwu.claim.application.infrastructure.claim.persistence.ClaimApplicationDO;
import com.wangguangwu.claim.application.infrastructure.claim.persistence.ClaimApplicationDOMapper;
import com.wangguangwu.claim.application.infrastructure.claim.persistence.ClaimApplicationIssueDO;
import com.wangguangwu.claim.application.infrastructure.claim.persistence.ClaimApplicationIssueDOMapper;
import com.wangguangwu.claim.application.infrastructure.claim.persistence.ClaimCaseRelationDOMapper;
import com.wangguangwu.claim.application.infrastructure.claim.persistence.MaterialDO;
import com.wangguangwu.claim.application.infrastructure.claim.persistence.MaterialDOMapper;
import com.wangguangwu.claim.application.usecase.query.model.ClaimApplicationSummary;
import com.wangguangwu.claim.application.usecase.query.model.ClaimApplicationWithIssues;
import com.wangguangwu.claim.application.usecase.query.model.ClaimApplicationWithMaterials;
import com.wangguangwu.claim.application.usecase.query.model.IssueView;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 详情读模型单元测试：验证完成时间、问题件下发人与关闭人的映射及空值处理。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@DisplayName("详情读模型映射")
class MyBatisClaimApplicationDetailQueryTest {

    private static final String TENANT_CODE = "tenant-a";
    private static final String CLAIM_APPLICATION_NO = "A2026110300000001";
    private static final String EXTERNAL_NO = "EXT-20261103-001";
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 11, 3, 12, 0);
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 3, 10, 0);

    private ClaimApplicationDOMapper claimApplicationMapper;
    private MaterialDOMapper materialMapper;
    private ClaimApplicationIssueDOMapper issueMapper;
    private ClaimCaseRelationDOMapper caseRelationMapper;

    private MyBatisClaimApplicationDetailQuery detailQuery;

    @BeforeEach
    void setUp() {
        claimApplicationMapper = mock(ClaimApplicationDOMapper.class);
        materialMapper = mock(MaterialDOMapper.class);
        issueMapper = mock(ClaimApplicationIssueDOMapper.class);
        caseRelationMapper = mock(ClaimCaseRelationDOMapper.class);

        detailQuery = new MyBatisClaimApplicationDetailQuery(claimApplicationMapper, materialMapper, issueMapper,
                caseRelationMapper);
    }

    @Test
    @DisplayName("已完成申请：摘要带出完成时间，问题件带出下发人与关闭人")
    void findWithIssues_completedApplication_mapsCompletionAndIssueActors() {
        when(claimApplicationMapper.selectOne(any())).thenReturn(application(ClaimApplicationStatus.COMPLETED,
                COMPLETED_AT));
        when(issueMapper.selectList(any())).thenReturn(List.of(
                issue("ISSUE-001", ClaimApplicationIssueStatus.OPEN, null, null),
                issue("ISSUE-002", ClaimApplicationIssueStatus.CLOSED, "auditor-2", COMPLETED_AT)));

        ClaimApplicationWithIssues result = detailQuery.findWithIssues(TENANT_CODE, CLAIM_APPLICATION_NO).orElseThrow();

        ClaimApplicationSummary summary = result.application();
        assertThat(summary.status()).isEqualTo(ClaimApplicationStatus.COMPLETED);
        assertThat(summary.completedAt()).isEqualTo(COMPLETED_AT);
        assertThat(result.issues()).hasSize(2);
        IssueView openIssue = result.issues().getFirst();
        assertThat(openIssue.issueNo()).isEqualTo("ISSUE-001");
        assertThat(openIssue.raisedBy()).isEqualTo("auditor-1");
        assertThat(openIssue.closedBy()).isNull();
        assertThat(openIssue.closedAt()).isNull();
        IssueView closedIssue = result.issues().getLast();
        assertThat(closedIssue.issueNo()).isEqualTo("ISSUE-002");
        assertThat(closedIssue.raisedBy()).isEqualTo("auditor-1");
        assertThat(closedIssue.closedBy()).isEqualTo("auditor-2");
        assertThat(closedIssue.closedAt()).isEqualTo(COMPLETED_AT);
    }

    @Test
    @DisplayName("等待审核申请：摘要完成时间为空")
    void findWithMaterials_waitAuditApplication_mapsNullCompletionTime() {
        when(claimApplicationMapper.selectOne(any())).thenReturn(application(ClaimApplicationStatus.WAIT_AUDIT, null));
        when(materialMapper.selectList(any())).thenReturn(List.of(MaterialDO.builder()
                .batchNo("B2026110300000001").materialType("INVOICE")
                .originalFileUrl("https://example.com/invoice.pdf")
                .uploadedFileUrl("https://files/invoice.pdf")
                .status(MaterialStatus.PROCESSED.name()).createdAt(CREATED_AT).build()));

        ClaimApplicationWithMaterials result = detailQuery.findWithMaterials(TENANT_CODE, CLAIM_APPLICATION_NO)
                .orElseThrow();

        assertThat(result.application().status()).isEqualTo(ClaimApplicationStatus.WAIT_AUDIT);
        assertThat(result.application().completedAt()).isNull();
        assertThat(result.materials()).hasSize(1);
        assertThat(result.materials().getFirst().status()).isEqualTo(MaterialStatus.PROCESSED);
    }

    private ClaimApplicationDO application(ClaimApplicationStatus status, LocalDateTime completedAt) {
        return ClaimApplicationDO.builder().tenantCode(TENANT_CODE).claimApplicationNo(CLAIM_APPLICATION_NO)
                .externalClaimApplicationNo(EXTERNAL_NO).status(status.name()).claimApplicationAttempt(1)
                .completedAt(completedAt).createdAt(CREATED_AT).updatedAt(CREATED_AT).build();
    }

    private ClaimApplicationIssueDO issue(String issueNo, ClaimApplicationIssueStatus status, String closedBy,
                                          LocalDateTime closedAt) {
        return ClaimApplicationIssueDO.builder().claimApplicationNo(CLAIM_APPLICATION_NO).issueNo(issueNo)
                .reason("缺少发票").raisedBy("auditor-1").closedBy(closedBy).issueContentJson("{}")
                .status(status.name()).isOpen(status == ClaimApplicationIssueStatus.OPEN ? 1 : 0)
                .closedAt(closedAt).createdAt(CREATED_AT).updatedAt(CREATED_AT).build();
    }
}
