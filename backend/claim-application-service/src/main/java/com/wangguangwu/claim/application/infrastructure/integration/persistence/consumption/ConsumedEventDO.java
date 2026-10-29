package com.wangguangwu.claim.application.infrastructure.integration.persistence.consumption;

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

    /** 消费登记记录自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消费消息的消费者组，用于区分独立消费语义。 */
    @TableField("consumer_group")
    private String consumerGroup;

    /** 已完成消费的全局唯一事件标识。 */
    @TableField("event_id")
    private String eventId;

    /** 消息所属理赔申请的平台申请编号。 */
    @TableField("application_no")
    private String applicationNo;

    /** 消费业务处理成功的时间。 */
    @TableField("consumed_at")
    private LocalDateTime consumedAt;

    /** 消费登记创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 消费登记最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
