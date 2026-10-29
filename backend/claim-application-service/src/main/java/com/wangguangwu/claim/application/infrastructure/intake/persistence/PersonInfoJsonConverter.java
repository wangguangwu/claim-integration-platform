package com.wangguangwu.claim.application.infrastructure.intake.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.claim.person.AccidentPerson;
import com.wangguangwu.claim.application.domain.claim.person.Applicant;
import com.wangguangwu.claim.application.domain.claim.person.Gender;
import com.wangguangwu.claim.application.domain.claim.person.Payee;
import com.wangguangwu.claim.application.domain.claim.person.PersonInfo;
import com.wangguangwu.claim.application.domain.claim.person.PrimaryInsured;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 人员标准模型与数据库 JSON 列之间的转换器。
 *
 * <p>人员角色由列名决定，JSON 内容只保存四个基础字段，因此写方向按 {@link PersonInfo} 基类统一序列化，
 * 读方向由调用方按列指定角色类型重建值对象。</p>
 *
 * @author wangguangwu
 * @since 2026-10-28
 */
@RequiredArgsConstructor
@Component
public class PersonInfoJsonConverter {

    private final ObjectMapper objectMapper;

    /**
     * 将人员信息序列化为 JSON 列内容。
     *
     * @param personInfo 人员信息
     * @return JSON 文本；入参为空时返回空
     */
    public String toJson(PersonInfo personInfo) {
        if (personInfo == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(PersonSnapshot.of(personInfo));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("理赔申请人员信息序列化失败", exception);
        }
    }

    /**
     * 从申请人 JSON 列还原申请人身份信息。
     *
     * @param applicantJson 人员 JSON 文本
     * @return 申请人身份信息；入参为空时返回空
     */
    public Applicant toApplicant(String applicantJson) {
        return fromJson(applicantJson, PersonSnapshot::toApplicant);
    }

    /**
     * 从主被保险人 JSON 列还原主被保险人身份信息。
     *
     * @param primaryInsuredJson 人员 JSON 文本
     * @return 主被保险人身份信息；入参为空时返回空
     */
    public PrimaryInsured toPrimaryInsured(String primaryInsuredJson) {
        return fromJson(primaryInsuredJson, PersonSnapshot::toPrimaryInsured);
    }

    /**
     * 从出险人 JSON 列还原出险人身份信息。
     *
     * @param accidentPersonJson 人员 JSON 文本
     * @return 出险人身份信息；入参为空时返回空
     */
    public AccidentPerson toAccidentPerson(String accidentPersonJson) {
        return fromJson(accidentPersonJson, PersonSnapshot::toAccidentPerson);
    }

    /**
     * 从领款人 JSON 列还原领款人身份信息。
     *
     * @param payeeJson 人员 JSON 文本
     * @return 领款人身份信息；入参为空时返回空
     */
    public Payee toPayee(String payeeJson) {
        return fromJson(payeeJson, PersonSnapshot::toPayee);
    }

    private <T extends PersonInfo> T fromJson(String personJson, Function<PersonSnapshot, T> roleFactory) {
        if (personJson == null || personJson.isBlank()) {
            return null;
        }
        try {
            return roleFactory.apply(objectMapper.readValue(personJson, PersonSnapshot.class));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("理赔申请人员信息反序列化失败", exception);
        }
    }

    /**
     * 人员 JSON 列的存储结构，只描述落库字段，不承担业务角色。
     *
     * @param name            姓名
     * @param gender          性别枚举名
     * @param certificateType 证件类型
     * @param certificateNo   证件号码
     */
    private record PersonSnapshot(String name, String gender, String certificateType, String certificateNo) {

        private static PersonSnapshot of(PersonInfo personInfo) {
            Gender gender = personInfo.gender();
            return new PersonSnapshot(personInfo.name(), gender == null ? null : gender.name(),
                    personInfo.certificateType(), personInfo.certificateNo());
        }

        private Applicant toApplicant() {
            return new Applicant(name, genderValue(), certificateType, certificateNo);
        }

        private PrimaryInsured toPrimaryInsured() {
            return new PrimaryInsured(name, genderValue(), certificateType, certificateNo);
        }

        private AccidentPerson toAccidentPerson() {
            return new AccidentPerson(name, genderValue(), certificateType, certificateNo);
        }

        private Payee toPayee() {
            return new Payee(name, genderValue(), certificateType, certificateNo);
        }

        private Gender genderValue() {
            return gender == null ? null : Gender.valueOf(gender);
        }
    }
}
