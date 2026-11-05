package org.dromara.lqg.ocr;

import org.dromara.lqg.ocr.domain.OcrFieldParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 微信官方通用印刷体识别的**真实返回形态**（2026-11-05 用生产 appid 对 ocr-cases 01 号印刷标签实测）：
 * 全角冒号被归一成半角、空格被吃掉、同一行两个字段粘在一起（「性别:男年龄:56岁」）。解析规则要吃得下这种形态。
 */
class OcrFieldParserWechatRealTest {

    static final List<String> WECHAT_LINES = List.of(
        "A医院", "姓名:测试供体甲", "性别:男年龄:56岁", "住院号:ZY0000001", "标本:肝组织");

    @Test
    @DisplayName("微信真实返回（半角冒号、无空格、性别年龄同一行）→ 六个字段都解析出来")
    void parsesWechatShape() {
        Map<String, String> f = OcrFieldParser.parse(WECHAT_LINES, Set.of("A医院"));
        assertEquals("测试供体甲", (f.get("donorName")));
        assertEquals("male", (f.get("gender")));
        assertEquals("56", (f.get("age")));
        assertEquals("ZY0000001", (f.get("hospitalNo")));
        assertEquals("肝组织", (f.get("tissueType")));
        assertEquals("A医院", (f.get("sourceUnitName")));
    }
}
