package com.wangguangwu.claim.application.interfaces.http.callback;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wangguangwu.claim.application.usecase.UnderwritingCallbackUseCase;
import com.wangguangwu.claim.application.usecase.exception.ApplicationAsyncErrorCode;
import com.wangguangwu.claim.application.usecase.exception.ApplicationAsyncException;
import com.wangguangwu.claim.application.usecase.result.UnderwritingCallbackDisposition;
import com.wangguangwu.claim.application.usecase.result.UnderwritingCallbackResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 承保完成回调接口测试：成功响应与业务错误状态映射。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class UnderwritingCallbackControllerTest {

    private static final String APPLICATION_NO = "A2026102800000001";
    private static final String CALLBACK_PATH = "/api/application/v1/callbacks/underwriting-completed";
    private static final String REQUEST_BODY = "{\"applicationNo\":\"" + APPLICATION_NO + "\"}";

    private final UnderwritingCallbackUseCase underwritingCallbackUseCase = mock(UnderwritingCallbackUseCase.class);

    private MockMvc mockMvc;

    /**
     * 构建只含回调控制器与回调异常处理器的 MockMvc。
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UnderwritingCallbackController(underwritingCallbackUseCase))
                .setControllerAdvice(new UnderwritingCallbackExceptionHandler())
                .build();
    }

    /**
     * 有效回调返回统一成功结构与处理结果。
     */
    @Test
    void shouldReturnSuccessForValidCallback() throws Exception {
        when(underwritingCallbackUseCase.complete(any())).thenReturn(
                new UnderwritingCallbackResult(APPLICATION_NO, UnderwritingCallbackDisposition.COMPLETED));

        mockMvc.perform(post(CALLBACK_PATH)
                        .header("X-Request-Id", "callback-request-1")
                        .header("X-Trace-Id", "trace-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.traceId").value("trace-1"))
                .andExpect(jsonPath("$.data.disposition").value("COMPLETED"));
    }

    /**
     * 报文缺少申请编号返回 400 与回调非法错误码。
     */
    @Test
    void shouldReturnBadRequestForInvalidCallback() throws Exception {
        when(underwritingCallbackUseCase.complete(any()))
                .thenThrow(new ApplicationAsyncException(ApplicationAsyncErrorCode.CALLBACK_INVALID, "回调缺少 X-Request-Id"));

        mockMvc.perform(post(CALLBACK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CALLBACK_INVALID"));
    }

    /**
     * 申请不存在返回 404。
     */
    @Test
    void shouldReturnNotFoundForMissingApplication() throws Exception {
        when(underwritingCallbackUseCase.complete(any())).thenThrow(new ApplicationAsyncException(
                ApplicationAsyncErrorCode.APPLICATION_NOT_FOUND, "承保完成通知对应的申请不存在"));

        mockMvc.perform(post(CALLBACK_PATH)
                        .header("X-Request-Id", "callback-request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
    }

    /**
     * 未预期异常返回统一系统错误，避免回调方拿到框架默认报文。
     */
    @Test
    void shouldReturnSystemErrorForUnexpectedException() throws Exception {
        when(underwritingCallbackUseCase.complete(any())).thenThrow(new IllegalStateException("数据库不可用"));

        mockMvc.perform(post(CALLBACK_PATH)
                        .header("X-Request-Id", "callback-request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("APPLICATION_ASYNC_SYSTEM_ERROR"));
    }

    /**
     * 报文无法解析时按回调非法处理，返回 400。
     */
    @Test
    void shouldReturnBadRequestForUnreadableBody() throws Exception {
        mockMvc.perform(post(CALLBACK_PATH)
                        .header("X-Request-Id", "callback-request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationNo\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CALLBACK_INVALID"));
    }
}
