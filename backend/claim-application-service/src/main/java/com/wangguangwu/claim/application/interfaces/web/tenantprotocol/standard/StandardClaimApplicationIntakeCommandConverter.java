package com.wangguangwu.claim.application.interfaces.web.tenantprotocol.standard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.application.command.ClaimApplicationIntakeCommand;
import com.wangguangwu.claim.application.domain.claim.material.MaterialSubmission;
import com.wangguangwu.claim.application.domain.claim.person.AccidentPerson;
import com.wangguangwu.claim.application.domain.claim.person.Applicant;
import com.wangguangwu.claim.application.domain.claim.person.Payee;
import com.wangguangwu.claim.application.domain.claim.person.PrimaryInsured;
import com.wangguangwu.claim.application.shared.exception.BusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 平台标准报文到标准受理命令的转换器。
 *
 * <p>转换器只在租户协议接入边界解析报文，先完成 Bean Validation，再创建领域人员和值对象，
 * 防止未经校验的 HTTP 字段直接进入签收用例。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@Component
@RequiredArgsConstructor
public class StandardClaimApplicationIntakeCommandConverter {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * 将平台标准 JSON 报文转换为当前租户的标准受理命令。
     *
     * @param tenantCode 当前流水线的租户编码
     * @param plaintextPayload 已解密的标准 JSON 报文
     * @return 标准受理命令
     */
    public ClaimApplicationIntakeCommand convert(String tenantCode, String plaintextPayload) {
        StandardClaimApplicationIntakeRequest request = parse(plaintextPayload);
        validate(request);
        return ClaimApplicationIntakeCommand.builder()
                .tenantCode(tenantCode)
                .externalClaimApplicationNo(request.externalClaimApplicationNo().trim())
                .claimApplicationNo(normalize(request.claimApplicationNo()))
                .applicant(toApplicant(request.applicant()))
                .primaryInsured(toPrimaryInsured(request.primaryInsured()))
                .accidentPerson(toAccidentPerson(request.accidentPerson()))
                .payee(toPayee(request.payee()))
                .materials(toMaterials(request.materials()))
                .build();
    }

    private StandardClaimApplicationIntakeRequest parse(String plaintextPayload) {
        if (plaintextPayload == null || plaintextPayload.isBlank()) {
            throw new BusinessException("标准理赔申请报文不能为空");
        }
        try {
            return objectMapper.readValue(plaintextPayload, StandardClaimApplicationIntakeRequest.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("标准理赔申请报文不是合法 JSON", exception);
        }
    }

    private void validate(StandardClaimApplicationIntakeRequest request) {
        Set<ConstraintViolation<StandardClaimApplicationIntakeRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String reason = violations.stream()
                    .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                    .map(violation -> violation.getPropertyPath() + violation.getMessage())
                    .reduce((left, right) -> left + "；" + right)
                    .orElseThrow();
            throw new BusinessException("标准理赔申请报文校验失败：" + reason);
        }
    }

    private Applicant toApplicant(StandardClaimApplicationIntakeRequest.StandardPerson person) {
        return new Applicant(person.name().trim(), person.gender(), person.certificateType().trim(),
                person.certificateNo().trim());
    }

    private PrimaryInsured toPrimaryInsured(StandardClaimApplicationIntakeRequest.StandardPerson person) {
        return new PrimaryInsured(person.name().trim(), person.gender(), person.certificateType().trim(),
                person.certificateNo().trim());
    }

    private AccidentPerson toAccidentPerson(StandardClaimApplicationIntakeRequest.StandardPerson person) {
        return new AccidentPerson(person.name().trim(), person.gender(), person.certificateType().trim(),
                person.certificateNo().trim());
    }

    private Payee toPayee(StandardClaimApplicationIntakeRequest.StandardPerson person) {
        return new Payee(person.name().trim(), person.gender(), person.certificateType().trim(),
                person.certificateNo().trim());
    }

    private List<MaterialSubmission> toMaterials(List<StandardClaimApplicationIntakeRequest.StandardMaterial> materials) {
        return materials.stream()
                .map(material -> new MaterialSubmission(material.materialType().trim(), material.originalFileUrl().trim()))
                .toList();
    }

    private String normalize(String claimApplicationNo) {
        if (claimApplicationNo == null || claimApplicationNo.isBlank()) {
            return null;
        }
        return claimApplicationNo.trim();
    }
}
