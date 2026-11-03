/**
 * 理赔申请聚合。
 *
 * <p>以 {@code ClaimApplication} 为唯一聚合根，材料与问题件为聚合内实体，分别位于 {@code material}、
 * {@code issue} 子包，人员信息为 {@code person} 子包中的值对象；聚合仓储与领域错误码放在子包。
 * 案件关联事实不属于本聚合，位于 {@code domain.claimcase}。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.application.domain.claim;
