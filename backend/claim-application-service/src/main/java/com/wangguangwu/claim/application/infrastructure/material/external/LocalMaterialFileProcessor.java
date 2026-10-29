package com.wangguangwu.claim.application.infrastructure.material.external;

import com.wangguangwu.claim.application.domain.integration.interaction.InteractionType;
import com.wangguangwu.claim.application.domain.material.FileUploadResult;
import com.wangguangwu.claim.application.domain.material.MaterialUploadRequest;
import com.wangguangwu.claim.application.domain.material.port.MaterialFileProcessor;
import com.wangguangwu.claim.application.infrastructure.integration.config.LocalIntegrationProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 材料文件上传端口的本地实现：按配置休眠、打印交互日志，并按材料类型返回约定结果。
 *
 * <p>明确拒绝以失败结果返回，属于业务失败；休眠被中断时恢复中断标记并抛出，由调用方保留原状态。</p>
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LocalMaterialFileProcessor implements MaterialFileProcessor {

    private static final InteractionType INTERACTION_TYPE = InteractionType.MATERIAL_PROCESS;
    private static final String LOCAL_UPLOAD_REJECTED_CODE = "LOCAL_UPLOAD_REJECTED";
    private static final String LOCAL_UPLOAD_REJECTED_MESSAGE = "本地实现按配置拒绝该材料类型";
    private static final String LOCAL_UPLOADED_FILE_URL_TEMPLATE = "https://local.invalid/material/%s/%s";

    private final LocalIntegrationProperties properties;

    /**
     * 上传材料；命中 {@code fail-material-types} 时返回拒绝结果，其余返回约定文件地址。
     *
     * @param request 上传请求
     * @return 上传结果
     */
    @Override
    public FileUploadResult upload(MaterialUploadRequest request) {
        pause(request.claimApplicationNo());
        if (properties.failMaterialTypes().contains(request.materialType())) {
            log.warn("外部系统本地实现拒绝材料上传，claimApplicationNo={}，materialType={}", request.claimApplicationNo(),
                    request.materialType());
            return FileUploadResult.failed(LOCAL_UPLOAD_REJECTED_CODE, LOCAL_UPLOAD_REJECTED_MESSAGE);
        }
        return FileUploadResult.succeeded(
                LOCAL_UPLOADED_FILE_URL_TEMPLATE.formatted(request.claimApplicationNo(), request.materialType()));
    }

    private void pause(String claimApplicationNo) {
        Duration delay = properties.delay();
        log.info("外部系统本地实现调用，targetSystem={}，interactionType={}，claimApplicationNo={}，delay={}",
                INTERACTION_TYPE.targetSystem(), INTERACTION_TYPE, claimApplicationNo, delay);
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("外部系统本地实现休眠被中断，claimApplicationNo=" + claimApplicationNo,
                    exception);
        }
    }
}
