package org.dromara.lqg.embed.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.dto.DictDataDTO;
import org.dromara.common.core.service.DictService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 石蜡包埋域用到的两本字典（ticket §2：染色值必须在 {@code lqg_stain_type} 里、
 * marker 表达必须在 {@code lqg_marker_expr} 里）。
 *
 * <p>★ <b>字典不许在本票里插</b>（ticket §0 口径 10）：两本字典由 SYS-BASE-001 的
 * {@code V202609210810__SYS-BASE-001-lqg-dicts.sql} 建好，本类只读。
 *
 * <p>★ 读不到/空字典时返回空数组，而 {@code StainRules} 的判据是
 * 「空字典 → 只按固定五个 value 校验」：字典服务临时故障不该让整张表写不进去
 * （那会把「字典挂了」伪装成「实现拒绝一切」）。
 *
 * @author EMBED-MODEL-001
 */
@Service
@RequiredArgsConstructor
public class EmbedDictService {

    /**
     * 染色字典（HE / IF / IHC / OTHER / NONE）。
     */
    public static final String DICT_STAIN = "lqg_stain_type";

    /**
     * marker 表达字典（negative / weak / strong）。
     */
    public static final String DICT_MARKER_EXPR = "lqg_marker_expr";

    private final DictService dictService;

    /**
     * 字典的 value 列表（键值，不是给人看的 label）。
     */
    public List<String> stainValues() {
        return values(DICT_STAIN);
    }

    /**
     * marker 表达的 value 列表。
     */
    public List<String> markerExprValues() {
        return values(DICT_MARKER_EXPR);
    }

    /**
     * 一本字典的 value → label 映射（EMBED-WEB-001 的导出用：染色要导中文标签
     * {@code HE染色、IHC染色}，不能导 value {@code HE,IHC}）。
     *
     * <p>★ 只多这一个只读口，写侧继续走 {@link #stainValues()} / {@link #markerExprValues()}；
     * 读不到字典 / 空字典 → 空表，调用方（{@code EmbedExportService}）对「字典里没有的历史值」
     * 原样带出 value —— 一次字典抖动不该把整张导出打挂。
     */
    public Map<String, String> labels(String dictType) {
        List<DictDataDTO> data = dictService.getDictData(dictType);
        if (data == null) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (DictDataDTO row : data) {
            if (row.getDictValue() != null) {
                out.put(row.getDictValue(), row.getDictLabel());
            }
        }
        return out;
    }

    private List<String> values(String dictType) {
        List<DictDataDTO> data = dictService.getDictData(dictType);
        if (data == null) {
            return List.of();
        }
        return data.stream().map(DictDataDTO::getDictValue).filter(Objects::nonNull).toList();
    }

}
