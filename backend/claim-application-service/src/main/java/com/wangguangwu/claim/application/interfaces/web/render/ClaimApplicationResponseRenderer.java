package com.wangguangwu.claim.application.interfaces.web.render;

import com.wangguangwu.claim.application.interfaces.dto.ClaimApplicationIntakeResponse;
import com.wangguangwu.claim.application.interfaces.dto.ApiResponse;
import com.wangguangwu.claim.application.application.process.ClaimApplicationIntakeResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * 平台统一申请响应渲染器，全部租户共用同一种成功响应结构。
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
@Component
public class ClaimApplicationResponseRenderer {

    /**
     * 渲染签收成功响应。
     *
     * @param traceId 链路标识
     * @param result  签收结果
     * @return HTTP 响应
     */
    public ResponseEntity<Object> renderSuccess(String traceId, ClaimApplicationIntakeResult result) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.success(traceId, new ClaimApplicationIntakeResponse(result.claimApplicationNo())));
    }
}
