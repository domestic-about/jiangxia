package org.dromara.lqg.sample.domain.bo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.common.core.exception.ServiceException;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 「补丁」请求体：解析好的入参 + 请求体里<b>出现过</b>的键（值为 {@code null} 也算出现）。
 *
 * <p>★ 为什么要有它（FIX V28 / V33）：补丁语义要区分三种情况，而普通的 {@code @RequestBody SomeBo}
 * 只能看到两种 —— 反序列化之后「没传」和「传了 null」都是 null：
 * <ul>
 *   <li><b>没传这个键</b> → 不改（沿用库里现值）；</li>
 *   <li><b>传了空值</b>（{@code null} 或空串）→ 清空；不许清空的字段（必填项）→ 明确报错 400，不假装成功；</li>
 *   <li><b>传了值</b> → 改成它。</li>
 * </ul>
 * 以前的写法是「空值 = 不改」，于是用户在小程序 / 工作台里把一个字段清空、点保存，
 * 界面提示「已保存」而库里还是旧值（独立验收 V28；工作台石蜡包埋的工序时间、冻存的转液氮时间同一病灶，V33）。
 *
 * <p>★ 解析用 Spring 容器里那一个 {@link ObjectMapper}（{@code @JsonFormat} 的日期格式、
 * 忽略未知键等配置与普通 {@code @RequestBody} 完全一致）；类型不对（如日期写成 ISO 的 {@code T}）→ 400，
 * 提示里只有字段名，不回吐 Java 类名。
 *
 * @param value 解析好的入参
 * @param keys  请求体里出现过的顶层键
 * @param <T>   入参类型
 * @author FIX-V28
 */
public record PatchBody<T>(T value, Set<String> keys) {

    /**
     * 这个键在请求体里出现过没有（值为 null 也算出现）。
     */
    public boolean has(String key) {
        return keys.contains(key);
    }

    /**
     * 把原始 JSON 解析成补丁。
     *
     * @param mapper 容器里的 ObjectMapper
     * @param body   原始请求体
     * @param type   入参类型
     * @throws ServiceException 400：请求体不是 JSON 对象，或某个字段的值类型不对
     */
    public static <T> PatchBody<T> parse(ObjectMapper mapper, JsonNode body, Class<T> type) {
        if (body == null || body.isNull() || body.isMissingNode()) {
            throw new ServiceException("请求体不能为空", 400);
        }
        if (!body.isObject()) {
            throw new ServiceException("请求体必须是 JSON 对象", 400);
        }
        T value;
        try {
            value = mapper.treeToValue(body, type);
        } catch (JsonProcessingException e) {
            throw new ServiceException("请求参数格式错误：" + fieldOf(e) + "的值不合法", 400);
        }
        Set<String> keys = new LinkedHashSet<>();
        body.fieldNames().forEachRemaining(keys::add);
        return new PatchBody<>(value, Collections.unmodifiableSet(keys));
    }

    /**
     * 测试 / 内部调用用：直接给定入参与出现过的键。
     */
    public static <T> PatchBody<T> of(T value, Set<String> keys) {
        return new PatchBody<>(value, keys == null ? Set.of() : Set.copyOf(keys));
    }

    private static String fieldOf(JsonProcessingException e) {
        if (e instanceof JsonMappingException m && m.getPath() != null && !m.getPath().isEmpty()) {
            return "「" + m.getPath().stream()
                .map(r -> r.getFieldName() != null ? r.getFieldName() : "[" + r.getIndex() + "]")
                .collect(Collectors.joining(".")) + "」";
        }
        return "请求体";
    }

}
