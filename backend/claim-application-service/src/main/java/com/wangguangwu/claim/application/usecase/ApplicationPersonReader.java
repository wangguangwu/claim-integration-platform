package com.wangguangwu.claim.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.application.Application;
import org.springframework.stereotype.Component;

/**
 * 从申请的人员 JSON 中读取保单查询需要的非敏感标识。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Component
public class ApplicationPersonReader {

    private static final String CERTIFICATE_NO_FIELD = "certificateNo";

    private final ObjectMapper objectMapper;

    public ApplicationPersonReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 读取主被保险人证件号，用于向承保系统查询有效保单。
     *
     * @param application 申请聚合
     * @return 主被保险人证件号
     */
    public String readPrimaryInsuredCertificateNo(Application application) {
        JsonNode personNode = readPersonNode(application.getPrimaryInsuredJson(), application.getApplicationNo());
        JsonNode certificateNoNode = personNode.get(CERTIFICATE_NO_FIELD);
        if (certificateNoNode == null || certificateNoNode.isNull() || certificateNoNode.asText().isBlank()) {
            throw new IllegalStateException("主被保险人缺少证件号，applicationNo=" + application.getApplicationNo());
        }
        return certificateNoNode.asText();
    }

    private JsonNode readPersonNode(String personJson, String applicationNo) {
        if (personJson == null || personJson.isBlank()) {
            throw new IllegalStateException("主被保险人信息缺失，applicationNo=" + applicationNo);
        }
        try {
            return objectMapper.readTree(personJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("主被保险人信息不是合法 JSON，applicationNo=" + applicationNo, exception);
        }
    }
}
