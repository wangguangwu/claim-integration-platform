package com.wangguangwu.claim.application.domain.claim.person;

import lombok.EqualsAndHashCode;

/**
 * 出险人身份信息：本次保险事故的实际受害人。
 *
 * <p>四个基础字段与相等语义全部继承 {@link PersonInfo}，本类型只表达业务角色。</p>
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
@EqualsAndHashCode(callSuper = true)
public final class AccidentPerson extends PersonInfo {

    /**
     * 创建出险人身份信息。
     *
     * @param name            姓名
     * @param gender          性别
     * @param certificateType 证件类型
     * @param certificateNo   证件号码
     */
    public AccidentPerson(String name, Gender gender, String certificateType, String certificateNo) {
        super(name, gender, certificateType, certificateNo);
    }
}
