package com.wangguangwu.claim.application.domain.claim;

import com.wangguangwu.claim.foundation.common.util.AssertUtil;
import lombok.Getter;

/**
 * 理赔申请与审核案件的关联关系。
 *
 * <p>一条记录表示审核系统为某次申请签收后返回的案件号。一个申请可对应多个案件号，
 * 案件生命周期由案件服务承载，本服务只记录追加的关联事实。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Getter
public class ClaimCaseRelation {

    private final String tenantCode;
    private final String claimApplicationNo;
    private final String caseNo;

    private ClaimCaseRelation(String tenantCode, String claimApplicationNo, String caseNo) {
        this.tenantCode = tenantCode;
        this.claimApplicationNo = claimApplicationNo;
        this.caseNo = caseNo;
    }

    /**
     * 创建一条案件关联，三个字段均不允许空白。
     *
     * @param tenantCode         租户编码
     * @param claimApplicationNo 平台申请编号
     * @param caseNo             审核签收返回的案件号
     * @return 案件关联
     */
    public static ClaimCaseRelation create(String tenantCode, String claimApplicationNo, String caseNo) {
        AssertUtil.notBlank(tenantCode, "租户编码不能为空");
        AssertUtil.notBlank(claimApplicationNo, "平台申请编号不能为空");
        AssertUtil.notBlank(caseNo, "案件号不能为空");
        return new ClaimCaseRelation(tenantCode, claimApplicationNo, caseNo);
    }
}
