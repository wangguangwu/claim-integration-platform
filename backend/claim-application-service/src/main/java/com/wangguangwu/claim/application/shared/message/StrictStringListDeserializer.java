package com.wangguangwu.claim.application.shared.message;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 案件号数组的严格反序列化器，数组元素必须是 JSON 字符串。
 *
 * <p>Jackson 默认会把数字静默转成字符串，这里显式拒绝，避免非法报文被接受。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
public class StrictStringListDeserializer extends JsonDeserializer<List<String>> {

    /**
     * 读取字符串数组；null 交由装配器按业务规则处理。
     *
     * @param parser  JSON 解析器
     * @param context 反序列化上下文
     * @return 字符串列表；JSON null 返回 null
     * @throws IOException 读取失败或元素不是字符串
     */
    @Override
    public List<String> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node.isNull()) {
            return null;
        }
        if (!node.isArray()) {
            throw JsonMappingException.from(parser, "案件号必须为字符串数组");
        }
        List<String> caseNos = new ArrayList<>(node.size());
        for (JsonNode element : node) {
            if (!element.isTextual()) {
                throw JsonMappingException.from(parser, "案件号必须为字符串，实际为 " + element.getNodeType());
            }
            caseNos.add(element.textValue());
        }
        return caseNos;
    }
}
