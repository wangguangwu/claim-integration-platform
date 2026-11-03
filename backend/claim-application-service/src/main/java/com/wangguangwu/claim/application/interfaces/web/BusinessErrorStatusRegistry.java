package com.wangguangwu.claim.application.interfaces.web;

import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationErrorCode;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationQueryErrorCode;
import com.wangguangwu.claim.application.domain.claim.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.shared.contract.BusinessErrorCode;
import com.wangguangwu.claim.application.shared.contract.ClaimApplicationAsyncErrorCode;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 业务错误码到 HTTP 状态码的唯一映射表。
 *
 * <p>错误码由各业务域自行定义，状态码只允许在本类集中决定；未登记的错误码查询时立即失败，
 * 由映射完整性测试在构建阶段暴露，不静默降级为 500。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
public final class BusinessErrorStatusRegistry {

    private static final Map<BusinessErrorCode, HttpStatus> STATUS_BY_ERROR_CODE = buildMappings();

    private BusinessErrorStatusRegistry() {
    }

    /**
     * 查询错误码对应的 HTTP 状态。
     *
     * @param errorCode 业务错误码
     * @return 对应的 HTTP 状态
     */
    public static HttpStatus statusOf(BusinessErrorCode errorCode) {
        HttpStatus status = STATUS_BY_ERROR_CODE.get(errorCode);
        if (status == null) {
            throw new IllegalArgumentException("业务错误码未登记 HTTP 状态：" + errorCode.code());
        }
        return status;
    }

    /**
     * 登记全部业务错误码的状态映射，新增错误码必须同步补充。
     *
     * @return 不可变映射表
     */
    private static Map<BusinessErrorCode, HttpStatus> buildMappings() {
        Map<BusinessErrorCode, HttpStatus> mappings = new HashMap<>();
        mappings.put(ClaimApplicationIntakeErrorCode.TENANT_PROTOCOL_UNSUPPORTED, HttpStatus.BAD_REQUEST);
        mappings.put(ClaimApplicationIntakeErrorCode.TENANT_PROTOCOL_AMBIGUOUS, HttpStatus.BAD_REQUEST);
        mappings.put(ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_INVALID, HttpStatus.BAD_REQUEST);
        mappings.put(ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_BUSY, HttpStatus.SERVICE_UNAVAILABLE);
        mappings.put(ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_SYSTEM_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR);
        mappings.put(ClaimApplicationIntakeErrorCode.AUDIT_DELIVERY_APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND);

        mappings.put(ClaimApplicationErrorCode.CLAIM_APPLICATION_STATUS_INVALID, HttpStatus.CONFLICT);
        mappings.put(ClaimApplicationErrorCode.CLAIM_APPLICATION_MATERIAL_RESULT_INVALID, HttpStatus.BAD_REQUEST);
        mappings.put(ClaimApplicationErrorCode.CLAIM_APPLICATION_MATERIAL_STATUS_INVALID, HttpStatus.CONFLICT);

        mappings.put(ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        mappings.put(ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID, HttpStatus.BAD_REQUEST);
        mappings.put(ClaimApplicationIssueErrorCode.ISSUE_STATUS_INVALID, HttpStatus.CONFLICT);
        mappings.put(ClaimApplicationIssueErrorCode.ISSUE_ALREADY_OPEN, HttpStatus.CONFLICT);
        mappings.put(ClaimApplicationIssueErrorCode.ISSUE_NOT_FOUND, HttpStatus.NOT_FOUND);
        mappings.put(ClaimApplicationIssueErrorCode.ISSUE_INTAKE_INVALID, HttpStatus.BAD_REQUEST);
        mappings.put(ClaimApplicationIssueErrorCode.ISSUE_SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        mappings.put(ClaimApplicationIssueErrorCode.CASE_RELATION_NOT_FOUND, HttpStatus.NOT_FOUND);

        mappings.put(ClaimApplicationAsyncErrorCode.CALLBACK_INVALID, HttpStatus.BAD_REQUEST);
        mappings.put(ClaimApplicationAsyncErrorCode.CLAIM_APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        mappings.put(ClaimApplicationAsyncErrorCode.CLAIM_APPLICATION_ASYNC_SYSTEM_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR);
        mappings.put(ClaimApplicationAsyncErrorCode.AUDIT_SIGNED_INVALID, HttpStatus.BAD_REQUEST);

        mappings.put(ClaimApplicationQueryErrorCode.CLAIM_APPLICATION_QUERY_NOT_FOUND, HttpStatus.NOT_FOUND);
        mappings.put(ClaimApplicationQueryErrorCode.CLAIM_APPLICATION_QUERY_INVALID, HttpStatus.BAD_REQUEST);
        mappings.put(ClaimApplicationQueryErrorCode.CLAIM_APPLICATION_QUERY_SYSTEM_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR);
        return Map.copyOf(mappings);
    }
}
