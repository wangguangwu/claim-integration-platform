package com.wangguangwu.claim.application.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 消息消费登记数据对象，对应 {@code t_claim_consumed_event} 表。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Getter
@Setter
@TableName("t_claim_consumed_event")
public class ConsumedEventDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("consumer_group")
    private String consumerGroup;

    @TableField("event_id")
    private String eventId;

    @TableField("application_no")
    private String applicationNo;

    @TableField("consumed_at")
    private LocalDateTime consumedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
