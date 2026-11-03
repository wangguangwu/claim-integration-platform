package com.wangguangwu.claim.application.application.process.compensation;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 补偿处理的公共时间与标识口径。
 *
 * <p>补偿扫描时间统一使用东八区业务时区；补偿请求标识由申请编号推导，
 * 保证多轮补偿对同一申请复用同一请求语义。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
final class CompensationDefaults {

    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private static final String REQUEST_ID_PREFIX = "compensation-";

    private CompensationDefaults() {
    }

    /**
     * 返回当前业务时间。
     *
     * @return 东八区当前时间
     */
    static LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE_ID);
    }

    /**
     * 生成补偿请求标识。
     *
     * @param claimApplicationNo 平台申请编号
     * @return 补偿请求标识
     */
    static String requestId(String claimApplicationNo) {
        return REQUEST_ID_PREFIX + claimApplicationNo;
    }
}
