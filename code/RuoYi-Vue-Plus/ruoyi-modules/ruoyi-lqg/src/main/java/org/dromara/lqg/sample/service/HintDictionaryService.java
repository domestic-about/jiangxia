package org.dromara.lqg.sample.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.dto.DictDataDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 联想词字典（{@code GET /mp/dict/hints?type=…}，REQ-SAMPLE-008 / ticket §2.2 口径 4）。
 *
 * <p>★ <b>联想词走字典、不走历史值</b>：绝不用
 * {@code SELECT DISTINCT tissue_type FROM t_lqg_sample} 这种写法 —— 外部人员会从历史值联想里
 * 看到<b>别的单位</b>填过的内容（accept 第 3 条倒数第 2 段断的就是「字典里有『肝组织』、
 * 响应里绝不能出现『测试供体甲』这种只存在于样本表里的值」）。
 *
 * <p>四个 type 与四本字典一一对应（{@code authority/field-ssot.yaml} 的 {@code lqg_hint_*} 与 {@code lqg_species}）：
 * {@code tissue → lqg_hint_tissue_type}、{@code organoid → lqg_hint_organoid_type}、
 * {@code sample → lqg_hint_sample_type}、{@code species → lqg_species}（CR-20261009-18：种属的常用值，
 * 小程序填写页的种属选择面板从这里取，列表里没有的可以手填）。
 *
 * @author SAMPLE-MODEL-001
 */
@Service
@RequiredArgsConstructor
public class HintDictionaryService {

    /**
     * 对外类型名 → 字典 type。顺序即字典 seed 的顺序（dict_sort），响应保持它。
     */
    private static final Map<String, String> DICT_OF_TYPE = new LinkedHashMap<>();

    static {
        DICT_OF_TYPE.put("tissue", "lqg_hint_tissue_type");
        DICT_OF_TYPE.put("organoid", "lqg_hint_organoid_type");
        DICT_OF_TYPE.put("sample", "lqg_hint_sample_type");
        DICT_OF_TYPE.put("species", "lqg_species");
    }

    private final DictService dictService;

    /**
     * 某个联想词类型下的全部候选词（按字典 {@code dict_sort} 升序）。
     *
     * @param type tissue / organoid / sample / species
     * @return 候选词数组（字典里没有该类型 → 空数组，不是 500）
     */
    public List<String> hints(String type) {
        String dictType = DICT_OF_TYPE.get(type == null ? "" : type.trim());
        if (dictType == null) {
            throw new ServiceException("联想词类型只能是 tissue / organoid / sample / species");
        }
        List<DictDataDTO> data = dictService.getDictData(dictType);
        if (data == null) {
            return List.of();
        }
        // 取 label 而不是 value：SSOT 里两者同值（肝组织=肝组织），但 dict 的语义是 label 给人看
        return data.stream().map(DictDataDTO::getDictLabel).filter(java.util.Objects::nonNull).toList();
    }

}
