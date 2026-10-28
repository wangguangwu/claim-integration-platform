package com.wangguangwu.claim.application.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 申请交互事件数据对象，对应 {@code t_interaction_log} 表。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Getter
@Setter
@TableName("t_interaction_log")
public class InteractionLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("application_no")
    private String applicationNo;

    @TableField("interaction_no")
    private String interactionNo;

    @TableField("interaction_type")
    private String interactionType;

    private String action;
    private String direction;

    @TableField("target_system")
    private String targetSystem;

    @TableField("request_id")
    private String requestId;

    @TableField("trace_id")
    private String traceId;

    @TableField("result_status")
    private String resultStatus;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_message")
    private String errorMessage;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;
}
