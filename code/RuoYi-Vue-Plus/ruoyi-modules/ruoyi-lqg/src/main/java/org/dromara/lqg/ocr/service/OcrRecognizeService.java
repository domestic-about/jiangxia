package org.dromara.lqg.ocr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.auth.group.domain.SourceUnit;
import org.dromara.lqg.auth.group.mapper.SourceUnitMapper;
import org.dromara.lqg.ocr.config.OcrProviderFactory;
import org.dromara.lqg.ocr.domain.OcrFieldParser;
import org.dromara.lqg.ocr.domain.vo.OcrRecognizeVo;
import org.dromara.lqg.ocr.domain.vo.OcrStatusVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 拍照识别预填的业务编排（FLOW:F-OCR-01.step2 → step3）。
 *
 * <p>流程：收图（内存里）→ provider 出原始文本行 → 规则解析出候选字段 → 返回给前端预填。
 * <b>全程不落库、不存图</b>（ADR-0007）：本类不写任何业务表，也不碰 OSS ——
 * accept 1 会在识别前后对 {@code t_lqg_sample} + {@code sys_oss} 计数（多留一份图 = 多留一份供体信息）。
 *
 * <p>★ <b>单位名只认 active 且完全相等</b>：从 {@code t_lqg_source_unit} 取
 * {@code unit_status='active'} 的 {@code unit_name} 当白名单，交给
 * {@link OcrFieldParser#parse} 做整行全等比较 —— 解析器自己不查库（纯函数、可逐例单测）。
 *
 * <p>★ 读单位表包 {@link DataPermissionHelper#ignore}：外部身份调这个接口时 token 里没有
 * 部门上下文，将来给这张表加 {@code @DataPermission} 时也不该被数据范围静默滤空
 * （AUTH-LOGIN-001 报告坑 1 的形态；同 {@code UnitQueryService} 的口径）。
 *
 * @author OCR-IMPL-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrRecognizeService {

    /**
     * 只要这两个单位状态当白名单：{@code pending}（外部自填待核验）/ {@code disabled}（停用）都不认。
     */
    private static final String ACTIVE_UNIT = "active";

    private final OcrProviderFactory providerFactory;
    private final SourceUnitMapper sourceUnitMapper;

    /**
     * 识别并解析。
     *
     * @param image    图片字节（调用方已校验非空 / 大小 / 格式）
     * @param stubCase 请求头 {@code X-Ocr-Stub-Case}（非测试桩忽略）
     * @return {@code {rawLines, fields}}（字段解析不出则不出现在 {@code fields} 里）
     * @throws ServiceException provider 不可用（含「未配 provider，请手动填写」）
     */
    public OcrRecognizeVo recognize(byte[] image, String stubCase) {
        List<String> rawLines = providerFactory.recognize(image, stubCase);
        OcrRecognizeVo vo = new OcrRecognizeVo();
        vo.setRawLines(rawLines == null ? List.of() : rawLines);
        vo.setFields(OcrFieldParser.parse(rawLines, activeUnitNames()));
        return vo;
    }

    /**
     * 识别通道状态（工作台）。
     */
    public OcrStatusVo status() {
        return providerFactory.status();
    }

    /**
     * 当前 active 的来源单位名（白名单）。
     */
    private Set<String> activeUnitNames() {
        return DataPermissionHelper.ignore(() -> {
            List<SourceUnit> units = sourceUnitMapper.selectList(new LambdaQueryWrapper<SourceUnit>()
                .eq(SourceUnit::getUnitStatus, ACTIVE_UNIT)
                .select(SourceUnit::getUnitName));
            Set<String> names = new LinkedHashSet<>();
            for (SourceUnit unit : units) {
                if (unit.getUnitName() != null && !unit.getUnitName().isBlank()) {
                    names.add(unit.getUnitName());
                }
            }
            return names;
        });
    }

}
