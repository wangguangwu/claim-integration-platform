package com.wangguangwu.claim.application.interfaces.intake.web.render;

import com.wangguangwu.claim.application.domain.claim.exception.ApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.usecase.intake.result.ClaimApplicationIntakeResult;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import org.springframework.http.ResponseEntity;

/**
 * 租户申请响应渲染端口，允许不同租户按自身协议返回不同的响应结构与 HTTP 状态。
 *
 * <p>实现类必须自行控制响应头，未提供实现的租户由统一结构渲染器兜底。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface TenantApplicationResponseRenderer {

    /**
     * 判断是否由当前渲染器处理该租户。
     *
     * @param tenantInfo 当前租户信息
     * @return 匹配时为 true
     */
    boolean supports(TenantInfo tenantInfo);

    /**
     * 渲染签收成功响应。
     *
     * @param tenantInfo 当前租户信息
     * @param traceId    链路标识
     * @param result     签收结果
     * @return HTTP 响应
     */
    ResponseEntity<Object> renderSuccess(TenantInfo tenantInfo, String traceId, ClaimApplicationIntakeResult result);

    /**
     * 渲染签收失败响应。
     *
     * @param tenantInfo   当前租户信息
     * @param traceId      链路标识
     * @param errorCode    业务错误码
     * @param errorMessage 面向调用方的错误说明
     * @return HTTP 响应
     */
    ResponseEntity<Object> renderFailure(TenantInfo tenantInfo, String traceId, ApplicationIntakeErrorCode errorCode,
                                         String errorMessage);
}
