package com.wangguangwu.claim.application.infrastructure.integration.persistence.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 待投递事件数据对象，对应 {@code t_claim_outbox_event} 表。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_claim_outbox_event")
public class OutboxEventDO {

    /** Outbox 记录自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局唯一事件标识。 */
    @TableField("event_id")
    private String eventId;

    /** 事件类型。 */
    @TableField("event_type")
    private String eventType;

    /** 事件载荷版本，用于消费者兼容演进。 */
    @TableField("event_version")
    private Integer eventVersion;

    /** 事件所属理赔申请的平台申请编号。 */
    @TableField("claim_application_no")
    private String claimApplicationNo;

    /** 事件所属租户编码。 */
    @TableField("tenant_code")
    private String tenantCode;

    /** 不含敏感信息的事件载荷 JSON。 */
    private String payload;

    /** 投递状态枚举名称。 */
    private String status;

    /** 已执行的投递重试次数。 */
    @TableField("retry_count")
    private Integer retryCount;

    /** 下一次允许投递的时间；首次投递或无需重试时为空。 */
    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;

    /** 成功投递到消息系统的时间；未投递时为空。 */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /** 事件创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 事件记录最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
