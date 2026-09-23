<template>
  <div class="lqg-preview-pane">
    <!-- ═══ 头部：版本切换 + 刷新 ═══ -->
    <div class="lqg-preview-pane__bar">
      <el-radio-group v-model="audience" size="small" :disabled="busy" @change="handleAudienceChange">
        <el-radio-button label="internal">{{ t('lqg.qc.preview.audienceInternal') }}</el-radio-button>
        <el-radio-button label="external">{{ t('lqg.qc.preview.audienceExternal') }}</el-radio-button>
      </el-radio-group>
      <span class="lqg-preview-pane__audience-hint">{{ audienceHint }}</span>
    </div>

    <!-- ═══ 状态机回草稿但旧产物还在：提示要重新同步（FLOW:F-QC-01.step7）═══ -->
    <el-alert
      v-if="needsResync"
      type="warning"
      :closable="false"
      show-icon
      :title="t('lqg.qc.preview.needsResync')"
      class="lqg-preview-pane__alert"
    />

    <div class="lqg-preview-pane__bar">
      <el-button link type="primary" size="small" :loading="busy" @click="loadPages">
        {{ t('lqg.qc.preview.refresh') }}
      </el-button>
    </div>

    <!-- ═══ 渲染中 ═══ -->
    <div v-if="busy" class="lqg-preview-pane__state">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>{{ t('lqg.qc.preview.rendering') }}</span>
    </div>

    <!-- ═══ 失败：原因 + 重新生成（FLOW:F-DOC-01.step6）═══ -->
    <el-alert
      v-else-if="status === 'failed'"
      type="error"
      :closable="false"
      show-icon
      :title="t('lqg.qc.preview.failedTitle')"
      class="lqg-preview-pane__alert"
    >
      <div class="lqg-preview-pane__reason">{{ errorMsg || t('lqg.qc.preview.noReason') }}</div>
      <el-button type="primary" size="small" class="mt-2" @click="render">
        {{ t('lqg.qc.preview.regenerate') }}
      </el-button>
    </el-alert>

    <!-- ═══ 还没生成过 ═══ -->
    <div v-else-if="status === 'none'" class="lqg-preview-pane__state">
      <el-icon><Document /></el-icon>
      <span>{{ t('lqg.qc.preview.notGenerated') }}</span>
      <el-button type="primary" size="small" @click="render">{{ t('lqg.qc.preview.preview') }}</el-button>
    </div>

    <!-- ═══ 生成好了：逐页显示页面图片 ═══ -->
    <div v-else class="lqg-preview-pane__pages">
      <div v-for="page in pages" :key="page.pageNo" class="lqg-preview-pane__page">
        <div class="lqg-preview-pane__page-no">{{ t('lqg.qc.preview.pageNo', { n: page.pageNo }) }}</div>
        <el-image :src="page.url" fit="contain" class="lqg-preview-pane__page-img" :preview-src-list="pageUrls" />
      </div>
    </div>

    <!-- ═══ 下载（真下载：取短时签名链接再开）═══ -->
    <div class="lqg-preview-pane__downloads">
      <div class="lqg-preview-pane__downloads-title">{{ t('lqg.qc.preview.downloadTitle') }}</div>
      <div class="lqg-preview-pane__downloads-row">
        <el-button size="small" :disabled="status !== 'done'" :loading="downloading === 'docx'" @click="download('docx')">
          {{ t('lqg.qc.preview.downloadWord') }}
        </el-button>
        <el-button size="small" :disabled="status !== 'done'" :loading="downloading === 'pdf'" @click="download('pdf')">
          {{ t('lqg.qc.preview.downloadPdf') }}
        </el-button>
        <el-button
          size="small"
          :disabled="status !== 'done'"
          :loading="downloading === 'merged-docx'"
          @click="downloadMerged('docx')"
        >
          {{ t('lqg.qc.preview.downloadMergedWord') }}
        </el-button>
        <el-button
          size="small"
          :disabled="status !== 'done'"
          :loading="downloading === 'merged-pdf'"
          @click="downloadMerged('pdf')"
        >
          {{ t('lqg.qc.preview.downloadMergedPdf') }}
        </el-button>
      </div>
      <div v-if="mergedSkipped" class="lqg-preview-pane__merged-tip">{{ t('lqg.qc.preview.mergedNotForScore') }}</div>
    </div>
  </div>
</template>

<script setup name="LqgQcPreviewPane" lang="ts">
import { getDocDownload, getDocPages, renderDoc, type DocAudience, type DocKind } from '@/api/lqg/doc';
import { useI18n } from 'vue-i18n';

// ============================================================================
// 文档预览面板（UI:admin.doc.preview / FLOW:F-QC-01.step5，DOC-PUBLISH-001）
//
// 落点：质控文档编辑页（views/lqg/qc/editor/index.vue）的右栏 —— **不是**新开页面。
//
// ★ 同一份东西的三个口径（做反了就是 counterfeit 里那句「和小程序看到的不是同一份」）：
//   1) 页面图来自 `GET /lqg/doc/{sampleId}/{docKind}/pages` 的 `pages[].url`
//      —— 那就是「将要下载的 Word / PDF」转出来的同一份 PDF 的页面图
//      （DOC-PDF-001 的流水线：一个指纹、三种产物）。**不 iframe PDF 链接**。
//   2) 下载走 `GET …/download?format=`，拿到的是**10 分钟短时签名链接**：不缓存、不存库。
//   3) 渲染是异步的（点「完成并同步」后后端排队），所以 render 之后要**轮询 pages**，
//      直到 done / failed（本组件的 `waitForRender`）。
// ============================================================================

const props = defineProps<{
  /** 样本 id（路由 query 带进来） */
  sampleId: string | number;
  /** 当前页签对应的 docKind（下划线，字典 lqg_doc_kind 同名） */
  docKind: DocKind;
  /** 该文档当前的 doc_status（draft / published）—— 只用来提示「改完要重新同步」 */
  docStatus?: string;
}>();

/** `changed` = 面板里发生了会影响文档的事（渲染完成等）；`busy` = 正在渲染（父组件据此转按钮） */
const emit = defineEmits<{ (e: 'changed'): void; (e: 'busy', value: boolean): void }>();

const { t } = useI18n();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

/** 内外部版切换（UI:admin.doc.preview：可切换对照看；外部版里内部编号一格为空） */
const audience = ref<DocAudience>('internal');
const status = ref<'none' | 'pending' | 'done' | 'failed'>('none');
const errorMsg = ref<string | null>(null);
const pages = ref<{ pageNo: number; url: string }[]>([]);
const busy = ref(false);
/** 正在下载哪一份（docx / pdf / merged-docx / merged-pdf） */
const downloading = ref<string>('');

/** 轮询参数：每 2.5 秒看一眼，最多 40 次（≈100 秒）—— 后端单份渲染有 60 秒预算 */
const POLL_INTERVAL_MS = 2500;
const POLL_MAX = 40;

const pageUrls = computed(() => pages.value.map((page) => page.url));
const audienceHint = computed(() =>
  audience.value === 'internal' ? t('lqg.qc.preview.internalHint') : t('lqg.qc.preview.externalHint')
);
/** 评分表没有独立的合并件形态（合并件是「已完成的几份」拼起来的） */
const mergedSkipped = computed(() => props.docKind === 'organoid_score');

/** 「这份文档此刻该不该显示已完成」：状态机回草稿后要提示用户重新同步 */
const needsResync = computed(() => props.docStatus === 'draft' && status.value === 'done');

/**
 * 读一次页面图。
 *
 * ★ 「从没渲染过」后端返回 400 + 原因（DOC-PDF-001 的口径），这里是**正常的初始态**，
 *   当成 `none` 处理（不是 failed）—— 不能把「还没点过预览」显示成「渲染失败」。
 */
const loadPages = async () => {
  if (!props.sampleId) return;
  try {
    const res = await getDocPages(props.sampleId, props.docKind, audience.value);
    status.value = (res.data.status as typeof status.value) || 'done';
    errorMsg.value = res.data.errorMsg ?? null;
    pages.value = res.data.pages || [];
  } catch (e: any) {
    status.value = 'none';
    errorMsg.value = e?.message ?? null;
    pages.value = [];
  }
};

/** 轮询到 done / failed 为止（后端异步渲染；pending 时页面图一定是空的） */
const waitForRender = async () => {
  for (let i = 0; i < POLL_MAX; i++) {
    await loadPages();
    if (status.value === 'done' || status.value === 'failed') return;
    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
  }
};

/**
 * 触发一次内部版渲染（FLOW:F-QC-01.step5 的「点预览 → 触发渲染」）。
 *
 * ★ 只渲染**当前页签**的那一份（内部版）——「完成并同步」才渲染内外部两版 + 合并件。
 *   渲染完不等后端，直接轮询 pages。
 */
const render = async () => {
  if (!props.sampleId) return;
  busy.value = true;
  emit('busy', true);
  try {
    const res = await renderDoc(props.sampleId, props.docKind, audience.value);
    if (res.data.status === 'done') {
      // 命中缓存或立刻成功：直接取页面图
      await loadPages();
    } else if (res.data.status === 'failed') {
      status.value = 'failed';
      errorMsg.value = res.data.errorMsg ?? null;
      pages.value = [];
    } else {
      status.value = 'pending';
      await waitForRender();
    }
  } catch (e: any) {
    status.value = 'failed';
    errorMsg.value = e?.message ?? String(e);
    pages.value = [];
  } finally {
    busy.value = false;
    emit('busy', false);
    emit('changed');
  }
};

/** 切内外部版：重新读一次页面图（两版是两份独立产物，指纹/对象键都不同） */
const handleAudienceChange = async () => {
  busy.value = true;
  pages.value = [];
  try {
    await loadPages();
  } finally {
    busy.value = false;
  }
};

/** 取短时签名链接并打开（真下载；链接 10 分钟过期，所以每次都现取） */
const download = async (format: 'docx' | 'pdf', merged = false) => {
  if (!props.sampleId) return;
  const key = merged ? `merged-${format}` : format;
  downloading.value = key;
  try {
    const res = await getDocDownload(props.sampleId, merged ? 'merged' : props.docKind, format, audience.value);
    window.open(res.data.url, '_blank');
  } catch (e: any) {
    if (merged) {
      proxy?.$modal.msgWarning(t('lqg.qc.preview.mergedUnavailable') + (e?.message || ''));
    } else {
      proxy?.$modal.msgError(e?.message || String(e));
    }
  } finally {
    downloading.value = '';
  }
};

const downloadMerged = (format: 'docx' | 'pdf') => download(format, true);

/** 页签切换时由父组件调用：重置成「还没看」并读一次状态 */
const reset = async () => {
  status.value = 'none';
  errorMsg.value = null;
  pages.value = [];
  await loadPages();
};

defineExpose({ render, reset, loadPages });

// 换页签 → 父组件改 docKind：重新读状态（v-if 由父组件按 docKind 控制，这里兜一手）
watch(
  () => props.docKind,
  async () => {
    await reset();
  },
  { immediate: true }
);
</script>

<style scoped lang="scss">
.lqg-preview-pane {
  .lqg-preview-pane__bar {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
  .lqg-preview-pane__audience-hint {
    font-size: 12px;
    color: var(--lqg-ink-3);
    flex: 1;
  }
  .lqg-preview-pane__state {
    margin-top: 12px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: var(--lqg-ink-3);
  }
  .lqg-preview-pane__alert {
    margin-top: 10px;
  }
  .lqg-preview-pane__reason {
    margin-top: 4px;
    word-break: break-all;
  }
  .lqg-preview-pane__pages {
    margin-top: 10px;
    display: flex;
    flex-direction: column;
    gap: 10px;
    max-height: 560px;
    overflow-y: auto;
  }
  .lqg-preview-pane__page-no {
    font-size: 12px;
    color: var(--lqg-ink-3);
    margin-bottom: 4px;
  }
  .lqg-preview-pane__page-img {
    width: 100%;
    border: 1px solid var(--lqg-line);
    background: var(--lqg-card);
  }
  .lqg-preview-pane__downloads {
    margin-top: 12px;
    padding-top: 10px;
    border-top: 1px solid var(--lqg-line);
  }
  .lqg-preview-pane__downloads-title {
    font-size: 12px;
    color: var(--lqg-ink-3);
    margin-bottom: 6px;
  }
  .lqg-preview-pane__downloads-row {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .lqg-preview-pane__merged-tip {
    margin-top: 6px;
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
}
</style>
