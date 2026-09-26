<template>
  <div class="p-2 lqg-qc-editor">
    <!-- ═══ 地址里没带样本（直接输了编辑页地址）：给引导，不显示空白、也不显示上一次的样本 ═══ -->
    <el-card v-if="!sampleId" shadow="hover" class="lqg-qc-editor__guide">
      <el-empty :image-size="80" :description="t('lqg.qc.session.guideTitle')">
        <p class="lqg-qc-editor__guide-desc">{{ t('lqg.qc.session.guideDesc') }}</p>
        <el-button v-for="page in guidePages" :key="page.path" type="primary" plain @click="router.push(page.path)">
          {{ t('lqg.qc.session.guideGo', { name: t(page.titleKey) }) }}
        </el-button>
      </el-empty>
    </el-card>

    <!-- ═══ 页头：样本摘要条（从样本主档带出，只读；要改去样本总表改）═══ -->
    <el-card v-if="sampleId" shadow="hover" class="lqg-qc-editor__head">
      <div class="lqg-qc-editor__headline">
        <span class="lqg-qc-editor__title">{{ t('lqg.qc.editor.title') }}</span>
        <span class="lqg-qc-editor__mono">{{ sample.internalNo || '—' }}</span>
        <span class="lqg-qc-editor__sub">{{ t('lqg.qc.editor.readonlyHint', { name: samplePageName }) }}</span>
        <el-button link type="primary" class="lqg-qc-editor__back" @click="goBack">
          {{ t('lqg.qc.editor.back', { name: samplePageName }) }}
        </el-button>
      </div>
      <div class="lqg-qc-editor__summary">
        <span v-for="item in summaryItems" :key="item.label" class="lqg-qc-editor__summary-item">
          <span class="lqg-qc-editor__summary-label">{{ item.label }}</span>
          <span class="lqg-qc-editor__summary-value">{{ item.value }}</span>
        </span>
      </div>
    </el-card>

    <el-alert v-if="sampleId && loadError" type="error" :closable="false" show-icon :title="loadError" class="mb-2" />

    <el-card v-if="sampleId" v-loading="loading" shadow="hover" class="lqg-qc-editor__body">
      <!-- ═══ 三个页签（各带 草稿 / 已完成 徽标）═══ -->
      <!-- ★ 换页签不在这里另调一次 loadPages：预览面板自己 watch docKind 重读（原来两处都读 →
           一次切换发两个请求，Kevin 本机验收「网页工作台」第 6 行截图里连弹两条就是这么来的） -->
      <el-tabs v-model="activeTab" class="lqg-qc-editor__tabs">
        <el-tab-pane v-for="tab in TABS" :key="tab.name" :name="tab.name">
          <template #label>
            <span class="lqg-qc-editor__tab-label">
              {{ t(tab.labelKey) }}
              <el-tag size="small" :type="docStatusOf(tab) === 'published' ? 'success' : 'warning'" effect="light">
                {{ docStatusOf(tab) === 'published' ? t('lqg.qc.editor.statusPublished') : t('lqg.qc.editor.statusDraft') }}
              </el-tag>
            </span>
          </template>
        </el-tab-pane>
      </el-tabs>

      <!-- ═══ 左编辑 / 右预览 ═══ -->
      <div class="lqg-qc-editor__split">
        <div class="lqg-qc-editor__left">
          <!-- ★ 三份文档各挂一次、用 v-show 隐藏（**不是** v-if 卸载）：换页签再回来时，
               没保存的改动还在（QC-WEB-001 只有样本质控表时用 v-if 没问题，三个页签都能
               编辑之后 v-if 会在换页签时静默丢掉另一页签的改动）。
               dirty 按页签各记一份（dirtyTabs），保存只存当前页签。 -->
          <template v-if="bundle">
            <SampleQcTab
              v-show="activeTab === 'sample-qc'"
              ref="sampleTabRef"
              :sample-id="sampleId"
              :doc="bundle.sampleQc"
              :readonly="!canEdit"
              @dirty="(value) => setTabDirty('sample-qc', value)"
              @changed="reload"
            />
            <OrganoidQcTab
              v-show="activeTab === 'organoid-qc'"
              ref="organoidTabRef"
              :sample-id="sampleId"
              :doc="bundle.organoidQc"
              :readonly="!canEdit"
              @dirty="(value) => setTabDirty('organoid-qc', value)"
              @changed="reload"
            />
            <ScoreTab
              v-show="activeTab === 'score'"
              ref="scoreTabRef"
              :sample-id="sampleId"
              :doc="bundle.score"
              :readonly="!canEdit"
              @dirty="(value) => setTabDirty('score', value)"
              @changed="reload"
            />
          </template>
          <el-empty v-else :description="loading ? '' : loadError || t('lqg.qc.editor.loadFailed')" />
        </div>

        <div class="lqg-qc-editor__right">
          <div class="lqg-qc-editor__pane-title">{{ t('lqg.qc.editor.previewTitle') }}</div>

          <!-- ★ 预览面板（UI:admin.doc.preview / FLOW:F-QC-01.step5，DOC-PUBLISH-001）：
               内外部版切换、逐页页面图、四个下载入口、渲染中 / 失败 + 重新生成。
               ★ **单个实例**（不是 v-for 里每页签一个）：`v-for` 里的 ref 会变成数组，
                 `previewPaneRef.value?.render()` 就调不到了（点「预览」没反应的那种坏法）。 -->
          <!-- ★ 按「样本 + 进来时要看的版本」重建：换了样本（或从首页异常清单点进来要看另一版）整块重来，
               版本切换、轮询、页面图都不串到下一个样本 -->
          <PreviewPane
            :key="`${sampleId}|${initialAudience}`"
            ref="previewPaneRef"
            :sample-id="sampleId"
            :doc-kind="currentTab.docKind"
            :doc-status="docStatusOf(currentTab)"
            :initial-audience="initialAudience"
            @busy="(value: boolean) => (previewing = value)"
          />
        </div>
      </div>

      <!-- ═══ 页脚按钮 ═══ -->
      <div class="lqg-qc-editor__footer">
        <el-button type="primary" :loading="saving" :disabled="!bundle || !canEdit" @click="handleSaveDraft">
          {{ t('lqg.qc.editor.saveDraft') }}
        </el-button>
        <!-- ★ 两个按钮在 DOC-PUBLISH-001 点亮（QC-WEB-001 里是写死置灰的占位） -->
        <el-button :loading="previewing" :disabled="!bundle" @click="handlePreview">
          {{ t('lqg.qc.editor.preview') }}
        </el-button>
        <el-button
          :type="currentPublished ? 'default' : 'success'"
          :loading="publishing"
          :disabled="!bundle || !canEdit || currentTabDirty"
          @click="handlePublish"
        >
          {{ currentPublished ? t('lqg.qc.preview.unpublish') : t('lqg.qc.editor.publish') }}
        </el-button>
        <span class="lqg-qc-editor__footer-hint">{{ footerHint }}</span>
      </div>
    </el-card>
  </div>
</template>

<script setup name="LqgQcEditor" lang="ts">
import { getQcBundle, type QcDocBundleVO, type QcDocKind } from '@/api/lqg/qc';
import { publishQcDoc, unpublishQcDoc } from '@/api/lqg/doc';
import SampleQcTab from './SampleQcTab.vue';
import OrganoidQcTab from './OrganoidQcTab.vue';
import ScoreTab from './ScoreTab.vue';
import PreviewPane from '../components/PreviewPane.vue';
import { QC_FRESH_URLS, urlsMayBeStale } from '../components/freshUrls';
import { decideSampleSwitch, sampleIdOf } from './session';
import { SAMPLE_KINDS, samplePageOf } from '@/views/lqg/sample/pages';
import type { DocAudience } from '@/api/lqg/doc';
import { useTagsViewStore } from '@/store/modules/tagsView';
import type { RouteLocationNormalizedLoaded } from 'vue-router';
import { useI18n } from 'vue-i18n';

// ============================================================================
// 质控文档编辑页（QC-WEB-001 / UI:admin.qc.editor，方案 A：独立整页，左编辑右预览）
//
// 路由：隐藏菜单 5510（`/qc-console/qc-editor`，父目录 5500 是隐藏目录）。
// 进入：样本总表行操作「质控文档」→ `?sampleId=<id>`（只对已核验有效的样本可点）。
//
// ★ 工作台里**唯一不用抽屉**的录入页（内容多、带多图，抽屉装不下；方案 B 已否决）。
// ★ 页头摘要条里那七项（来源单位 / 患者姓名 / 性别 / 收样时间 / 处理时间 / 操作人 /
//   内部编号）从样本主档带出，**只读**——不进表单（accept 2 第 4 段断这个）。
// ★ 三份文档都能编辑了（QC-WEB-002）：脏标记**按页签各记一份**，页头/路由离开的拦截
//   用「任一页签脏」。保存按钮只保存当前页签（各页签的 save() 自己发各自的 PUT）。
// ★ 右栏是**真的预览面板**（PreviewPane.vue，DOC-PUBLISH-001）：点「预览」触发内部版渲染 →
//   轮询页面图 → 逐页显示；内外部版切换、四个下载入口、渲染中 / 失败 +「重新生成」。
// ★ 页脚「预览」「完成并同步」已点亮；已完成状态下按钮变「撤回」，
//   页脚提示「已同步给送检方 · 修改后需重新同步」（FLOW:F-QC-01.step6 / step7）。
// ★ 一个标签装一个样本（Kevin 本机验收「网页工作台」第 8 行）：本页被 keep-alive 缓存、按 path 复用，
//   所以 sampleId **不能只在 setup 里读一次** —— 重新激活（onActivated）或地址里换了样本
//   （onBeforeRouteUpdate）都按地址重新打开；判据与未保存提示的口径见 ./session.ts。
// ★ 图片 / 附件地址是 10 分钟签名链接（私有桶）：provide 一个 ensureFreshUrls，
//   图片位 / 附件组件用之前调它，老了就重取（见 ../components/freshUrls.ts）。
// ============================================================================

type TabName = 'sample-qc' | 'organoid-qc' | 'score';

const { t } = useI18n();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const route = useRoute();
const router = useRouter();

const TABS: { name: TabName; labelKey: string; docKind: QcDocKind }[] = [
  { name: 'sample-qc', labelKey: 'lqg.qc.editor.tabSampleQc', docKind: 'sample_qc' },
  { name: 'organoid-qc', labelKey: 'lqg.qc.editor.tabOrganoidQc', docKind: 'organoid_qc' },
  { name: 'score', labelKey: 'lqg.qc.editor.tabScore', docKind: 'organoid_score' }
];

/**
 * 从首页「渲染失败与缺图」清单点进来时带 `tab` 与 `audience`（独立验收 V29）：
 * 直接落到出问题的那一份、预览面板切到出问题的那一版（失败原因 / 缺了哪几张就在面板上）。
 */
const TAB_NAMES: TabName[] = ['sample-qc', 'organoid-qc', 'score'];
const tabOf = (query: RouteLocationNormalizedLoaded['query']): TabName =>
  TAB_NAMES.includes(query.tab as TabName) ? (query.tab as TabName) : 'sample-qc';
const audienceOf = (query: RouteLocationNormalizedLoaded['query']): DocAudience =>
  query.audience === 'external' ? 'external' : 'internal';

/** 本页自己的路由路径（keep-alive 下 route 是全局的：别的页的地址不归本页管） */
const selfPath = route.path;
/** 标签页原来的标题（「质控文档编辑」）；装了样本后换成「质控文档 · 内部编号」 */
const baseTagTitle = String(route.meta?.title || '');
const tagsViewStore = useTagsViewStore();

const sampleId = ref<string>(sampleIdOf(route.query));
const activeTab = ref<TabName>(tabOf(route.query));
const initialAudience = ref<DocAudience>(audienceOf(route.query));
/** 此刻装的这个样本对应的地址参数（换样本时用户选「留下」，地址要改回来） */
let openedQuery: RouteLocationNormalizedLoaded['query'] = { ...route.query };
/** 当前的未保存改动在离开编辑页时已确认过「丢掉」（再从列表点别的样本就不问第二遍） */
let leaveConfirmed = false;
/** bundle 请求的序号：换了样本 / 又发了一次，晚回来的旧响应不认 */
let loadSeq = 0;
/** 这批图片 / 附件地址是什么时候取回来的（签名链接 10 分钟过期） */
let loadedAt = 0;
const loading = ref(false);
const saving = ref(false);
const previewing = ref(false);
const publishing = ref(false);
const dirtyTabs = reactive<Record<TabName, boolean>>({ 'sample-qc': false, 'organoid-qc': false, score: false });
/** 任一页签有未保存改动 → 离开页面时拦一次 */
const dirty = computed(() => Object.values(dirtyTabs).some(Boolean));
const loadError = ref('');
const bundle = ref<QcDocBundleVO | null>(null);
/** 右栏预览面板（页面图 / 下载 / 重新生成都在它里面，见 components/PreviewPane.vue） */
const previewPaneRef = ref<InstanceType<typeof PreviewPane>>();
const sampleTabRef = ref<InstanceType<typeof SampleQcTab>>();
const organoidTabRef = ref<InstanceType<typeof OrganoidQcTab>>();
const scoreTabRef = ref<InstanceType<typeof ScoreTab>>();

const sample = computed(() => bundle.value?.sample ?? ({} as QcDocBundleVO['sample']));
/** 编辑权走 `lqg:qc:edit`（101/102 都有）；没有就整页只读（后端也会 403） */
const canEdit = computed(() => proxy?.$auth?.hasPermi?.('lqg:qc:edit') ?? true);

const summaryItems = computed(() => [
  { label: t('lqg.qc.editor.sourceUnit'), value: sample.value.sourceUnitName || '—' },
  { label: t('lqg.qc.editor.donorName'), value: sample.value.donorName || '—' },
  { label: t('lqg.qc.editor.gender'), value: sample.value.gender || '—' },
  { label: t('lqg.qc.editor.receiveDate'), value: sample.value.receiveDate || '—' },
  { label: t('lqg.qc.editor.processTime'), value: sample.value.processTime || '—' },
  { label: t('lqg.qc.editor.operatorName'), value: sample.value.operatorName || '—' },
  { label: t('lqg.qc.editor.internalNo'), value: sample.value.internalNo || '—' }
]);

const currentTab = computed(() => TABS.find((tab) => tab.name === activeTab.value) || TABS[0]);

/** 当前页签的编辑组件（保存按钮按它派发到各自的 PUT） */
const activeTabRef = computed(() => {
  if (activeTab.value === 'organoid-qc') return organoidTabRef.value;
  if (activeTab.value === 'score') return scoreTabRef.value;
  return sampleTabRef.value;
});

/** 页签上的状态徽标 = 该文档的 doc_status（draft 草稿 / published 已完成） */
const docStatusOf = (tab: (typeof TABS)[number]) => {
  if (!bundle.value) return 'draft';
  if (tab.name === 'sample-qc') return bundle.value.sampleQc?.docStatus || 'draft';
  if (tab.name === 'organoid-qc') return bundle.value.organoidQc?.docStatus || 'draft';
  return bundle.value.score?.docStatus || 'draft';
};

/**
 * 取三份文档 + 图片 + 附件（首次访问后端就地建三份空草稿；样本不是 valid → 400）。
 *
 * `quiet`：只为换一批新的签名地址而重取（ensureFreshUrls），不盖加载遮罩、失败也不改页面上的报错。
 * 没保存的表单不会被冲掉 —— 各页签的 syncFromDoc 在有改动时不覆盖。
 */
const reload = async (options?: { quiet?: boolean }) => {
  const id = sampleId.value;
  if (!id) {
    return;
  }
  const quiet = options?.quiet === true;
  const seq = ++loadSeq;
  if (!quiet) {
    loading.value = true;
  }
  try {
    const res = await getQcBundle(id);
    if (seq !== loadSeq) return;
    bundle.value = res.data;
    loadedAt = Date.now();
    loadError.value = '';
    updateTagTitle();
  } catch (e: any) {
    if (seq !== loadSeq || quiet) return;
    // 400（样本不存在 / 不是有效样本）也走这里：把后端 msg 原样显示，不吞
    loadError.value = e?.message || t('lqg.qc.editor.loadFailed');
  } finally {
    if (seq === loadSeq) {
      loading.value = false;
    }
  }
};

/**
 * 图片 / 附件用之前先保鲜：这批签名地址取回来超过 5 分钟就重取一次（同一时刻只有一次在途），
 * 等页面换上新地址（nextTick）再返回 —— 调用方随后读 props 拿到的就是新的。
 */
let refreshing: Promise<void> | null = null;
const ensureFreshUrls = async (): Promise<void> => {
  if (!bundle.value || !urlsMayBeStale(loadedAt, Date.now())) {
    return;
  }
  if (!refreshing) {
    refreshing = reload({ quiet: true })
      .then(() => nextTick())
      .finally(() => {
        refreshing = null;
      });
  }
  await refreshing;
};
provide(QC_FRESH_URLS, ensureFreshUrls);

/** 标签页标题带上此刻装的样本（一个标签装一个样本，换了样本标题跟着换） */
const updateTagTitle = () => {
  const view = tagsViewStore.visitedViews.find((v) => v.path === selfPath) as (RouteLocationNormalizedLoaded & { title?: string }) | undefined;
  if (!view) return;
  const no = bundle.value?.sample?.internalNo;
  view.title = no ? t('lqg.qc.session.tagTitle', { no }) : baseTagTitle;
};

/**
 * 按地址打开一个样本（首次进入 / 换了样本）：上一个样本的一切先清掉再取数
 * —— 左栏三个页签随 bundle 置空卸载（表单与脏标记不串过来），右栏预览面板按 :key 重建。
 */
const openFromRoute = async (target: RouteLocationNormalizedLoaded) => {
  loadSeq++; // 上一个样本在途的请求作废
  sampleId.value = sampleIdOf(target.query);
  activeTab.value = tabOf(target.query);
  initialAudience.value = audienceOf(target.query);
  openedQuery = { ...target.query };
  leaveConfirmed = false;
  bundle.value = null;
  loadError.value = '';
  loading.value = false;
  loadedAt = 0;
  TAB_NAMES.forEach((name) => (dirtyTabs[name] = false));
  updateTagTitle();
  await reload();
};

/**
 * 还是同一个样本，但入口带了别的页签 / 版本（首页「渲染失败与缺图」清单点同一个样本的另一份）：
 * 落到那一份。只认和上次打开时**不一样**的参数 —— 从标签回来时地址就是上次那个，不会把用户后来
 * 手动切的页签又拨回去。切页签不丢改动（三个页签是 v-show）。
 */
const applyEntryQuery = (query: RouteLocationNormalizedLoaded['query']) => {
  if (query.tab && query.tab !== openedQuery.tab) {
    activeTab.value = tabOf(query);
  }
  if (query.audience && query.audience !== openedQuery.audience) {
    initialAudience.value = audienceOf(query);
  }
  openedQuery = { ...query };
};

/** 当前样本有没保存的改动、地址里却要换样本：问一句（选「留下」→ false） */
const confirmSwitch = async (): Promise<boolean> => {
  const from = sample.value.internalNo || '—';
  try {
    await ElMessageBox.confirm(t('lqg.qc.session.switchMessage', { from }), t('lqg.qc.session.switchTitle'), {
      confirmButtonText: t('lqg.qc.session.switchGo'),
      cancelButtonText: t('lqg.qc.session.switchStay', { from }),
      type: 'warning'
    });
    return true;
  } catch {
    return false;
  }
};

/** 没选样本时的引导：两张样本表（路径与名字取 views/lqg/sample/pages.ts，不另写字面量） */
const guidePages = SAMPLE_KINDS.map((kind) => samplePageOf(kind));

/** 当前页签的这份文档此刻是不是「已完成」（页脚按钮 / 提示按它变） */
const currentPublished = computed(() => bundle.value !== null && docStatusOf(currentTab.value) === 'published');

/** 当前页签有没有没保存的改动（有 → 「完成并同步」先别点，免得同步了半份改动） */
const currentTabDirty = computed(() => dirtyTabs[activeTab.value]);

/** 页脚提示：已完成 → 「已同步给送检方 · 修改后需重新同步」；否则还是原来的说明 */
const footerHint = computed(() =>
  currentPublished.value ? t('lqg.qc.preview.syncedFooter') : t('lqg.qc.editor.footerHint')
);

/**
 * 「预览」= 让右栏面板触发一次**当前页签**的内部版渲染并逐页显示
 * （FLOW:F-QC-01.step5：点预览 → 触发渲染 → 显示页面图，与将要下载的 Word / PDF 同源）。
 *
 * ★ 有未保存的改动时先提醒保存：预览是拿**库里**的内容渲染的，
 *   不保存就预览会看到旧内容（页面上改了字、预览图没改 —— 用户会以为渲染坏了）。
 */
const handlePreview = async () => {
  if (currentTabDirty.value) {
    proxy?.$modal.msgWarning(t('lqg.qc.preview.saveFirst'));
    return;
  }
  // 渲染是异步的：render() 内部会轮询到 done / failed，loading 由面板的 @busy 驱动
  await previewPaneRef.value?.render();
};

/**
 * 「完成并同步」/「撤回」（POST …/publish · …/unpublish，DOC-PUBLISH-001）。
 *
 * 已完成 → 这个按钮是「撤回」；草稿 → 是「完成并同步」（后端异步触发内外部版 + 合并件渲染）。
 * 两条转移都是**状态机**的合法转移，非法方向后端一律 400（这里也先按当前状态分流）。
 */
const handlePublish = async () => {
  if (!sampleId.value) return;
  const docType = docTypeOf(currentTab.value);
  const published = currentPublished.value;
  try {
    await ElMessageBox.confirm(
      published ? t('lqg.qc.preview.unpublishConfirm') : t('lqg.qc.preview.publishConfirm'),
      published ? t('lqg.qc.preview.unpublish') : t('lqg.qc.editor.publish'),
      { confirmButtonText: published ? t('lqg.qc.preview.unpublish') : t('lqg.qc.editor.publish'), type: 'warning' }
    );
  } catch {
    return;
  }
  publishing.value = true;
  try {
    if (published) {
      await unpublishQcDoc(sampleId.value, docType);
      proxy?.$modal.msgSuccess(t('lqg.qc.preview.unpublishDone'));
    } else {
      await publishQcDoc(sampleId.value, docType);
      proxy?.$modal.msgSuccess(t('lqg.qc.preview.publishDone'));
    }
    await reload();
    // 完成并同步是异步渲染：面板自己轮询，不用等它
    previewPaneRef.value?.loadPages();
  } catch (e: any) {
    proxy?.$modal.msgError(
      (published ? t('lqg.qc.preview.unpublishFailed') : t('lqg.qc.preview.publishFailed')) + (e?.message || String(e))
    );
  } finally {
    publishing.value = false;
  }
};

/** 页签名（连字符）→ docType（连字符，与后端路径段一致） */
const docTypeOf = (tab: (typeof TABS)[number]): 'sample-qc' | 'organoid-qc' | 'score' => tab.name;

/** 页签各自的脏标记（子组件 `@dirty` 上报；保存成功后子组件自己置 false） */
const setTabDirty = (tab: TabName, value: boolean) => {
  dirtyTabs[tab] = value;
};

/** 保存草稿：只保存**当前页签**（各页签的 save() 自己发各自的 PUT，见 api/lqg/qc/index.ts） */
const handleSaveDraft = async () => {
  const tab = activeTabRef.value;
  if (!tab) return;
  saving.value = true;
  try {
    await tab.save();
    await reload();
  } finally {
    saving.value = false;
  }
};

// ★ 返回按样本类别回对应的那一页（CR-20260924-10：样本总表拆成样本记录信息表 / 类器官收样记录）
const samplePage = computed(() => samplePageOf(sample.value.sampleKind));
const samplePageName = computed(() => t(samplePage.value.titleKey));

const goBack = () => {
  router.push({ path: samplePage.value.path });
};

// ── 未保存改动提示 ───────────────────────────────────────────────────────────
/** 关标签页 / 刷新时提示（浏览器原生的 beforeunload） */
const beforeUnloadHandler = (e: BeforeUnloadEvent) => {
  if (!dirty.value) return;
  e.preventDefault();
  e.returnValue = '';
};

onMounted(async () => {
  window.addEventListener('beforeunload', beforeUnloadHandler);
  await openFromRoute(route);
});

/**
 * 从标签 / 列表 / 首页再进来（keep-alive 重新激活）：地址里换了样本就按新样本重新打开。
 * ★ 第一次挂载后 Vue 也会调一次 onActivated —— 那时样本相同，走 stay 什么都不做。
 */
onActivated(async () => {
  if (route.path !== selfPath) return;
  const decision = decideSampleSwitch(sampleId.value, sampleIdOf(route.query), dirty.value, leaveConfirmed);
  if (decision === 'stay') {
    // 回到同一个样本：没保存的改动还在，接着编辑；再离开时照常提示
    leaveConfirmed = false;
    applyEntryQuery(route.query);
    return;
  }
  if (decision === 'confirm' && !(await confirmSwitch())) {
    // 留在原样本：地址改回来（标签页的地址也跟着回来）
    await router.replace({ path: selfPath, query: openedQuery });
    return;
  }
  await openFromRoute(route);
});

/** 编辑页开着、地址里的 sampleId 变了（前进后退 / 页内链接）：离开提示不会触发，这里问 */
onBeforeRouteUpdate(async (to) => {
  const decision = decideSampleSwitch(sampleId.value, sampleIdOf(to.query), dirty.value, false);
  if (decision === 'stay') {
    applyEntryQuery(to.query);
    return true;
  }
  if (decision === 'confirm' && !(await confirmSwitch())) return false;
  openFromRoute(to);
  return true;
});

onUnmounted(() => {
  window.removeEventListener('beforeunload', beforeUnloadHandler);
});

/** 站内离开（返回样本总表 / 点侧边栏 / 换页签路由）时拦一次确认 */
onBeforeRouteLeave(async (to, from, next) => {
  if (!dirty.value) {
    next();
    return;
  }
  try {
    await ElMessageBox.confirm(t('lqg.qc.editor.unsavedMessage'), t('lqg.qc.editor.unsavedTitle'), {
      confirmButtonText: t('lqg.qc.editor.unsavedLeave'),
      cancelButtonText: t('lqg.qc.editor.unsavedStay'),
      type: 'warning'
    });
    // 已经说过「离开就会丢掉」：之后从列表点另一个样本直接换，不再问第二遍（./session.ts）
    leaveConfirmed = true;
    next();
  } catch {
    next(false);
  }
});
</script>

<style scoped lang="scss">
.lqg-qc-editor {
  .lqg-qc-editor__headline {
    display: flex;
    align-items: baseline;
    gap: 10px;
    flex-wrap: wrap;
  }
  .lqg-qc-editor__title {
    font-size: 16px;
    font-weight: 600;
    color: var(--lqg-ink);
  }
  .lqg-qc-editor__mono {
    font-family: var(--lqg-font-mono);
    color: var(--lqg-ink-2);
  }
  .lqg-qc-editor__sub {
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
  .lqg-qc-editor__back {
    margin-left: auto;
  }
  .lqg-qc-editor__summary {
    margin-top: 8px;
    display: flex;
    flex-wrap: wrap;
    gap: 6px 18px;
    padding: 8px 10px;
    border-radius: 6px;
    background: var(--lqg-bg);
    font-size: 12px;
  }
  .lqg-qc-editor__summary-label {
    color: var(--lqg-ink-3);
    margin-right: 4px;
  }
  .lqg-qc-editor__summary-value {
    color: var(--lqg-ink);
  }
  .lqg-qc-editor__body {
    margin-top: 12px;
  }
  .lqg-qc-editor__tab-label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
  .lqg-qc-editor__split {
    display: grid;
    grid-template-columns: 1.15fr 1fr;
    gap: 16px;
    align-items: start;
  }
  @media (max-width: 1200px) {
    .lqg-qc-editor__split {
      grid-template-columns: 1fr;
    }
  }
  .lqg-qc-editor__left {
    min-width: 0;
  }
  .lqg-qc-editor__right {
    min-width: 0;
    padding: 10px;
    border-radius: 6px;
    background: var(--lqg-bg);
  }
  .lqg-qc-editor__pane-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--lqg-ink);
    margin-bottom: 8px;
  }
  .lqg-qc-editor__reason {
    margin-top: 4px;
    word-break: break-all;
  }
  .lqg-qc-editor__placeholder {
    margin-top: 16px;
    text-align: center;
    color: var(--lqg-ink-3);
  }
  .lqg-qc-editor__placeholder-icon {
    font-size: 28px;
  }
  .lqg-qc-editor__placeholder-text {
    margin: 8px 0 2px;
    font-size: 13px;
  }
  .lqg-qc-editor__placeholder-sub {
    margin: 0;
    font-size: 12px;
  }
  .lqg-qc-editor__footer {
    margin-top: 16px;
    padding-top: 12px;
    border-top: 1px solid var(--lqg-line);
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .lqg-qc-editor__footer-hint {
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
  .lqg-qc-editor__guide-desc {
    margin: 0 0 12px;
    font-size: 13px;
    color: var(--lqg-ink-2);
  }
}
</style>
