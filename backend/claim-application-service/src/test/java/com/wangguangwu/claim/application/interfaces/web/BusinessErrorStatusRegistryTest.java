package com.wangguangwu.claim.application.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationErrorCode;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationQueryErrorCode;
import com.wangguangwu.claim.application.domain.claim.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.shared.contract.BusinessErrorCode;
import com.wangguangwu.claim.application.shared.contract.ClaimApplicationAsyncErrorCode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 业务错误码状态注册表单元测试：保证每个错误码都有唯一映射，且与集成契约一致。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@DisplayName("业务错误码状态注册表")
class BusinessErrorStatusRegistryTest {

    private static final Map<BusinessErrorCode, HttpStatus> EXPECTED_STATUS = Map.ofEntries(
            Map.entry(ClaimApplicationIntakeErrorCode.TENANT_PROTOCOL_UNSUPPORTED, HttpStatus.BAD_REQUEST),
            Map.entry(ClaimApplicationIntakeErrorCode.TENANT_PROTOCOL_AMBIGUOUS, HttpStatus.BAD_REQUEST),
            Map.entry(ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_INVALID, HttpStatus.BAD_REQUEST),
            Map.entry(ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_BUSY, HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry(ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_SYSTEM_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry(ClaimApplicationIntakeErrorCode.AUDIT_DELIVERY_APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ClaimApplicationErrorCode.CLAIM_APPLICATION_STATUS_INVALID, HttpStatus.CONFLICT),
            Map.entry(ClaimApplicationErrorCode.CLAIM_APPLICATION_MATERIAL_RESULT_INVALID, HttpStatus.BAD_REQUEST),
            Map.entry(ClaimApplicationErrorCode.CLAIM_APPLICATION_MATERIAL_STATUS_INVALID, HttpStatus.CONFLICT),
            Map.entry(ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID, HttpStatus.BAD_REQUEST),
            Map.entry(ClaimApplicationIssueErrorCode.ISSUE_STATUS_INVALID, HttpStatus.CONFLICT),
            Map.entry(ClaimApplicationIssueErrorCode.ISSUE_ALREADY_OPEN, HttpStatus.CONFLICT),
            Map.entry(ClaimApplicationIssueErrorCode.ISSUE_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ClaimApplicationIssueErrorCode.ISSUE_INTAKE_INVALID, HttpStatus.BAD_REQUEST),
            Map.entry(ClaimApplicationIssueErrorCode.ISSUE_SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry(ClaimApplicationIssueErrorCode.CASE_RELATION_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ClaimApplicationAsyncErrorCode.CALLBACK_INVALID, HttpStatus.BAD_REQUEST),
            Map.entry(ClaimApplicationAsyncErrorCode.CLAIM_APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ClaimApplicationAsyncErrorCode.CLAIM_APPLICATION_ASYNC_SYSTEM_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry(ClaimApplicationAsyncErrorCode.AUDIT_SIGNED_INVALID, HttpStatus.BAD_REQUEST),
            Map.entry(ClaimApplicationQueryErrorCode.CLAIM_APPLICATION_QUERY_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ClaimApplicationQueryErrorCode.CLAIM_APPLICATION_QUERY_INVALID, HttpStatus.BAD_REQUEST),
            Map.entry(ClaimApplicationQueryErrorCode.CLAIM_APPLICATION_QUERY_SYSTEM_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR)
    );

    @Test
    @DisplayName("四个枚举的全部错误码均有 HTTP 状态映射")
    void everyBusinessErrorCode_hasMapping() {
        List<BusinessErrorCode> errorCodes = allBusinessErrorCodes().toList();
        assertThat(errorCodes).hasSameSizeAs(EXPECTED_STATUS.keySet());
        errorCodes.forEach(errorCode ->
                assertThat(BusinessErrorStatusRegistry.statusOf(errorCode)).isNotNull());
    }

    @Test
    @DisplayName("全部错误码映射与集成契约一致")
    void statusOf_allBusinessErrorCodes_matchesContract() {
        EXPECTED_STATUS.forEach((errorCode, expectedStatus) ->
                assertThat(BusinessErrorStatusRegistry.statusOf(errorCode))
                        .as("错误码 %s 的 HTTP 状态", errorCode.code())
                        .isEqualTo(expectedStatus));
    }

    @Test
    @DisplayName("未登记错误码立即失败，不静默降级")
    void statusOf_unregisteredErrorCode_throwsIllegalArgument() {
        BusinessErrorCode unknownErrorCode = () -> "UNKNOWN_BUSINESS_ERROR";

        assertThatThrownBy(() -> BusinessErrorStatusRegistry.statusOf(unknownErrorCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN_BUSINESS_ERROR");
    }

    private static Stream<BusinessErrorCode> allBusinessErrorCodes() {
        return Stream.of(
                Arrays.asList(ClaimApplicationIntakeErrorCode.values()),
                Arrays.asList(ClaimApplicationErrorCode.values()),
                Arrays.asList(ClaimApplicationIssueErrorCode.values()),
                Arrays.asList(ClaimApplicationAsyncErrorCode.values()),
                Arrays.asList(ClaimApplicationQueryErrorCode.values()))
                .flatMap(List::stream);
    }
}
