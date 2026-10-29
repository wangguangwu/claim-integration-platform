package com.wangguangwu.claim.application.usecase.intake;

import com.wangguangwu.claim.application.domain.material.Material;
import com.wangguangwu.claim.application.domain.material.MaterialBatchNoGenerator;
import com.wangguangwu.claim.application.domain.claim.MaterialSubmission;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 构建申请签收所需的材料记录，同一次签收共用一个批次编号，不访问数据库与 Redis。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Service
public class ApplicationIntakeMaterialFactory {

    private final MaterialBatchNoGenerator materialBatchNoGenerator;

    public ApplicationIntakeMaterialFactory(MaterialBatchNoGenerator materialBatchNoGenerator) {
        this.materialBatchNoGenerator = materialBatchNoGenerator;
    }

    /**
     * 为同一次签收创建共用批次编号的材料记录。
     *
     * @param applicationNo 平台申请编号
     * @param materials     本次提交的材料
     * @return 待持久化材料
     */
    public List<Material> createMaterials(String applicationNo, List<MaterialSubmission> materials) {
        String batchNo = materialBatchNoGenerator.nextBatchNo();
        return materials.stream()
                .map(submission -> Material.createPending(applicationNo, batchNo, submission))
                .toList();
    }
}
