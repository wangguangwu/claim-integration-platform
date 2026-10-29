package com.wangguangwu.claim.application.infrastructure.integration.persistence.interaction;

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
 * 申请交互事件数据对象，对应 {@code t_claim_interaction_log} 表。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_claim_interaction_log")
public class InteractionLogDO {

    /** 交互事件记录自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属理赔申请的平台申请编号。 */
    @TableField("application_no")
    private String applicationNo;

    /** 一次交互行为的聚合编号，可关联开始、完成或失败事件。 */
    @TableField("interaction_no")
    private String interactionNo;

    /** 交互业务类型。 */
    @TableField("interaction_type")
    private String interactionType;

    /** 当前事件动作，例如开始、完成、复用或失败。 */
    private String action;

    /** 交互方向，例如请求或回调。 */
    private String direction;

    /** 本次交互的目标外部系统或内部组件。 */
    @TableField("target_system")
    private String targetSystem;

    /** 调用方提供的幂等请求标识。 */
    @TableField("request_id")
    private String requestId;

    /** 链路追踪标识。 */
    @TableField("trace_id")
    private String traceId;

    /** 交互结果状态。 */
    @TableField("result_status")
    private String resultStatus;

    /** 失败时记录的业务或系统错误码。 */
    @TableField("error_code")
    private String errorCode;

    /** 失败时记录的脱敏错误说明。 */
    @TableField("error_message")
    private String errorMessage;

    /** 事件实际发生时间。 */
    @TableField("occurred_at")
    private LocalDateTime occurredAt;
}
