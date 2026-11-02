package com.wangguangwu.claim.application.shared.contract.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;

/**
 * 审核签收载荷，携带本次签收的全部案件号。
 *
 * @param caseNos 案件号列表
 * @author wangguangwu
 * @since 2026-11-02
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditSignedPayload(
        @JsonDeserialize(using = StrictStringListDeserializer.class) List<String> caseNos) {
}
