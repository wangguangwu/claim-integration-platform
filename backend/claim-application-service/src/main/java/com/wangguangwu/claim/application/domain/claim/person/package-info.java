/**
 * 理赔申请参与人员的值对象。
 *
 * <p>四个业务角色由 {@code PersonInfo} 密封基类约束，各用独立类型传递，装配错位无法通过编译；
 * 人员信息只承载身份字段，不感知申请状态与持久化。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
package com.wangguangwu.claim.application.domain.claim.person;
