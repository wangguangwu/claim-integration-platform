package com.wangguangwu.claim.application.interfaces.web.tenantprotocol;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.application.command.ClaimApplicationIntakeCommand;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.pipeline.TenantProtocolPipeline;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.pipeline.TenantProtocolPipelineRegistry;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.security.DefaultTenantProtocolDecryptor;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.security.DefaultTenantProtocolSignatureVerifier;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.security.TenantProtocolDecryptor;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.security.TenantProtocolSignatureVerifier;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.standard.StandardClaimApplicationIntakeCommandConverter;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.tenants.cpic.CpicProtocolPipeline;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.tenants.picclife.PicclifeProtocolPipeline;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.tenants.pingan.PinganProtocolPipeline;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.tenants.taikang.TaikangProtocolPipeline;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.tenants.xinhua.XinhuaProtocolPipeline;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.tenants.yongchong.YongchongProtocolPipeline;
import com.wangguangwu.claim.application.shared.exception.BusinessException;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 租户协议流水线测试，验证默认安全阶段与标准命令转换不会阻断六租户签收。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
class TenantProtocolPipelineTest {

    private static final String STANDARD_PAYLOAD = """
            {
              "externalClaimApplicationNo": "EXT-20261103-001",
              "claimApplicationNo": "  ",
              "applicant": {"name": "申请人", "gender": "MALE", "certificateType": "ID", "certificateNo": "110101199001010001"},
              "primaryInsured": {"name": "被保险人", "gender": "FEMALE", "certificateType": "ID", "certificateNo": "110101199001010002"},
              "accidentPerson": {"name": "出险人", "gender": "FEMALE", "certificateType": "ID", "certificateNo": "110101199001010003"},
              "payee": {"name": "领款人", "gender": "MALE", "certificateType": "ID", "certificateNo": "110101199001010004"},
              "materials": [{"materialType": "INVOICE", "originalFileUrl": "https://example.com/invoice.pdf"}]
            }
            """;

    @Test
    void shouldConvertStandardPayloadForEachTenant() {
        TenantProtocolPipelineRegistry registry = new TenantProtocolPipelineRegistry(createPipelines());

        for (String tenantCode : List.of("PICCLIFE", "XINHUA", "CPIC", "PINGAN", "TAIKANG", "YONGCHONG")) {
            ClaimApplicationIntakeCommand command = registry.execute(new TenantInfo(tenantCode, "v1"), STANDARD_PAYLOAD);

            assertEquals(tenantCode, command.tenantCode());
            assertEquals("EXT-20261103-001", command.externalClaimApplicationNo());
            assertNull(command.claimApplicationNo());
            assertEquals(1, command.materials().size());
        }
    }

    @Test
    void shouldRejectInvalidStandardPayloadBeforeIntake() {
        TenantProtocolPipelineRegistry registry = new TenantProtocolPipelineRegistry(createPipelines());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> registry.execute(new TenantInfo("PICCLIFE", "v1"), "{}"));

        assertTrue(exception.getMessage().startsWith("标准理赔申请报文校验失败："));
    }

    @Test
    void shouldRejectMalformedStandardPayloadBeforeIntake() {
        TenantProtocolPipelineRegistry registry = new TenantProtocolPipelineRegistry(createPipelines());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> registry.execute(new TenantInfo("PICCLIFE", "v1"), "not-json"));

        assertEquals("标准理赔申请报文不是合法 JSON", exception.getMessage());
    }

    @Test
    void shouldKeepDefaultSecurityStagesPassThrough() {
        TenantProtocolSignatureVerifier signatureVerifier = new DefaultTenantProtocolSignatureVerifier();
        TenantProtocolDecryptor decryptor = new DefaultTenantProtocolDecryptor();

        assertDoesNotThrow(() -> signatureVerifier.verify(STANDARD_PAYLOAD));
        assertEquals(STANDARD_PAYLOAD, decryptor.decrypt(STANDARD_PAYLOAD));
    }

    private List<TenantProtocolPipeline> createPipelines() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        StandardClaimApplicationIntakeCommandConverter converter = new StandardClaimApplicationIntakeCommandConverter(
                new ObjectMapper(), validator);
        TenantProtocolSignatureVerifier signatureVerifier = new DefaultTenantProtocolSignatureVerifier();
        TenantProtocolDecryptor decryptor = new DefaultTenantProtocolDecryptor();
        return List.of(
                new PicclifeProtocolPipeline(signatureVerifier, decryptor, converter),
                new XinhuaProtocolPipeline(signatureVerifier, decryptor, converter),
                new CpicProtocolPipeline(signatureVerifier, decryptor, converter),
                new PinganProtocolPipeline(signatureVerifier, decryptor, converter),
                new TaikangProtocolPipeline(signatureVerifier, decryptor, converter),
                new YongchongProtocolPipeline(signatureVerifier, decryptor, converter));
    }
}
