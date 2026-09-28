package org.dromara.lqg.sample.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.lqg.sample.domain.bo.PatchBody;
import org.springframework.stereotype.Component;

/**
 * 把原始 JSON 请求体读成 {@link PatchBody}（FIX V28 / V33 的补丁语义：没带 = 不改、带了空值 = 清空）。
 *
 * <p>单独一个 bean、而不是让各 controller 直接注入 {@code ObjectMapper}：mp 包的契约测试禁止出现任何
 * {@code *Mapper} 字段（防 MyBatis mapper 绕开 service），{@code ObjectMapper} 恰好也叫这个后缀；
 * 同时「用容器里那一个 ObjectMapper 解析」这件事只写一处。
 *
 * @author FIX-V28
 */
@Component
@RequiredArgsConstructor
public class PatchBodyReader {

    private final ObjectMapper objectMapper;

    /**
     * 读补丁：请求体必须是 JSON 对象；字段类型不对 → 400（只报字段名）。
     */
    public <T> PatchBody<T> read(JsonNode body, Class<T> type) {
        return PatchBody.parse(objectMapper, body, type);
    }

}
