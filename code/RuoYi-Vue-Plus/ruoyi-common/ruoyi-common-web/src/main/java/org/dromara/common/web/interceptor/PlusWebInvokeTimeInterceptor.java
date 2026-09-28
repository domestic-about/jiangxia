package org.dromara.common.web.interceptor;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.web.filter.RepeatedlyRequestWrapper;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * web的调用时间统计拦截器
 *
 * <p>★ 请求日志脱敏（独立验收 V19，2026-09-23）：上游只把口令类字段从日志里删掉，
 * 外部提交里的供体姓名、住院号、手机号会以明文写进 {@code [PLUS]开始请求} 那一行。现在分两类：
 * <ul>
 *   <li><b>口令类</b>（{@link SystemConstants#EXCLUDE_PROPERTIES}）：照旧整个字段删掉；</li>
 *   <li><b>个人信息与凭据类</b>（{@link #MASKED_FIELDS}）：值替换成掩码 —— 手机号留前 3 后 4
 *       （{@code 138****0011}），其余一律 {@value #MASK}。字段还在，排查时看得出「传没传」。</li>
 * </ul>
 * JSON 体（任意深度、数组里）与表单 / 查询参数两条路径都走同一份清单；字段名比较不区分大小写。
 *
 * @author Lion Li
 * @since 3.3.0
 */
@Slf4j
public class PlusWebInvokeTimeInterceptor implements HandlerInterceptor {

    /**
     * 掩码。
     */
    static final String MASK = "******";

    /**
     * 要打码的字段（小写比较）：供体 / 患者身份（ADR-0006 的三个加密列）、手机号、证件号，
     * 以及登录凭据（微信 code、手机号快速验证 code、对象存储密钥、token）。
     */
    static final Set<String> MASKED_FIELDS = Stream.of(
            "donorName", "hospitalNo", "patientNo",
            "phone", "phonenumber", "mobile", "contactPhone", "tel", "telephone",
            "idCard", "idNo", "email",
            "xcxCode", "phoneCode", "accessKey", "secretKey", "clientSecret",
            "accessToken", "refreshToken", "token")
        .map(name -> name.toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());

    /**
     * 按手机号规则打码的字段（小写）：留前 3 后 4，其余打星。
     */
    private static final Set<String> PHONE_FIELDS = Stream.of(
            "phone", "phonenumber", "mobile", "contactPhone", "tel", "telephone")
        .map(name -> name.toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());

    /**
     * 整个删掉的字段（小写）：上游的口令类清单。
     */
    private static final Set<String> REMOVED_FIELDS = Stream.of(SystemConstants.EXCLUDE_PROPERTIES)
        .map(name -> name.toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());

    private final static ThreadLocal<StopWatch> KEY_CACHE = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String url = request.getMethod() + " " + request.getRequestURI();
        // 打印请求参数
        if (isJsonRequest(request)) {
            String jsonParam = "";
            if (request instanceof RepeatedlyRequestWrapper) {
                jsonParam = IoUtil.read(request.getReader());
                if (StringUtils.isNotBlank(jsonParam)) {
                    jsonParam = sanitizeJson(jsonParam, JsonUtils.getObjectMapper());
                }
            }
            log.info("[PLUS]开始请求 => URL[{}],参数类型[json],参数:[{}]", url, jsonParam);
        } else {
            Map<String, String[]> parameterMap = request.getParameterMap();
            if (MapUtil.isNotEmpty(parameterMap)) {
                String parameters = JsonUtils.toJsonString(sanitizeParams(parameterMap));
                log.info("[PLUS]开始请求 => URL[{}],参数类型[param],参数:[{}]", url, parameters);
            } else {
                log.info("[PLUS]开始请求 => URL[{}],无参数", url);
            }
        }

        StopWatch stopWatch = new StopWatch();
        KEY_CACHE.set(stopWatch);
        stopWatch.start();

        return true;
    }

    /**
     * JSON 请求体脱敏：口令类字段删掉，个人信息与凭据类字段打码（任意深度）。
     * 解析不了的请求体（不是合法 JSON）<b>一个字都不打印</b>，只记长度 —— 半截 JSON 里同样可能有明文。
     */
    static String sanitizeJson(String json, ObjectMapper objectMapper) {
        try {
            JsonNode rootNode = objectMapper.readTree(json);
            if (rootNode == null) {
                return "";
            }
            sanitizeNode(rootNode);
            return rootNode.toString();
        } catch (JsonProcessingException e) {
            return "<非 JSON 请求体，" + json.length() + " 字符，不打印>";
        }
    }

    /**
     * 表单 / 查询参数脱敏（返回新 Map，不改请求本身的参数表）。
     */
    static Map<String, String[]> sanitizeParams(Map<String, String[]> parameterMap) {
        Map<String, String[]> map = new LinkedHashMap<>();
        parameterMap.forEach((name, values) -> {
            String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
            if (REMOVED_FIELDS.contains(key)) {
                return;
            }
            if (MASKED_FIELDS.contains(key) && values != null) {
                String[] masked = new String[values.length];
                for (int i = 0; i < values.length; i++) {
                    masked[i] = mask(key, values[i]);
                }
                map.put(name, masked);
            } else {
                map.put(name, values);
            }
        });
        return map;
    }

    private static void sanitizeNode(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            // 先收集字段名再改（避免 ConcurrentModification）
            List<String> names = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                String key = name.toLowerCase(Locale.ROOT);
                JsonNode child = objectNode.get(name);
                if (REMOVED_FIELDS.contains(key)) {
                    objectNode.remove(name);
                } else if (MASKED_FIELDS.contains(key) && child != null && !child.isNull()) {
                    objectNode.set(name, child.isValueNode()
                        ? TextNode.valueOf(mask(key, child.asText()))
                        : TextNode.valueOf(MASK));
                } else {
                    // 递归处理子节点
                    sanitizeNode(child);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (JsonNode child : arrayNode) {
                sanitizeNode(child);
            }
        }
    }

    /**
     * 单个值打码：手机号类且是 11 位数字 → 留前 3 后 4；其余一律 {@value #MASK}（不泄露长度）。
     * 空值原样（「没传」本身不敏感，排查时有用）。
     */
    static String mask(String lowerCaseField, String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (PHONE_FIELDS.contains(lowerCaseField) && value.matches("\\d{11}")) {
            return value.substring(0, 3) + "****" + value.substring(7);
        }
        return MASK;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        StopWatch stopWatch = KEY_CACHE.get();
        if (ObjectUtil.isNotNull(stopWatch)) {
            stopWatch.stop();
            log.info("[PLUS]结束请求 => URL[{}],耗时:[{}]毫秒", request.getMethod() + " " + request.getRequestURI(), stopWatch.getDuration().toMillis());
            KEY_CACHE.remove();
        }
    }

    /**
     * 判断本次请求的数据类型是否为json
     *
     * @param request request
     * @return boolean
     */
    private boolean isJsonRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType != null) {
            return StringUtils.startsWithIgnoreCase(contentType, MediaType.APPLICATION_JSON_VALUE);
        }
        return false;
    }

}
