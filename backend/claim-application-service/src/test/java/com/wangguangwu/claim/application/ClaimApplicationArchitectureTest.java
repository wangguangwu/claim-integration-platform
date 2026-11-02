package com.wangguangwu.claim.application;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.wangguangwu.claim.application.interfaces.web.ApiErrorCodeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

/**
 * 理赔申请服务的分层依赖架构测试。
 *
 * <p>规则对应 {@code interfaces → usecase → domain ← infrastructure}：领域层只放业务模型与聚合 Repository，
 * 用例层拥有出站端口与端口模型，基础设施层实现这些端口但不得反向依赖用例编排。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
class ClaimApplicationArchitectureTest {

    private static final JavaClasses APPLICATION_CLASSES =
            new ClassFileImporter().importPackages("com.wangguangwu.claim.application");

    /**
     * 基础设施允许依赖的用例契约包，逐项列出端口与端口模型，避免宽泛匹配放进用例编排类。
     */
    private static final String[] USE_CASE_CONTRACT_PACKAGES = {
            "..usecase.intake.port..", "..usecase.intake.model..",
            "..usecase.material.port..", "..usecase.material.model..",
            "..usecase.underwriting.port..", "..usecase.underwriting.model..",
            "..usecase.audit.port..", "..usecase.audit.model..",
            "..usecase.issue.port..", "..usecase.issue.model..",
            "..usecase.integration.outbox.port..", "..usecase.integration.outbox.model..",
            "..usecase.integration.consumption.port..", "..usecase.integration.consumption.model..",
            "..usecase.integration.compensation.port..", "..usecase.integration.compensation.model..",
            "..usecase.integration.interaction.port..", "..usecase.integration.interaction.model.."
    };

    /**
     * 领域层只能依赖领域自身和受控共享能力，不能依赖任何外层实现。
     */
    @Test
    void domainMustNotDependOnOuterLayers() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..usecase..", "..infrastructure..", "..interfaces..", "..support..")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 用例层通过领域模型与自己的出站端口获取能力，不能直接依赖入站适配或技术实现。
     */
    @Test
    void useCaseMustNotDependOnAdapters() {
        noClasses()
                .that()
                .resideInAPackage("..usecase..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..", "..interfaces..")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 基础设施层实现用例端口与领域仓储，不得反向调用入站适配层。
     */
    @Test
    void infrastructureMustNotDependOnInboundAdapters() {
        noClasses()
                .that()
                .resideInAPackage("..infrastructure..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..interfaces..")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 基础设施层只能依赖用例层的出站端口与端口模型，不得依赖 UseCase、TransactionService 等编排类。
     */
    @Test
    void infrastructureMustDependOnlyOnUseCaseContracts() {
        noClasses()
                .that()
                .resideInAPackage("..infrastructure..")
                .should()
                .dependOnClassesThat(
                        resideInAPackage("..usecase..")
                                .and(resideOutsideOfPackages(USE_CASE_CONTRACT_PACKAGES)))
                .because("基础设施只能依赖用例的出站端口与端口模型，不得依赖用例编排")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 端口与端口模型只描述跨层契约，不得反向依赖用例编排类。
     */
    @Test
    void useCaseContractsMustNotDependOnOrchestration() {
        noClasses()
                .that()
                .resideInAnyPackage(USE_CASE_CONTRACT_PACKAGES)
                .should()
                .dependOnClassesThat(
                        resideInAPackage("..usecase..")
                                .and(resideOutsideOfPackages(USE_CASE_CONTRACT_PACKAGES)))
                .because("端口与端口模型不得反向依赖用例编排类")
                .allowEmptyShould(true)
                .check(APPLICATION_CLASSES);
    }

    /**
     * 入站适配层只能调用用例和领域契约，不能绕过端口直接使用基础设施实现。
     */
    @Test
    void inboundAdaptersMustNotDependOnInfrastructure() {
        noClasses()
                .that()
                .resideInAPackage("..interfaces..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 每个 HTTP 入口控制器都必须声明请求非法码与系统错误码，否则全局异常处理器无法组装失败响应。
     */
    @Test
    void controllersMustDeclareApiErrorCodes() {
        classes()
                .that()
                .areAnnotatedWith(RestController.class)
                .and()
                .resideInAPackage("..interfaces..")
                .should()
                .implement(ApiErrorCodeProvider.class)
                .because("HTTP 入口必须声明请求非法码与系统错误码")
                .check(APPLICATION_CLASSES);
    }
}
