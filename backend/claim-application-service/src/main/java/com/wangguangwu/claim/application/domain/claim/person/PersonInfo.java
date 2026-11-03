package com.wangguangwu.claim.application.domain.claim.person;

import com.wangguangwu.claim.foundation.common.util.AssertUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 理赔申请参与人员的标准化身份信息基类，四个业务角色各自继承本类。
 *
 * <p>基类只承载姓名、性别、证件类型与证件号四个基础字段，角色差异由子类表达：
 * 申请人、主被保险人、出险人和领款人各用独立类型传递，装配错位无法通过编译。
 * 证件号属于敏感信息，本类不提供 {@code toString}，日志与异常信息只允许输出角色与申请编号。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
public abstract sealed class PersonInfo permits Applicant, PrimaryInsured, AccidentPerson, Payee {

    /** 姓名。 */
    private final String name;

    /** 性别。 */
    private final Gender gender;

    /** 证件类型。 */
    private final String certificateType;

    /** 证件号码。 */
    private final String certificateNo;

    /**
     * 创建人员身份信息，四个基础字段是人员不变式，非法输入在构造阶段拒绝。
     *
     * @param name            姓名
     * @param gender          性别
     * @param certificateType 证件类型
     * @param certificateNo   证件号码
     */
    protected PersonInfo(String name, Gender gender, String certificateType, String certificateNo) {
        AssertUtil.notBlank(name, "姓名不能为空");
        AssertUtil.notNull(gender, "性别不能为空");
        this.name = name;
        this.gender = gender;
        AssertUtil.notBlank(certificateType, "证件类型不能为空");
        AssertUtil.notBlank(certificateNo, "证件号不能为空");
        this.certificateType = certificateType;
        this.certificateNo = certificateNo;
    }
}
