package com.wangguangwu.claim.application.support.mock.infrastructure;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模拟文件系统配置，仅在模拟开关开启时生效。
 *
 * @param failMaterialTypes 需要模拟上传失败的材料类型
 * @param delay             模拟处理耗时，用于验证外部调用超时
 * @author wangguangwu
 * @since 2026-10-27
 */
@ConfigurationProperties(prefix = "claim.mock.file-system")
public record MockFileSystemProperties(List<String> failMaterialTypes, Duration delay) {
}
