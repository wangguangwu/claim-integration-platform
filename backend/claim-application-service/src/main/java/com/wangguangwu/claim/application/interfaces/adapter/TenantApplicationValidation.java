package com.wangguangwu.claim.application.interfaces.adapter;

import com.wangguangwu.claim.application.usecase.exception.ApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.usecase.exception.ApplicationIntakeException;
import org.springframework.util.StringUtils;

/**
 * 租户报文校验的公共判断，统一错误码与字段定位方式。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public final class TenantApplicationValidation {

    private TenantApplicationValidation() {
    }

    /**
     * 校验文本字段非空。
     *
     * @param value     字段值
     * @param fieldPath 字段路径，用于定位报文问题
     */
    public static void requireText(String value, String fieldPath) {
        if (!StringUtils.hasText(value)) {
            throw invalid("租户申请报文缺少字段：" + fieldPath);
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
            throw invalid("租户申请报文缺少字段：" + fieldPath);
        }
    }

    /**
     * 构建报文不合法异常，不携带原始请求正文。
     *
     * @param message 错误说明
     * @return 标准业务异常
     */
    public static ApplicationIntakeException invalid(String message) {
        return new ApplicationIntakeException(ApplicationIntakeErrorCode.APPLICATION_INTAKE_INVALID, message);
    }
}
