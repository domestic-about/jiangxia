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

    <el-alert
      v-if="loadError"
      type="error"
      :closable="false"
      show-icon
      :title="loadError"
      class="mb-2"
    />

    <el-card v-loading="loading" shadow="hover" class="lqg-qc-editor__body">
      <!-- ═══ 三个页签（各带 草稿 / 已完成 徽标；后两个本张占位）═══ -->
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
          <SampleQcTab
            v-if="activeTab === 'sample-qc' && bundle"
            ref="sampleTabRef"
            :sample-id="sampleId"
            :doc="bundle.sampleQc"
            :readonly="!canEdit"
            @dirty="handleDirty"
            @changed="reload"
          />
          <el-empty v-else :description="placeholderText" />
        </div>

        <div class="lqg-qc-editor__right">
          <div class="lqg-qc-editor__pane-title">{{ t('lqg.qc.editor.previewTitle') }}</div>

          <!-- failed 时看得见原因、点「重新生成」重试（FLOW:F-DOC-01.step6；
               页面图 / 下载 / 完成并同步是 DOC-PUBLISH-001） -->
          <el-alert
            v-if="renderState.status === 'failed'"
            type="error"
            :closable="false"
            show-icon
            :title="t('lqg.qc.editor.renderFailed')"
          >
            <div class="lqg-qc-editor__reason">{{ renderState.errorMsg || t('lqg.qc.editor.renderNoReason') }}</div>
            <el-button
              type="primary"
              size="small"
              :loading="rendering"
              class="mt-2"
              @click="handleRegenerate"
            >
              {{ t('lqg.qc.editor.regenerate') }}
            </el-button>
          </el-alert>
          <el-alert
            v-else-if="renderState.status === 'pending'"
            type="warning"
            :closable="false"
            show-icon
            :title="t('lqg.qc.editor.renderPending')"
          />
          <el-alert
            v-else-if="renderState.status === 'done'"
            type="success"
            :closable="false"
            show-icon
            :title="t('lqg.qc.editor.renderDone', { pages: renderState.pages })"
          />

          <div class="lqg-qc-editor__placeholder">
            <el-icon class="lqg-qc-editor__placeholder-icon"><Document /></el-icon>
            <p class="lqg-qc-editor__placeholder-text">{{ t('lqg.qc.editor.previewPlaceholder') }}</p>
            <p class="lqg-qc-editor__placeholder-sub">{{ t('lqg.qc.editor.previewPlaceholderSub') }}</p>
          </div>
        </div>
      </div>

      <!-- ═══ 页脚按钮 ═══ -->
      <div class="lqg-qc-editor__footer">
        <el-button
          type="primary"
          :loading="saving"
          :disabled="!bundle || activeTab !== 'sample-qc' || !canEdit"
          @click="handleSaveDraft"
        >
          {{ t('lqg.qc.editor.saveDraft') }}
        </el-button>
        <el-tooltip :content="t('lqg.qc.editor.notYet')" placement="top">
          <span><el-button disabled>{{ t('lqg.qc.editor.preview') }}</el-button></span>
        </el-tooltip>
        <el-tooltip :content="t('lqg.qc.editor.notYet')" placement="top">
          <span><el-button disabled>{{ t('lqg.qc.editor.publish') }}</el-button></span>
        </el-tooltip>
        <span class="lqg-qc-editor__footer-hint">{{ t('lqg.qc.editor.footerHint') }}</span>
      </div>
    </el-card>
  </div>
</template>

<script setup name="LqgQcEditor" lang="ts">
import { getQcBundle, type QcDocBundleVO, type QcDocKind } from '@/api/lqg/qc';
import { getDocPages, renderDoc } from '@/api/lqg/doc';
import SampleQcTab from './SampleQcTab.vue';
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
// ★ 右栏预览面板本张是占位（页面图 / 下载 / 完成并同步 = DOC-PUBLISH-001）；
//   只有「渲染失败 → 重新生成」这一条先接上（FLOW:F-DOC-01.step6）。
// ============================================================================

const { t } = useI18n();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const route = useRoute();
const router = useRouter();

const TABS: { name: 'sample-qc' | 'organoid-qc' | 'score'; labelKey: string; docKind: QcDocKind; placeholderKey: string }[] = [
  { name: 'sample-qc', labelKey: 'lqg.qc.editor.tabSampleQc', docKind: 'sample_qc', placeholderKey: '' },
  { name: 'organoid-qc', labelKey: 'lqg.qc.editor.tabOrganoidQc', docKind: 'organoid_qc', placeholderKey: 'lqg.qc.editor.organoidPlaceholder' },
  { name: 'score', labelKey: 'lqg.qc.editor.tabScore', docKind: 'organoid_score', placeholderKey: 'lqg.qc.editor.scorePlaceholder' }
];

const sampleId = ref<string>((route.query.sampleId as string) || '');
const activeTab = ref<'sample-qc' | 'organoid-qc' | 'score'>('sample-qc');
const loading = ref(false);
const saving = ref(false);
const rendering = ref(false);
const dirty = ref(false);
const loadError = ref('');
const bundle = ref<QcDocBundleVO | null>(null);
const renderState = ref<{ status: string; errorMsg?: string | null; pages: number }>({ status: 'none', pages: 0 });
const sampleTabRef = ref<InstanceType<typeof SampleQcTab>>();

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
const placeholderText = computed(() => (currentTab.value.placeholderKey ? t(currentTab.value.placeholderKey) : ''));

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
  await loadRenderState();
};

/** 渲染状态（只有 failed 会在右栏亮出来；从没渲染过时后端 400 → 当成「还没生成」） */
const loadRenderState = async () => {
  if (!sampleId.value) return;
  try {
    const res = await getDocPages(sampleId.value, currentTab.value.docKind, 'internal');
    renderState.value = {
      status: res.data.status || 'none',
      errorMsg: res.data.errorMsg,
      pages: (res.data.pages || []).length
    };
  } catch (e: any) {
    renderState.value = { status: 'none', errorMsg: e?.message };
  }
};

/** 「重新生成」= 再调一次 render（幂等：指纹没变直接返回 done；失败拿回新的 errorMsg） */
const handleRegenerate = async () => {
  rendering.value = true;
  try {
    const res = await renderDoc(sampleId.value, currentTab.value.docKind, 'internal');
    if (res.data.status === 'done') {
      proxy?.$modal.msgSuccess(t('lqg.qc.editor.regenerated'));
    } else if (res.data.status === 'failed') {
      proxy?.$modal.msgError(t('lqg.qc.editor.renderStillFailed') + (res.data.errorMsg || t('lqg.qc.editor.renderNoReason')));
    }
  } catch (e: any) {
    proxy?.$modal.msgError(e?.message || String(e));
  } finally {
    rendering.value = false;
    await loadRenderState();
  }
};

const handleDirty = (value: boolean) => {
  dirty.value = value;
};

const handleTabChange = async () => {
  await loadRenderState();
};

const handleSaveDraft = async () => {
  if (activeTab.value !== 'sample-qc') return;
  saving.value = true;
  try {
    await sampleTabRef.value?.save();
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
