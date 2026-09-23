<template>
  <div class="p-2 lqg-qc-editor">
    <!-- ═══ 页头：样本摘要条（从样本主档带出，只读；要改去样本总表改）═══ -->
    <el-card shadow="hover" class="lqg-qc-editor__head">
      <div class="lqg-qc-editor__headline">
        <span class="lqg-qc-editor__title">{{ t('lqg.qc.editor.title') }}</span>
        <span class="lqg-qc-editor__mono">{{ sample.internalNo || '—' }}</span>
        <span class="lqg-qc-editor__sub">{{ t('lqg.qc.editor.readonlyHint') }}</span>
        <el-button link type="primary" class="lqg-qc-editor__back" @click="goBack">
          {{ t('lqg.qc.editor.back') }}
        </el-button>
      </div>
      <div class="lqg-qc-editor__summary">
        <span v-for="item in summaryItems" :key="item.label" class="lqg-qc-editor__summary-item">
          <span class="lqg-qc-editor__summary-label">{{ item.label }}</span>
          <span class="lqg-qc-editor__summary-value">{{ item.value }}</span>
        </span>
      </div>
    </el-card>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" class="mb-2" />

    <el-card v-loading="loading" shadow="hover" class="lqg-qc-editor__body">
      <!-- ═══ 三个页签（各带 草稿 / 已完成 徽标）═══ -->
      <el-tabs v-model="activeTab" class="lqg-qc-editor__tabs" @tab-change="handleTabChange">
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
          <PreviewPane
            ref="previewPaneRef"
            :sample-id="sampleId"
            :doc-kind="currentTab.docKind"
            :doc-status="docStatusOf(currentTab)"
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

const sampleId = ref<string>((route.query.sampleId as string) || '');
const activeTab = ref<TabName>('sample-qc');
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

/** 取三份文档 + 图片 + 附件（首次访问后端就地建三份空草稿；样本不是 valid → 400） */
const reload = async () => {
  if (!sampleId.value) {
    loadError.value = t('lqg.qc.editor.missingSampleId');
    return;
  }
  loading.value = true;
  try {
    const res = await getQcBundle(sampleId.value);
    bundle.value = res.data;
    loadError.value = '';
  } catch (e: any) {
    // 400（样本不存在 / 不是有效样本）也走这里：把后端 msg 原样显示，不吞
    loadError.value = e?.message || t('lqg.qc.editor.loadFailed');
  } finally {
    loading.value = false;
  }
};

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

/** 换页签：右栏的面板按新的 docKind 重新读一次状态（页面图不自动重渲染，等用户点「预览」） */
const handleTabChange = async () => {
  await nextTick();
  previewPaneRef.value?.loadPages();
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

const goBack = () => {
  router.push({ path: '/sample' });
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
  await reload();
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
}
</style>
