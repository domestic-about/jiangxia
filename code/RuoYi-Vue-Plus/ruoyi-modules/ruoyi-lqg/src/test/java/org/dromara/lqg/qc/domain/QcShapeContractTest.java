package org.dromara.lqg.qc.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dromara.lqg.qc.domain.vo.QcDocBundleVo;
import org.dromara.lqg.qc.domain.vo.QcOrganoidDocVo;
import org.dromara.lqg.qc.domain.vo.QcSampleDocVo;
import org.dromara.lqg.qc.domain.vo.QcSampleRefVo;
import org.dromara.lqg.qc.domain.vo.QcScoreDocVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 三份文档的**形状**契约（QC-MODEL-001）：库列与 VO 键与 accept 逐条对上。
 *
 * <p>★ 钉三件事：
 * <ol>
 *   <li><b>质控表上不许有来源单位 / 患者姓名 / 性别 / 收样时间 / 处理时间 / 操作人 / 内部编号</b>
 *       （ticket §0 口径复述 1）—— 那七项只在 {@link QcSampleRefVo} 上，从样本主档带出；</li>
 *   <li><b>accept 3 第 1 段点名的键必须在</b>：{@code sample.internalNo}、{@code sampleQc.docStatus /
 *       receiveDesc / observeDesc / pretreatDesc}、{@code score.totalScore}；</li>
 *   <li><b>聚合体恒有四个键</b>：sample / sampleQc / organoidQc / score
 *       （三份文档首次访问就建齐，前端不用处理「还没建」）。</li>
 * </ol>
 *
 * @author QC-MODEL-001
 */
class QcShapeContractTest {

    private static List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
            .map(Field::getName).sorted().collect(Collectors.toList());
    }

    @Test
    @DisplayName("① 三张文档表 / 三个文档 VO 上都没有样本主档的七项（存两份 = 两个真相源）")
    void docTablesCarryNoSampleMasterColumns() {
        List<String> forbidden = List.of("sourceUnitName", "sourceUnitId", "donorName", "gender",
            "receiveDate", "processTime", "operatorName", "internalNo", "hospitalNo", "age", "submitNo");
        for (Class<?> type : List.of(QcSampleDoc.class, QcOrganoidDoc.class, QcScoreDoc.class,
            QcSampleDocVo.class, QcOrganoidDocVo.class, QcScoreDocVo.class)) {
            for (String name : forbidden) {
                assertTrue(!fieldNames(type).contains(name),
                    type.getSimpleName() + " 不许有 " + name + "（它从样本主档带出，只读）");
            }
        }
        // 那七项在「样本只读字段」这个 VO 上：
        List<String> ref = fieldNames(QcSampleRefVo.class);
        for (String name : List.of("internalNo", "sourceUnitName", "donorName", "gender",
            "receiveDate", "processTime", "operatorName")) {
            assertTrue(ref.contains(name), "QcSampleRefVo 缺少只读字段 " + name);
        }
    }

    @Test
    @DisplayName("② accept 3 点名的键都在（internalNo / docStatus / 三段默认文案 / totalScore）")
    void acceptKeysExist() {
        assertTrue(fieldNames(QcSampleRefVo.class).contains("internalNo"));
        List<String> sampleQc = fieldNames(QcSampleDocVo.class);
        for (String name : List.of("docStatus", "receiveDesc", "observeDesc", "pretreatDesc",
            "patientNo", "viabilityOssId", "viabilityFileName", "images", "attachments")) {
            assertTrue(sampleQc.contains(name), "QcSampleDocVo 缺少 " + name);
        }
        assertTrue(fieldNames(QcScoreDocVo.class).contains("totalScore"));
        assertTrue(fieldNames(QcOrganoidDocVo.class).containsAll(
            List.of("formedTime", "growthState", "growthDesc", "plannedDrugScreen", "feedbackTime")));
    }

    @Test
    @DisplayName("③ 聚合体恒有四个键；评分表没有图片位（slot 归属在 QcDocRules 里断）")
    void bundleShape() {
        assertEquals(List.of("organoidQc", "sample", "sampleQc", "score"), fieldNames(QcDocBundleVo.class));
    }

    @Test
    @DisplayName("④ patientNo 在实体上是 String（密文），没有 *_Plain 这类分叉字段")
    void patientNoIsEncryptedColumn() {
        // 加密列本身是个普通 String 列（加解密在 service 手工做），
        // 这条断的是「实体上有这一列、类型没错」——真正的密文由 accept 3 的最后一段断。
        assertTrue(fieldNames(QcSampleDoc.class).contains("patientNo"));
        assertEquals(String.class, Arrays.stream(QcSampleDoc.class.getDeclaredFields())
            .filter(f -> f.getName().equals("patientNo")).findFirst().orElseThrow().getType());
        // 没有 *_Plain / *_Cipher 这类分叉字段（那会是两套写法混用的信号）
        assertTrue(fieldNames(QcSampleDoc.class).stream().noneMatch(n -> n.endsWith("Plain")));
    }

}
