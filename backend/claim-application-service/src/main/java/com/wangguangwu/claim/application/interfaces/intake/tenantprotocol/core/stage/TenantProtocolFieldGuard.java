package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage;

import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeException;
import org.springframework.util.StringUtils;

/**
 * 租户申请报文的必填字段守卫，统一缺失字段的定位方式与错误说明格式。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public final class TenantProtocolFieldGuard {

    private TenantProtocolFieldGuard() {
    }

    /**
     * 校验文本字段非空。
     *
     * @param value     字段值
     * @param fieldPath 字段路径，用于定位报文问题
     */
    public static void requireText(String value, String fieldPath) {
        if (!StringUtils.hasText(value)) {
            throw ClaimApplicationIntakeException.invalid("租户申请报文缺少字段：" + fieldPath);
        }
    }

    /**
     * 校验对象或集合字段存在。
     *
     * @param value     字段值
     * @param fieldPath 字段路径，用于定位报文问题
     */
    public static void requirePresent(Object value, String fieldPath) {
        if (value == null) {
            throw ClaimApplicationIntakeException.invalid("租户申请报文缺少字段：" + fieldPath);
        }
    }
}
