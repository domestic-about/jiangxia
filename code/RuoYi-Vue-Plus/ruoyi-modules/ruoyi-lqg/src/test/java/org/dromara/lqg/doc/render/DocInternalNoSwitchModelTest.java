package org.dromara.lqg.doc.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.lqg.doc.render.mapper.DocDictMapper;
import org.dromara.lqg.ext.service.ExtInternalNoSwitch;
import org.dromara.lqg.qc.domain.DocAttachment;
import org.dromara.lqg.qc.domain.DocImage;
import org.dromara.lqg.qc.domain.QcOrganoidDoc;
import org.dromara.lqg.qc.domain.QcSampleDoc;
import org.dromara.lqg.qc.domain.QcScoreDoc;
import org.dromara.lqg.qc.mapper.DocAttachmentMapper;
import org.dromara.lqg.qc.mapper.DocImageMapper;
import org.dromara.lqg.qc.mapper.QcOrganoidDocMapper;
import org.dromara.lqg.qc.mapper.QcSampleDocMapper;
import org.dromara.lqg.qc.mapper.QcScoreDocMapper;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.service.SampleFieldCipher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 内部编号开关作用到外部版文档（甲方 2026-09-24 意见第 23 行）—— <b>真的</b> {@link DocRenderModelFactory}，
 * 库与系统参数是替身。
 *
 * <ol>
 *   <li>内部版一直印内部编号（不看开关）；</li>
 *   <li>外部版：开关关（默认）→ 那一格是空串；开 → 印内部编号；</li>
 *   <li>「这一格印没印」进指纹：开关一切，外部版指纹必变（样本没有内部编号时两种设置印出来都是空格子，指纹照样分叉）；</li>
 *   <li>合并件：三个成员用<b>同一次</b>读到的开关；任一成员印了，合并件就记「印了」；</li>
 *   <li>类器官质控表 / 评分表没有这一格：开关切换不改它们的指纹（不必重出）。</li>
 * </ol>
 *
 * @author G 批 C 组（内部编号开关作用到外部版文档）
 */
class DocInternalNoSwitchModelTest {

    private static final long S = 9000009001L;

    private boolean switchOn;
    private int switchReads;
    private String internalNo = "T-demo01";
    private DocRenderModelFactory factory;

    /** Lambda 条件要先有表信息缓存（DocPagesServiceTest 同一个坑）。 */
    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : List.of(QcSampleDoc.class, QcOrganoidDoc.class, QcScoreDoc.class,
            DocImage.class, DocAttachment.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @BeforeEach
    void setUp() {
        switchOn = false;
        switchReads = 0;
        ExtInternalNoSwitch internalNoSwitch = new ExtInternalNoSwitch(null) {
            @Override
            public boolean enabled() {
                switchReads++;
                return switchOn;
            }
        };
        SampleFieldCipher cipher = new SampleFieldCipher(null) {
            @Override
            public String decrypt(String cipherText) {
                return cipherText;
            }
        };
        factory = new DocRenderModelFactory(sampleMapper(), sampleDocMapper(), organoidDocMapper(), scoreDocMapper(),
            listMapper(DocImageMapper.class), listMapper(DocAttachmentMapper.class), dictMapper(), cipher, internalNoSwitch);
    }

    @Test
    @DisplayName("内部版一直印内部编号，不看开关")
    void internalAlwaysPrints() {
        for (boolean on : new boolean[] {false, true}) {
            switchOn = on;
            DocRenderModel model = factory.single(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL);
            assertEquals("T-demo01", model.texts().get("internal_no"), "开关 " + on + " 时内部版");
            assertTrue(model.isInternalNoShown());
        }
    }

    @Test
    @DisplayName("外部版：开关关 → 那一格空串；开 → 印内部编号；指纹随开关变，关回来与原来一致")
    void externalFollowsSwitch() {
        switchOn = false;
        DocRenderModel off = factory.single(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        assertEquals("", off.texts().get("internal_no"), "关着：外部版那一格是空的");
        assertFalse(off.isInternalNoShown());
        assertTrue(off.canonical().contains("internal_no_shown=N"));

        switchOn = true;
        DocRenderModel on = factory.single(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        assertEquals("T-demo01", on.texts().get("internal_no"), "开着：外部版印内部编号");
        assertTrue(on.isInternalNoShown());
        assertTrue(on.canonical().contains("internal_no_shown=Y"));
        assertNotEquals(off.contentHash(), on.contentHash(), "开关一切，外部版指纹必须变（否则会命中按旧设置出的那一份）");

        switchOn = false;
        assertEquals(off.contentHash(), factory.single(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL).contentHash(),
            "关回来：与第一次关着时同一个指纹（内容没变不白重出）");
    }

    @Test
    @DisplayName("样本没有内部编号时两种设置印出来都是空格子，指纹照样分叉（开关状态单独进指纹）")
    void switchIsInFingerprintEvenWithoutInternalNo() {
        internalNo = null;
        switchOn = false;
        DocRenderModel off = factory.single(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        switchOn = true;
        DocRenderModel on = factory.single(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        assertEquals(off.texts(), on.texts(), "印出来的字一样（都是空格子）");
        assertNotEquals(off.contentHash(), on.contentHash());
    }

    @Test
    @DisplayName("内外部版指纹永远不同：开关开着时外部版也印了编号，audience 仍让两份产物分开")
    void internalAndExternalNeverShareAFingerprint() {
        switchOn = true;
        DocRenderModel internal = factory.single(S, DocKinds.SAMPLE_QC, DocAudiences.INTERNAL);
        DocRenderModel external = factory.single(S, DocKinds.SAMPLE_QC, DocAudiences.EXTERNAL);
        assertEquals(internal.texts(), external.texts(), "开着时两版印的字一样");
        assertNotEquals(internal.contentHash(), external.contentHash(), "但仍是两份独立产物");
    }

    @Test
    @DisplayName("合并件：三个成员用同一次读到的开关；样本质控表印了 → 合并件记「印了」，指纹随开关变")
    void mergedReadsSwitchOnceAndFollowsIt() {
        switchOn = true;
        switchReads = 0;
        List<DocRenderModel> members = factory.mergedMembers(S, DocAudiences.EXTERNAL);
        assertEquals(1, switchReads, "三个成员只读一次开关（合并件里不会一份印了、一份没印）");
        assertEquals(3, members.size());
        DocRenderModel mergedOn = DocRenderModel.merged(DocAudiences.EXTERNAL, "9", members);
        assertTrue(mergedOn.isInternalNoShown());
        assertEquals("T-demo01", members.get(0).texts().get("internal_no"));

        switchOn = false;
        DocRenderModel mergedOff = DocRenderModel.merged(DocAudiences.EXTERNAL, "9",
            factory.mergedMembers(S, DocAudiences.EXTERNAL));
        assertFalse(mergedOff.isInternalNoShown());
        assertNotEquals(mergedOn.contentHash(), mergedOff.contentHash());
    }

    @Test
    @DisplayName("类器官质控表 / 评分表没有「内部编号」一格：开关切换不改它们的指纹，也不记「印了」")
    void docsWithoutTheCellIgnoreTheSwitch() {
        for (String kind : List.of(DocKinds.ORGANOID_QC, DocKinds.ORGANOID_SCORE)) {
            switchOn = false;
            DocRenderModel off = factory.single(S, kind, DocAudiences.EXTERNAL);
            switchOn = true;
            DocRenderModel on = factory.single(S, kind, DocAudiences.EXTERNAL);
            assertFalse(on.isInternalNoShown(), kind);
            assertEquals(off.contentHash(), on.contentHash(), kind + " 没有这一格，开关切换不该让它重出");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 替身
    // ══════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> answer) {
        return (T) Proxy.newProxyInstance(DocInternalNoSwitchModelTest.class.getClassLoader(), new Class<?>[] {type},
            (p, method, args) -> switch (method.getName()) {
                case "toString" -> "stub-" + type.getSimpleName();
                case "hashCode" -> System.identityHashCode(p);
                case "equals" -> p == args[0];
                default -> answer.apply(method.getName(), args);
            });
    }

    private SampleMapper sampleMapper() {
        return proxy(SampleMapper.class, (name, args) -> {
            Sample sample = new Sample();
            sample.setId(S);
            sample.setInternalNo(internalNo);
            sample.setSubmitNo("SJ99990001");
            sample.setSourceUnitName("示例医院");
            sample.setDonorName("张某某");
            sample.setGender("male");
            sample.setReceiveDate(LocalDate.of(2026, 9, 20));
            sample.setOperatorName("李某");
            return sample;
        });
    }

    private QcSampleDocMapper sampleDocMapper() {
        return proxy(QcSampleDocMapper.class, (name, args) -> {
            QcSampleDoc doc = new QcSampleDoc();
            doc.setId(1L);
            doc.setSampleId(S);
            doc.setDocStatus("published");
            doc.setPatientNo("P-0001");
            return doc;
        });
    }

    private QcOrganoidDocMapper organoidDocMapper() {
        return proxy(QcOrganoidDocMapper.class, (name, args) -> {
            QcOrganoidDoc doc = new QcOrganoidDoc();
            doc.setId(2L);
            doc.setSampleId(S);
            doc.setDocStatus("published");
            doc.setGrowthState("良好");
            return doc;
        });
    }

    private QcScoreDocMapper scoreDocMapper() {
        return proxy(QcScoreDocMapper.class, (name, args) -> {
            QcScoreDoc doc = new QcScoreDoc();
            doc.setId(3L);
            doc.setSampleId(S);
            doc.setDocStatus("published");
            doc.setTotalScore(85);
            return doc;
        });
    }

    private static <T> T listMapper(Class<T> type) {
        return proxy(type, (name, args) -> {
            if ("selectList".equals(name)) {
                return new ArrayList<>();
            }
            throw new UnsupportedOperationException(name);
        });
    }

    private static DocDictMapper dictMapper() {
        return proxy(DocDictMapper.class, (name, args) -> "男");
    }
}
