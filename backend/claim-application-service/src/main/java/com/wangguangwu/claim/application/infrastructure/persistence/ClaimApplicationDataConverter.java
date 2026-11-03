package com.wangguangwu.claim.application.infrastructure.persistence;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.claim.material.Material;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 理赔申请数据对象与领域聚合之间的转换器，人员信息按 JSON 列落库。
 *
 * <p>写方向逐字段映射，读方向显式调用聚合自己的反水化工厂，不用 setter 绕过状态机约束。
 * 这里不使用 MapStruct 生成：聚合根不允许暴露 setter，而 MapStruct 无法向抽象 mapper
 * 基类做构造器注入，人员 JSON 的读写又必须共用同一个 Spring 管理的 ObjectMapper。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Component
public class ClaimApplicationDataConverter {

    private final PersonInfoJsonConverter personInfoJsonConverter;

    /**
     * 转换为持久化数据对象；创建与更新时间由数据库维护，不随领域对象写入。
     *
     * @param claimApplication 理赔申请聚合根
     * @return 数据对象
     */
    public ClaimApplicationDO toDataObject(ClaimApplication claimApplication) {
        if (claimApplication == null) {
            return null;
        }
        ClaimApplicationStatus status = claimApplication.getStatus();
        return ClaimApplicationDO.builder().id(claimApplication.getId()).tenantCode(claimApplication.getTenantCode())
                .claimApplicationNo(claimApplication.getClaimApplicationNo())
                .externalClaimApplicationNo(claimApplication.getExternalClaimApplicationNo())
                .claimApplicationAttempt(claimApplication.getClaimApplicationAttempt())
                .completedAt(claimApplication.getCompletedAt())
                .version(claimApplication.getVersion()).status(status == null ? null : status.name())
                .applicantJson(personInfoJsonConverter.toJson(claimApplication.getApplicant()))
                .primaryInsuredJson(personInfoJsonConverter.toJson(claimApplication.getPrimaryInsured()))
                .accidentPersonJson(personInfoJsonConverter.toJson(claimApplication.getAccidentPerson()))
                .payeeJson(personInfoJsonConverter.toJson(claimApplication.getPayee())).build();
    }

    /**
     * 从数据对象与子实体重建完整理赔申请聚合。
     *
     * @param claimApplicationDO 根数据对象
     * @param materials          当前有效材料
     * @param issues             全部问题件历史
     * @return 理赔申请聚合根
     */
    public ClaimApplication toDomain(ClaimApplicationDO claimApplicationDO, List<Material> materials,
                                     List<ClaimApplicationIssue> issues) {
        if (claimApplicationDO == null) {
            return null;
        }
        return ClaimApplication.restore(claimApplicationDO.getId(), claimApplicationDO.getTenantCode(),
                claimApplicationDO.getClaimApplicationNo(), claimApplicationDO.getExternalClaimApplicationNo(),
                ClaimApplicationStatus.valueOf(claimApplicationDO.getStatus()),
                claimApplicationDO.getClaimApplicationAttempt(),
                claimApplicationDO.getCompletedAt(),
                personInfoJsonConverter.toApplicant(claimApplicationDO.getApplicantJson()),
                personInfoJsonConverter.toPrimaryInsured(claimApplicationDO.getPrimaryInsuredJson()),
                personInfoJsonConverter.toAccidentPerson(claimApplicationDO.getAccidentPersonJson()),
                personInfoJsonConverter.toPayee(claimApplicationDO.getPayeeJson()),
                claimApplicationDO.getVersion(), materials, issues);
    }
}
