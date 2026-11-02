package com.wangguangwu.claim.application.interfaces.audit.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.shared.contract.BusinessException;
import com.wangguangwu.claim.application.shared.contract.ClaimApplicationAsyncErrorCode;
import com.wangguangwu.claim.application.shared.contract.message.AuditSignedMessage;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 审核签收报文解析器，使用平台 ObjectMapper 完成严格反序列化。
 *
 * <p>RocketMQ binder 的复合转换器会静默降级到 fastjson，并把 Jackson 的解析异常继续向后传递，
 * 导致非字符串案件号被强转为字符串。消费端因此改为原生字节解码，由本类显式解析，
 * 让 {@code StrictStringListDeserializer} 的校验真正生效。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Component
@RequiredArgsConstructor
public class AuditSignedMessageParser {

    private final ObjectMapper objectMapper;

    /**
     * 把原始报文解析为审核签收消息。
     *
     * @param payload 原始 JSON 字节
     * @return 审核签收消息
     */
    public AuditSignedMessage parse(byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw invalid("审核签收消息报文为空");
        }
        try {
            return objectMapper.readValue(payload, AuditSignedMessage.class);
        } catch (IOException exception) {
            String reason = exception instanceof JsonProcessingException jsonException
                    ? jsonException.getOriginalMessage()
                    : exception.getMessage();
            throw new BusinessException(ClaimApplicationAsyncErrorCode.AUDIT_SIGNED_INVALID,
                    "审核签收消息报文非法：" + reason, exception);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ClaimApplicationAsyncErrorCode.AUDIT_SIGNED_INVALID, message);
    }
}
