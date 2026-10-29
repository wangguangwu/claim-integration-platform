package com.wangguangwu.claim.application.infrastructure.intake.persistence;

import com.wangguangwu.claim.application.domain.claim.ApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
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
public class ApplicationDataConverter {

    private final PersonInfoJsonConverter personInfoJsonConverter;

    /**
     * 转换为持久化数据对象；创建与更新时间由数据库维护，不随领域对象写入。
     *
     * @param application 理赔申请聚合根
     * @return 数据对象
     */
    public ApplicationDO toDataObject(ClaimApplication application) {
        if (application == null) {
            return null;
        }
        ApplicationStatus status = application.getStatus();
        return ApplicationDO.builder().id(application.getId()).tenantCode(application.getTenantCode())
                .applicationNo(application.getApplicationNo())
                .externalApplicationNo(application.getExternalApplicationNo())
                .applicationAttempt(application.getApplicationAttempt()).status(status == null ? null : status.name())
                .applicantJson(personInfoJsonConverter.toJson(application.getApplicant()))
                .primaryInsuredJson(personInfoJsonConverter.toJson(application.getPrimaryInsured()))
                .accidentPersonJson(personInfoJsonConverter.toJson(application.getAccidentPerson()))
                .payeeJson(personInfoJsonConverter.toJson(application.getPayee())).build();
    }

    /**
     * 从数据对象重建理赔申请聚合；本次提交材料只在签收时产生，不回读。
     *
     * @param applicationDO 数据对象
     * @return 理赔申请聚合根
     */
    public ClaimApplication toDomain(ApplicationDO applicationDO) {
        if (applicationDO == null) {
            return null;
        }
        return ClaimApplication.restore(applicationDO.getId(), applicationDO.getTenantCode(),
                applicationDO.getApplicationNo(), applicationDO.getExternalApplicationNo(),
                ApplicationStatus.valueOf(applicationDO.getStatus()), applicationDO.getApplicationAttempt(),
                personInfoJsonConverter.toApplicant(applicationDO.getApplicantJson()),
                personInfoJsonConverter.toPrimaryInsured(applicationDO.getPrimaryInsuredJson()),
                personInfoJsonConverter.toAccidentPerson(applicationDO.getAccidentPersonJson()),
                personInfoJsonConverter.toPayee(applicationDO.getPayeeJson()));
    }
}
