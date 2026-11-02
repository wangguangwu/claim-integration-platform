package com.wangguangwu.claim.application;

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
 * <p>规则对应 {@code interfaces → usecase → domain ← infrastructure}，确保技术实现和入站适配
 * 不会反向侵入领域与用例层。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
class ClaimApplicationArchitectureTest {

    private static final JavaClasses APPLICATION_CLASSES =
            new ClassFileImporter().importPackages("com.wangguangwu.claim.application");

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
     * 用例层通过领域端口获取能力，不能直接依赖入站适配或技术实现。
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
     * 基础设施层实现领域端口，不得反向调用用例或入站适配层。
     */
    @Test
    void infrastructureMustNotDependOnUseCaseOrInboundAdapters() {
        noClasses()
                .that()
                .resideInAPackage("..infrastructure..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..usecase..", "..interfaces..")
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
