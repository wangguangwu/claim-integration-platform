package com.wangguangwu.claim.application.interfaces.adapter.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.wangguangwu.claim.application.usecase.command.ApplicationIntakeCommand;
import com.wangguangwu.claim.application.usecase.command.Gender;
import com.wangguangwu.claim.application.usecase.exception.ApplicationIntakeException;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import org.junit.jupiter.api.Test;

/**
 * 本地示例租户协议流水线测试，覆盖解析、校验与转换全链路。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class DemoApplicationPipelineTest {

    private final JsonMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * A 租户扁平字段应映射为标准人员和材料模型。
     */
    @Test
    void shouldConvertDemoInsurerARequest() {
        DemoInsurerAApplicationPipeline pipeline = new DemoInsurerAApplicationPipeline(objectMapper);

        ApplicationIntakeCommand command = pipeline.execute("""
                {
                  "requestNo": " A-REQUEST-001 ",
                  "applicant": {"fullName":"申请人","sex":"F","idType":"ID_CARD","idNo":"A1"},
                  "insured": {"fullName":"被保险人","sex":"M","idType":"ID_CARD","idNo":"I1"},
                  "accident": {"fullName":"出险人","sex":"M","idType":"ID_CARD","idNo":"C1"},
                  "payee": {"fullName":"领款人","sex":"F","idType":"ID_CARD","idNo":"P1"},
                  "attachments": [{"type":"IMAGE","sourceUrl":"https://example.com/a.jpg"}]
                }
                """);

        assertThat(command.tenantCode()).isEqualTo("demo-insurer-a");
        assertThat(command.externalApplicationNo()).isEqualTo("A-REQUEST-001");
        assertThat(command.applicant().name()).isEqualTo("申请人");
        assertThat(command.applicant().gender()).isEqualTo(Gender.FEMALE);
        assertThat(command.primaryInsured().gender()).isEqualTo(Gender.MALE);
        assertThat(command.materials()).singleElement()
                .satisfies(material -> assertThat(material.originalFileUrl()).isEqualTo("https://example.com/a.jpg"));
    }

    /**
     * B 租户嵌套证件字段与数字性别码应完成归一化。
     */
    @Test
    void shouldConvertDemoInsurerBRequest() {
        DemoInsurerBApplicationPipeline pipeline = new DemoInsurerBApplicationPipeline(objectMapper);

        ApplicationIntakeCommand command = pipeline.execute("""
                {
                  "claimReference": "B-CLAIM-001",
                  "claimant": {"name":"申请人","genderCode":"1","credential":{"type":"ID_CARD","number":"A1"}},
                  "mainInsured": {"name":"被保险人","genderCode":"2","credential":{"type":"ID_CARD","number":"I1"}},
                  "injured": {"name":"出险人","genderCode":"1","credential":{"type":"ID_CARD","number":"C1"}},
                  "beneficiary": {"name":"领款人","genderCode":"2","credential":{"type":"ID_CARD","number":"P1"}},
                  "evidences": [{"documentCode":"MEDICAL","fileDownloadUrl":"https://example.com/b.pdf"}]
                }
                """);

        assertThat(command.tenantCode()).isEqualTo("demo-insurer-b");
        assertThat(command.externalApplicationNo()).isEqualTo("B-CLAIM-001");
        assertThat(command.applicant().name()).isEqualTo("申请人");
        assertThat(command.applicant().gender()).isEqualTo(Gender.MALE);
        assertThat(command.primaryInsured().gender()).isEqualTo(Gender.FEMALE);
        assertThat(command.materials()).singleElement()
                .satisfies(material -> {
                    assertThat(material.materialType()).isEqualTo("MEDICAL");
                    assertThat(material.originalFileUrl()).isEqualTo("https://example.com/b.pdf");
                });
    }

    /**
     * B 租户未定义的性别码必须在转换阶段按字段拒绝。
     */
    @Test
    void shouldRejectUnsupportedDemoInsurerBGenderCode() {
        DemoInsurerBApplicationPipeline pipeline = new DemoInsurerBApplicationPipeline(objectMapper);

        assertThatThrownBy(() -> pipeline.execute("""
                {
                  "claimReference": "B-CLAIM-002",
                  "claimant": {"name":"申请人","genderCode":"9","credential":{"type":"ID_CARD","number":"A1"}},
                  "mainInsured": {"name":"被保险人","genderCode":"2","credential":{"type":"ID_CARD","number":"I1"}},
                  "injured": {"name":"出险人","genderCode":"1","credential":{"type":"ID_CARD","number":"C1"}},
                  "beneficiary": {"name":"领款人","genderCode":"2","credential":{"type":"ID_CARD","number":"P1"}},
                  "evidences": [{"documentCode":"MEDICAL","fileDownloadUrl":"https://example.com/b.pdf"}]
                }
                """))
                .isInstanceOf(ApplicationIntakeException.class)
                .hasMessage("genderCode 仅支持 1 或 2");
    }

    /**
     * 缺失嵌套人员字段时必须在校验阶段按字段路径拒绝。
     */
    @Test
    void shouldRejectMissingNestedPersonField() {
        DemoInsurerAApplicationPipeline pipeline = new DemoInsurerAApplicationPipeline(objectMapper);

        assertThatThrownBy(() -> pipeline.execute("""
                {
                  "requestNo": "A-REQUEST-003",
                  "applicant": {"sex":"F","idType":"ID_CARD","idNo":"A1"},
                  "insured": {"fullName":"被保险人","sex":"M","idType":"ID_CARD","idNo":"I1"},
                  "accident": {"fullName":"出险人","sex":"M","idType":"ID_CARD","idNo":"C1"},
                  "payee": {"fullName":"领款人","sex":"F","idType":"ID_CARD","idNo":"P1"},
                  "attachments": []
                }
                """))
                .isInstanceOf(ApplicationIntakeException.class)
                .hasMessage("租户申请报文缺少字段：applicant.fullName");
    }

    /**
     * 缺失嵌套证件对象时必须在校验阶段按字段路径拒绝。
     */
    @Test
    void shouldRejectMissingCredentialObject() {
        DemoInsurerBApplicationPipeline pipeline = new DemoInsurerBApplicationPipeline(objectMapper);

        assertThatThrownBy(() -> pipeline.execute("""
                {
                  "claimReference": "B-CLAIM-004",
                  "claimant": {"name":"申请人","genderCode":"1"},
                  "mainInsured": {"name":"被保险人","genderCode":"2","credential":{"type":"ID_CARD","number":"I1"}},
                  "injured": {"name":"出险人","genderCode":"1","credential":{"type":"ID_CARD","number":"C1"}},
                  "beneficiary": {"name":"领款人","genderCode":"2","credential":{"type":"ID_CARD","number":"P1"}},
                  "evidences": []
                }
                """))
                .isInstanceOf(ApplicationIntakeException.class)
                .hasMessage("租户申请报文缺少字段：claimant.credential");
    }

    /**
     * 非 JSON 正文明文必须在解析阶段拒绝，且错误信息不携带原文。
     */
    @Test
    void shouldRejectMalformedJson() {
        DemoInsurerAApplicationPipeline pipeline = new DemoInsurerAApplicationPipeline(objectMapper);

        assertThatThrownBy(() -> pipeline.execute("{requestNo:"))
                .isInstanceOf(ApplicationIntakeException.class)
                .hasMessage("租户申请报文不是合法的 A 租户协议 JSON");
    }

    /**
     * JSON 数组无法映射为租户 request 时必须在解析阶段拒绝。
     */
    @Test
    void shouldRejectNonObjectPayload() {
        DemoInsurerBApplicationPipeline pipeline = new DemoInsurerBApplicationPipeline(objectMapper);

        assertThatThrownBy(() -> pipeline.execute("[]"))
                .isInstanceOf(ApplicationIntakeException.class)
                .hasMessage("租户申请报文不是合法的 B 租户协议 JSON");
    }

    /**
     * 正文为 JSON null 时必须在流水线入口拒绝。
     */
    @Test
    void shouldRejectNullJsonPayload() {
        DemoInsurerAApplicationPipeline pipeline = new DemoInsurerAApplicationPipeline(objectMapper);

        assertThatThrownBy(() -> pipeline.execute("null"))
                .isInstanceOf(ApplicationIntakeException.class)
                .hasMessage("租户申请报文必须为 JSON 对象");
    }

    /**
     * 流水线只匹配自己的租户编码与协议版本。
     */
    @Test
    void shouldSupportOnlyOwnTenantAndProtocol() {
        DemoInsurerAApplicationPipeline pipeline = new DemoInsurerAApplicationPipeline(objectMapper);

        assertThat(pipeline.supports(new TenantInfo("demo-insurer-a", "v1"))).isTrue();
        assertThat(pipeline.supports(new TenantInfo("demo-insurer-b", "v1"))).isFalse();
        assertThat(pipeline.supports(new TenantInfo("demo-insurer-a", "v2"))).isFalse();
        assertThat(pipeline.supports(null)).isFalse();
    }
}
