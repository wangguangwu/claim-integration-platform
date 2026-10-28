package com.wangguangwu.claim.application.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 待投递事件数据对象，对应 {@code t_claim_outbox_event} 表。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Getter
@Setter
@TableName("t_claim_outbox_event")
public class OutboxEventDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("event_id")
    private String eventId;

    @TableField("event_type")
    private String eventType;

    @TableField("event_version")
    private Integer eventVersion;

    @TableField("application_no")
    private String applicationNo;

    @TableField("tenant_code")
    private String tenantCode;

    private String payload;
    private String status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
