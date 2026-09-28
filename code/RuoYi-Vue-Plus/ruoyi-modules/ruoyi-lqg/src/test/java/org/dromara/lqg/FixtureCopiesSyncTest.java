package org.dromara.lqg;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模块内夹具副本与仓库原件<b>逐字节一致</b>（独立验收 V31 / V22）。
 *
 * <p>后端构建只靠后端目录自己之后，有两类文件在仓库里各有两份：
 * <ul>
 *   <li>{@code ocr-cases.json}：原件 {@code doc/verify/fixtures/}（验收脚本拿它当期望值），
 *       副本 {@code ruoyi-lqg/src/main/resources/}（dev / test 的识别测试桩返回它、解析器单测读它）；</li>
 *   <li>四份甲方导出模板：原件 {@code _input/templates/}（甲方给的、只读），
 *       副本 {@code ruoyi-lqg/src/test/resources/export-templates/}（导出表头对账测试读它）。</li>
 * </ul>
 * 口径：<b>原件是权威，副本跟着原件走</b>。改了原件（甲方换模板、验收用例增删）就把副本一起换掉，
 * 否则本测试在整仓检出时红。只检出后端目录（拆仓后的后端仓库、CI 的后端构建）时找不到原件，
 * 本测试跳过（不算失败）—— 那时由验收脚本拿原件对接口实跑兜底。
 */
class FixtureCopiesSyncTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
        "ocr-cases.json|doc/verify/fixtures/ocr-cases.json",
        "export-templates/样本记录信息表模板.xlsx|_input/templates/样本记录信息表模板.xlsx",
        "export-templates/类器官收样记录模板.xlsx|_input/templates/类器官收样记录模板.xlsx",
        "export-templates/石蜡包埋送样记录模板.xlsx|_input/templates/石蜡包埋送样记录模板.xlsx",
        "export-templates/-80冻存模板.xlsx|_input/templates/-80冻存模板.xlsx"
    })
    @DisplayName("模块内副本 = 仓库原件（整仓检出时比对；只有后端目录时跳过）")
    void copyEqualsOriginal(String pair) throws IOException {
        String[] parts = pair.split("\\|");
        byte[] copy;
        try (InputStream in = FixtureCopiesSyncTest.class.getClassLoader().getResourceAsStream(parts[0])) {
            assertNotNull(in, "模块内副本不在 classpath 上：" + parts[0]);
            copy = in.readAllBytes();
        }
        Path original = findUpwards(parts[1]);
        Assumptions.assumeTrue(original != null,
            "没找到仓库原件 " + parts[1] + "（只检出了后端目录）—— 跳过比对");
        assertTrue(Arrays.equals(copy, Files.readAllBytes(original)),
            "模块内副本 " + parts[0] + " 与仓库原件 " + original + " 不一致：原件是权威，把副本换成原件的内容");
    }

    /**
     * 从当前工作目录往上找（模块目录跑、仓库根跑都能找到）。
     */
    private static Path findUpwards(String relative) {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

}
