package com.wangguangwu.claim.application;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.wangguangwu.claim.application.application.exception.ApplicationException;
import com.wangguangwu.claim.application.application.port.AuditSystemPort;
import com.wangguangwu.claim.application.application.port.InteractionLogRepository;
import com.wangguangwu.claim.application.application.port.MaterialFileProcessor;
import com.wangguangwu.claim.application.application.port.UnderwritingSystemPort;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.claim.material.Material;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.interfaces.web.ApiErrorCodeProvider;
import com.wangguangwu.claim.application.shared.exception.BusinessException;
import com.wangguangwu.claim.application.shared.exception.DomainException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

/**
 * 理赔申请服务的分层依赖架构测试。
 *
 * <p>规则对应 {@code interfaces → application → domain ← infrastructure}：接口层只做协议适配，
 * 应用层拥有命令、用例编排与出站端口，领域层只放业务模型与聚合仓储，
 * 基础设施层实现端口与仓储但不得反向依赖接口或应用编排，共享契约位于依赖图底部。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
class ClaimApplicationArchitectureTest {

    private static final String BASE = "com.wangguangwu.claim.application";

    private static final String INTERFACES = BASE + ".interfaces..";

    private static final String APPLICATION = BASE + ".application..";

    private static final String DOMAIN = BASE + ".domain..";

    private static final String INFRASTRUCTURE = BASE + ".infrastructure..";

    private static final String SHARED = BASE + ".shared..";

    private static final JavaClasses APPLICATION_CLASSES =
            new ClassFileImporter().importPackages(BASE);

    /**
     * 应用层的出站契约包，基础设施只允许依赖这些包，避免直接引用用例编排类。
     */
    private static final String[] APPLICATION_CONTRACT_PACKAGES = {
            BASE + ".application.port..",
            BASE + ".application.query.model.."
    };

    /**
     * 共享契约位于依赖图底部，只能被其他层引用，不得反向依赖任何业务分层。
     */
    @Test
    void sharedMustNotDependOnOuterLayers() {
        noClasses()
                .that()
                .resideInAPackage(SHARED)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(DOMAIN, APPLICATION, INTERFACES, INFRASTRUCTURE)
                .because("共享契约不得反向依赖业务分层")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 领域层统一抛出领域异常，生产代码不得绕过子类直接依赖异常基类。
     */
    @Test
    void domainMustUseDomainExceptionInsteadOfBusinessException() {
        noClasses()
                .that()
                .resideInAPackage(DOMAIN)
                .and()
                .doNotHaveFullyQualifiedName(DomainException.class.getName())
                .and()
                .haveSimpleNameNotEndingWith("Test")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(BusinessException.class.getName())
                .because("领域层统一抛 DomainException，不得直接依赖 BusinessException")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 应用层统一抛出应用异常，生产代码不得绕过子类直接依赖异常基类。
     */
    @Test
    void applicationMustUseApplicationExceptionInsteadOfBusinessException() {
        noClasses()
                .that()
                .resideInAPackage(APPLICATION)
                .and()
                .doNotHaveFullyQualifiedName(ApplicationException.class.getName())
                .and()
                .haveSimpleNameNotEndingWith("Test")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(BusinessException.class.getName())
                .because("应用层统一抛 ApplicationException，不得直接依赖 BusinessException")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 应用层与领域层只依赖统一异常体系，不得把持久化异常类型泄漏到业务代码。
     */
    @Test
    void businessLayersMustNotDependOnPersistenceExceptions() {
        noClasses()
                .that()
                .resideInAnyPackage(APPLICATION, DOMAIN)
                .and()
                .haveSimpleNameNotEndingWith("Test")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework.dao..")
                .because("业务分层不得依赖 Spring 持久化异常，唯一键冲突由仓储翻译为共享异常")
                .allowEmptyShould(true)
                .check(APPLICATION_CLASSES);
    }

    /**
     * 领域层保持时间无关，当前时间由应用层注入，保证聚合行为可重放与可测试。
     */
    @Test
    void domainMustNotReadSystemClock() {
        noClasses()
                .that()
                .resideInAPackage(DOMAIN)
                .should()
                .callMethod(LocalDateTime.class, "now")
                .because("领域层不得直接读取系统时钟，时间由应用层参数传入")
                .allowEmptyShould(true)
                .check(APPLICATION_CLASSES);
    }

    /**
     * 领域层只能依赖领域自身和受控共享能力，不能依赖任何外层实现。
     */
    @Test
    void domainMustNotDependOnOuterLayers() {
        noClasses()
                .that()
                .resideInAPackage(DOMAIN)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(APPLICATION, INTERFACES, INFRASTRUCTURE)
                .because("领域层不得依赖应用、接口与基础设施")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 应用层通过领域模型与自己的出站端口获取能力，不能直接依赖入站适配或技术实现。
     */
    @Test
    void applicationMustNotDependOnAdapters() {
        noClasses()
                .that()
                .resideInAPackage(APPLICATION)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(INTERFACES, INFRASTRUCTURE)
                .because("应用层不得依赖接口与基础设施")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 基础设施层实现应用端口与领域仓储，不得反向调用入站适配层。
     */
    @Test
    void infrastructureMustNotDependOnInboundAdapters() {
        noClasses()
                .that()
                .resideInAPackage(INFRASTRUCTURE)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(INTERFACES)
                .because("基础设施不得依赖入站适配")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 基础设施层只能依赖应用层的出站端口与端口模型，不得依赖用例编排、命令与查询用例。
     */
    @Test
    void infrastructureMustDependOnlyOnApplicationContracts() {
        noClasses()
                .that()
                .resideInAPackage(INFRASTRUCTURE)
                .should()
                .dependOnClassesThat(
                        resideInAPackage(APPLICATION)
                                .and(resideOutsideOfPackages(APPLICATION_CONTRACT_PACKAGES)))
                .because("基础设施只能依赖应用层的出站端口与端口模型，不得依赖用例编排")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 外部系统端口实现统一放在防腐层，技术适配包只承接 Redis、锁、发号等技术设施。
     */
    @Test
    void externalSystemImplementationsMustResideInAcl() {
        classes()
                .that()
                .implement(AuditSystemPort.class)
                .or()
                .implement(UnderwritingSystemPort.class)
                .or()
                .implement(MaterialFileProcessor.class)
                .should()
                .resideInAPackage(BASE + ".infrastructure.acl..")
                .because("外部系统实现统一放在基础设施防腐层")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 端口与端口模型只描述跨层契约，不得反向依赖用例编排类。
     */
    @Test
    void applicationContractsMustNotDependOnOrchestration() {
        noClasses()
                .that()
                .resideInAnyPackage(APPLICATION_CONTRACT_PACKAGES)
                .should()
                .dependOnClassesThat(
                        resideInAPackage(APPLICATION)
                                .and(resideOutsideOfPackages(APPLICATION_CONTRACT_PACKAGES)))
                .because("端口与端口模型不得反向依赖用例编排类")
                .allowEmptyShould(true)
                .check(APPLICATION_CLASSES);
    }

    /**
     * 入站适配层只能调用应用用例、领域契约与共享报文，不能绕过端口直接使用基础设施实现。
     */
    @Test
    void inboundAdaptersMustNotDependOnInfrastructure() {
        noClasses()
                .that()
                .resideInAPackage(INTERFACES)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(INFRASTRUCTURE)
                .because("接口层不得依赖基础设施实现")
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
                .resideInAPackage(INTERFACES)
                .should()
                .implement(ApiErrorCodeProvider.class)
                .because("HTTP 入口必须声明请求非法码与系统错误码")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 查询链路只读数据库，不得写入交互轨迹。
     */
    @Test
    void queryMustNotWriteInteractionLogs() {
        noClasses()
                .that()
                .resideInAPackage(BASE + ".application.query..")
                .or()
                .haveFullyQualifiedName(BASE + ".infrastructure.persistence.MyBatisClaimApplicationDetailQuery")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(InteractionLogRepository.class.getName())
                .because("查询不写交互轨迹")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 查询链路直接使用读模型与 DO Mapper，不得加载领域聚合。
     */
    @Test
    void queryMustNotLoadAggregate() {
        noClasses()
                .that()
                .resideInAPackage(BASE + ".application.query..")
                .or()
                .haveFullyQualifiedName(BASE + ".infrastructure.persistence.MyBatisClaimApplicationDetailQuery")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(ClaimApplicationRepository.class.getName())
                .because("查询不加载领域聚合")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 材料状态迁移只能由聚合根触发，其他层不得直接调用实体行为绕过申请状态机。
     */
    @Test
    void materialStateChangesOnlyThroughAggregate() {
        noClasses()
                .that()
                .resideOutsideOfPackages(BASE + ".domain.claim..")
                .and()
                .haveSimpleNameNotEndingWith("Test")
                .should()
                .callMethod(Material.class, "softDelete", LocalDateTime.class)
                .orShould()
                .callMethod(Material.class, "applyProcessResult", boolean.class, String.class)
                .because("材料状态只能由 ClaimApplication 聚合根迁移")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 问题件关闭只能由聚合根触发，保证关闭与申请状态迁移同事务。
     */
    @Test
    void issueStateChangesOnlyThroughAggregate() {
        noClasses()
                .that()
                .resideOutsideOfPackages(BASE + ".domain.claim..")
                .and()
                .haveSimpleNameNotEndingWith("Test")
                .should()
                .callMethod(ClaimApplicationIssue.class, "close", String.class, LocalDateTime.class)
                .because("问题件关闭只能由 ClaimApplication 聚合根触发")
                .check(APPLICATION_CLASSES);
    }

    /**
     * 反水化工厂只服务于持久化还原与领域测试，其他层不得用 restore 绕过创建工厂。
     */
    @Test
    void aggregateRestoreOnlyUsedByPersistence() {
        noClasses()
                .that()
                .resideOutsideOfPackages(BASE + ".domain.claim..", BASE + ".infrastructure.persistence..")
                .and()
                .haveSimpleNameNotEndingWith("Test")
                .should()
                .callMethodWhere(DescribedPredicate.describe("调用聚合或实体的 restore 工厂",
                        call -> call.getName().equals("restore")
                                && (call.getTarget().getOwner().isEquivalentTo(ClaimApplication.class)
                                || call.getTarget().getOwner().isEquivalentTo(Material.class)
                                || call.getTarget().getOwner().isEquivalentTo(ClaimApplicationIssue.class))))
                .because("聚合与实体的反水化只能由持久化层调用")
                .check(APPLICATION_CLASSES);
    }
}
