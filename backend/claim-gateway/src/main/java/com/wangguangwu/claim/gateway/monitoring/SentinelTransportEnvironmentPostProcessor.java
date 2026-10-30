package com.wangguangwu.claim.gateway.monitoring;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 在容器刷新前把 Sentinel 控制台地址写入系统属性。
 *
 * <p>Sentinel 只读系统属性，且心跳与命令通道在首次使用 Sentinel 时初始化，
 * 必须早于任何 Bean 的 {@code @PostConstruct}，因此用 {@link EnvironmentPostProcessor} 而不是配置类。
 * {@code claim.gateway.sentinel.dashboard-server} 留空表示关闭上报，不写任何属性。</p>
 *
 * @author wangguangwu
 * @since 2026-10-30
 */
public class SentinelTransportEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String DASHBOARD_SERVER_PROPERTY = "claim.gateway.sentinel.dashboard-server";
    private static final String CLIENT_IP_PROPERTY = "claim.gateway.sentinel.client-ip";
    private static final String API_PORT_PROPERTY = "claim.gateway.sentinel.api-port";
    private static final String APPLICATION_NAME_PROPERTY = "spring.application.name";

    private static final String SENTINEL_DASHBOARD_SERVER = "csp.sentinel.dashboard.server";
    private static final String SENTINEL_API_PORT = "csp.sentinel.api.port";
    private static final String SENTINEL_CLIENT_IP = "csp.sentinel.heartbeat.client.ip";
    private static final String SENTINEL_PROJECT_NAME = "project.name";

    private static final String DEFAULT_API_PORT = "8719";
    private static final String DEFAULT_PROJECT_NAME = "claim-gateway";

    private final Log log;

    public SentinelTransportEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(SentinelTransportEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String dashboardServer = environment.getProperty(DASHBOARD_SERVER_PROPERTY, "").trim();
        if (dashboardServer.isEmpty()) {
            log.info("Sentinel 控制台地址未配置，跳过上报注册");
            return;
        }
        String apiPort = environment.getProperty(API_PORT_PROPERTY, DEFAULT_API_PORT);
        String clientIp = environment.getProperty(CLIENT_IP_PROPERTY, "").trim();
        System.setProperty(SENTINEL_DASHBOARD_SERVER, dashboardServer);
        System.setProperty(SENTINEL_API_PORT, apiPort);
        System.setProperty(SENTINEL_PROJECT_NAME,
                environment.getProperty(APPLICATION_NAME_PROPERTY, DEFAULT_PROJECT_NAME));
        if (!clientIp.isEmpty()) {
            System.setProperty(SENTINEL_CLIENT_IP, clientIp);
        }
        log.info("Sentinel 控制台上报已注册: dashboardServer=" + dashboardServer
                + ", apiPort=" + apiPort
                + ", clientIp=" + (clientIp.isEmpty() ? "auto" : clientIp));
    }

    /**
     * 排在其它环境后处理器之后，确保配置值已完全解析。
     *
     * @return 排序值
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
