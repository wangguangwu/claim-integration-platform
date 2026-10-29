package com.wangguangwu.claim.application.support.mock.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 模拟文件系统测试：源地址幂等、按类型拒绝与记录清空。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class MockFileSystemStoreTest {

    private static final String APPLICATION_NO = "A2026102800000001";
    private static final String BATCH_NO = "B20261028batch";
    private static final String SOURCE_URL = "https://tenant.example.com/medical-record.pdf";

    private final MockFileSystemStore mockFileSystemStore = new MockFileSystemStore(
            new MockFileSystemProperties(List.of("MATERIAL_TYPE_REJECTED"), Duration.ZERO));

    /**
     * 清空上一用例的模拟数据。
     */
    @BeforeEach
    void setUp() {
        mockFileSystemStore.clear();
    }

    /**
     * 同一源地址重复上传返回同一模拟地址，且只保留一条记录。
     */
    @Test
    void shouldReturnSameUploadedUrlForSameSourceUrl() {
        MockFileSystemStore.UploadOutcome firstUpload = mockFileSystemStore.upload(command("medical-record"));
        MockFileSystemStore.UploadOutcome secondUpload = mockFileSystemStore.upload(command("medical-record"));

        assertThat(firstUpload.success()).isTrue();
        assertThat(firstUpload.uploadedFileUrl()).isEqualTo("mock://file-system/" + APPLICATION_NO + "/" + BATCH_NO
                + "/medical-record");
        assertThat(secondUpload.uploadedFileUrl()).isEqualTo(firstUpload.uploadedFileUrl());
        assertThat(secondUpload.fileId()).isEqualTo(firstUpload.fileId());
        assertThat(mockFileSystemStore.uploads()).hasSize(1);
    }

    /**
     * 命中失败配置的材料类型返回明确拒绝结果，不生成模拟地址。
     */
    @Test
    void shouldRejectConfiguredMaterialType() {
        MockFileSystemStore.UploadOutcome outcome = mockFileSystemStore.upload(command("MATERIAL_TYPE_REJECTED"));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("MOCK_UPLOAD_REJECTED");
        assertThat(outcome.uploadedFileUrl()).isNull();
        assertThat(mockFileSystemStore.uploads()).isEmpty();
    }

    /**
     * 清空后不再保留任何模拟上传记录。
     */
    @Test
    void shouldClearUploads() {
        mockFileSystemStore.upload(command("medical-record"));

        mockFileSystemStore.clear();

        assertThat(mockFileSystemStore.uploads()).isEmpty();
    }

    private MockFileSystemStore.MockUploadCommand command(String materialType) {
        return new MockFileSystemStore.MockUploadCommand(APPLICATION_NO, BATCH_NO, materialType, SOURCE_URL);
    }
}
