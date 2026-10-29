package com.wangguangwu.claim.application.support.mock.infrastructure;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 内存模拟文件系统，按源地址幂等并支持按材料类型模拟失败。
 *
 * <p>模拟数据不落库，重启后清空；模拟地址固定为 {@code mock://file-system/...}，不读写真实文件。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "claim.mock.enabled", havingValue = "true")
public class MockFileSystemStore {

    private static final String UPLOADED_FILE_URL_TEMPLATE = "mock://file-system/%s/%s/%s";
    private static final String REJECTED_ERROR_CODE = "MOCK_UPLOAD_REJECTED";
    private static final String REJECTED_ERROR_MESSAGE = "模拟文件系统按材料类型拒绝上传";
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final Map<String, UploadRecord> uploadsBySourceUrl = new ConcurrentHashMap<>();
    private final MockFileSystemProperties properties;

    /**
     * 模拟上传材料，同一源地址重复上传返回同一模拟地址。
     *
     * @param command 上传命令
     * @return 上传结果
     */
    public UploadOutcome upload(MockUploadCommand command) {
        applyDelay();
        if (shouldReject(command.materialType())) {
            log.info("模拟文件系统拒绝上传，claimApplicationNo={}，materialType={}", command.claimApplicationNo(),
                    command.materialType());
            return UploadOutcome.rejected(REJECTED_ERROR_CODE, REJECTED_ERROR_MESSAGE);
        }
        UploadRecord uploadRecord = uploadsBySourceUrl.computeIfAbsent(command.sourceUrl(),
                sourceUrl -> newUploadRecord(command));
        return UploadOutcome.succeeded(uploadRecord.fileId(), uploadRecord.uploadedFileUrl());
    }

    /**
     * 查看已模拟的上传记录，不返回租户原始地址。
     *
     * @return 上传记录
     */
    public List<UploadRecord> uploads() {
        return List.copyOf(uploadsBySourceUrl.values());
    }

    /**
     * 清空模拟上传记录，供联调与测试重置。
     */
    public void clear() {
        uploadsBySourceUrl.clear();
    }

    private UploadRecord newUploadRecord(MockUploadCommand command) {
        String uploadedFileUrl = UPLOADED_FILE_URL_TEMPLATE.formatted(command.claimApplicationNo(), command.batchNo(),
                command.materialType());
        return new UploadRecord(UUID.randomUUID().toString(), command.claimApplicationNo(), command.batchNo(),
                command.materialType(), uploadedFileUrl, LocalDateTime.now(BUSINESS_ZONE_ID));
    }

    private boolean shouldReject(String materialType) {
        return properties.failMaterialTypes() != null && properties.failMaterialTypes().contains(materialType);
    }

    private void applyDelay() {
        if (properties.delay() == null || properties.delay().isZero() || properties.delay().isNegative()) {
            return;
        }
        try {
            Thread.sleep(properties.delay().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("模拟文件系统等待被中断", exception);
        }
    }

    /**
     * 模拟上传命令。
     *
     * @param claimApplicationNo 平台申请编号
     * @param batchNo       材料批次编号
     * @param materialType  材料类型
     * @param sourceUrl     租户提交的初始文件地址
     */
    public record MockUploadCommand(String claimApplicationNo, String batchNo, String materialType, String sourceUrl) {
    }

    /**
     * 模拟上传记录。
     *
     * @param fileId          文件标识
     * @param claimApplicationNo   平台申请编号
     * @param batchNo         材料批次编号
     * @param materialType    材料类型
     * @param uploadedFileUrl 模拟上传地址
     * @param registeredAt    登记时间
     */
    public record UploadRecord(String fileId, String claimApplicationNo, String batchNo, String materialType,
                               String uploadedFileUrl, LocalDateTime registeredAt) {
    }

    /**
     * 模拟上传结果。
     *
     * @param success         是否成功
     * @param fileId          文件标识
     * @param uploadedFileUrl 模拟上传地址
     * @param errorCode       失败错误码
     * @param errorMessage    失败说明
     */
    public record UploadOutcome(boolean success, String fileId, String uploadedFileUrl, String errorCode,
                                String errorMessage) {

        /**
         * 构建上传成功结果。
         *
         * @param fileId          文件标识
         * @param uploadedFileUrl 模拟上传地址
         * @return 成功结果
         */
        public static UploadOutcome succeeded(String fileId, String uploadedFileUrl) {
            return new UploadOutcome(true, fileId, uploadedFileUrl, null, null);
        }

        /**
         * 构建上传被拒绝结果。
         *
         * @param errorCode    错误码
         * @param errorMessage 错误说明
         * @return 失败结果
         */
        public static UploadOutcome rejected(String errorCode, String errorMessage) {
            return new UploadOutcome(false, null, null, errorCode, errorMessage);
        }
    }
}
