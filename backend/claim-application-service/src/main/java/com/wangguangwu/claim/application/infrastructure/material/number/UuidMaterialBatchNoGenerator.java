package com.wangguangwu.claim.application.infrastructure.material.number;

import com.wangguangwu.claim.application.domain.material.MaterialBatchNoGenerator;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 基于业务日期与 UUID 随机段的材料批次编号生成器。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Component
public class UuidMaterialBatchNoGenerator implements MaterialBatchNoGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    /**
     * 生成 B 加日期加 UUID 随机段的批次编号。
     *
     * @return 材料批次编号
     */
    @Override
    public String nextBatchNo() {
        return "B" + LocalDate.now(BUSINESS_ZONE_ID).format(DATE_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "");
    }
}
