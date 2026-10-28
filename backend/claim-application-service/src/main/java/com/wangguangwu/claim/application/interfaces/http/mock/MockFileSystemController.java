package com.wangguangwu.claim.application.interfaces.http.mock;

import com.wangguangwu.claim.application.infrastructure.external.mock.MockFileSystemStore;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模拟文件系统接口，只在模拟开关开启时注册。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RestController
@RequestMapping("/mock/file-system/v1/uploads")
@ConditionalOnProperty(name = "claim.mock.enabled", havingValue = "true")
public class MockFileSystemController {

    private final MockFileSystemStore mockFileSystemStore;

    public MockFileSystemController(MockFileSystemStore mockFileSystemStore) {
        this.mockFileSystemStore = mockFileSystemStore;
    }

    /**
     * 上传材料，按源地址幂等。
     *
     * @param command 上传命令
     * @return 上传结果
     */
    @PostMapping
    public MockFileSystemStore.UploadOutcome upload(@RequestBody MockFileSystemStore.MockUploadCommand command) {
        return mockFileSystemStore.upload(command);
    }

    /**
     * 查看已模拟的上传记录。
     *
     * @return 上传记录
     */
    @GetMapping
    public List<MockFileSystemStore.UploadRecord> uploads() {
        return mockFileSystemStore.uploads();
    }

    /**
     * 清空模拟上传记录，不涉及磁盘文件。
     */
    @DeleteMapping
    public void clear() {
        mockFileSystemStore.clear();
    }
}
