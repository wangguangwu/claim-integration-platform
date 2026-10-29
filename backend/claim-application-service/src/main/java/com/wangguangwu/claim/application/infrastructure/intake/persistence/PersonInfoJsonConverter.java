package com.wangguangwu.claim.application.infrastructure.intake.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.claim.PersonInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 人员标准模型与数据库 JSON 列之间的转换器。
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
            return objectMapper.writeValueAsString(personInfo);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("理赔申请人员信息序列化失败", exception);
        }
    }

    /**
     * 从 JSON 列内容还原人员信息。
     *
     * @param personJson 人员 JSON 文本
     * @return 人员信息；入参为空时返回空
     */
    public PersonInfo fromJson(String personJson) {
        if (personJson == null || personJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(personJson, PersonInfo.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("理赔申请人员信息反序列化失败", exception);
        }
    }
}
