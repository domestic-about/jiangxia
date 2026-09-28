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

    <!-- ═══ 设置改过（内部编号开关 / 模板升级）：先给旧的一版，后台按新设置重出，出好了自动换 ═══ -->
    <div v-if="!busy && status === 'done' && outdated" class="lqg-preview-pane__outdated">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>{{ t('lqg.qc.internalNo.outdated') }}</span>
    </div>

    <!-- ═══ 渲染中 ═══ -->
    <div v-if="busy" class="lqg-preview-pane__state">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>{{ t('lqg.qc.preview.rendering') }}</span>
    </div>

    <!-- ═══ 失败：原因 + 重新生成（FLOW:F-DOC-01.step6）。「重新生成」带 force，一定重出 ═══ -->
    <el-alert
      v-else-if="status === 'failed'"
      type="error"
      :closable="false"
      show-icon
      :title="t('lqg.qc.preview.failedTitle')"
      class="lqg-preview-pane__alert"
    >
      <div class="lqg-preview-pane__reason">{{ errorMsg || t('lqg.qc.preview.noReason') }}</div>
      <el-button type="primary" size="small" class="mt-2" @click="regenerate">
        {{ t('lqg.qc.preview.regenerate') }}
      </el-button>
    </el-alert>

    <!-- ═══ 生成中（在途 / 合并件成员变了正在重出）：不显示旧页面图，自动轮询 ═══ -->
    <div v-else-if="status === 'pending'" class="lqg-preview-pane__state">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>{{ t('lqg.qc.integrity.pending') }}</span>
      <span class="lqg-preview-pane__sub">{{ t('lqg.qc.integrity.pendingHint') }}</span>
    </div>

    <!-- ═══ 还没生成过 ═══ -->
    <div v-else-if="status === 'none'" class="lqg-preview-pane__state">
      <el-icon><Document /></el-icon>
      <span>{{ t('lqg.qc.preview.notGenerated') }}</span>
      <el-button type="primary" size="small" @click="render()">{{ t('lqg.qc.preview.preview') }}</el-button>
    </div>

    <!-- ═══ 内部版照出但缺图（#217）：说清缺了哪几张 + 重新生成 ═══ -->
    <el-alert
      v-if="!busy && status === 'done' && missingImageCount > 0"
      type="warning"
      :closable="false"
      show-icon
      :title="t('lqg.qc.integrity.missingTitle', { n: missingImageCount })"
      class="lqg-preview-pane__alert lqg-preview-pane__missing"
    >
      <div class="lqg-preview-pane__reason">{{ missingImages }}</div>
      <div class="lqg-preview-pane__sub">{{ t('lqg.qc.integrity.missingHint') }}</div>
      <el-button type="primary" size="small" class="mt-2" @click="regenerate">
        {{ t('lqg.qc.integrity.regenerate') }}
      </el-button>
    </el-alert>

    <!-- ═══ 生成好了：逐页显示页面图片 ═══ -->
    <div v-if="!busy && status === 'done'" class="lqg-preview-pane__pages">
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
import { getDocDownload, getDocPages, renderDoc, type DocAudience, type DocKind, type DocPagesVO } from '@/api/lqg/doc';
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
//      直到 done / failed（本组件的 `waitForRender`）；读到 pending（在途 / 合并件成员变了正在重出）
//      也在后台轮询，不显示上一版的页面图。
//   4) 「重新生成」一定重出（force）；内部版缺图时显示缺了哪几张（#217）。
//   5) 外部版的「内部编号」一格随系统参数「合作单位可见内部编号」（甲方 2026-09-24 意见第 23 行）：
//      提示语按这一版实际印没印说（后端 pages 的 internalNoShown）；设置刚改过时后端给
//      outdated=true（或外部版印了编号而开关已关时直接 pending），面板在后台轮询到按新设置出的那一版。
// ============================================================================

/**
 * pages 接口里工作台专用的两个键（外部小程序那条接口不转出它们）。
 * 放在本组件里而不是改 api 类型文件：只有预览面板用得着。
 */
type DocPagesWithSettings = DocPagesVO & {
  /** 这一版「内部编号」一格是否印出 */
  internalNoShown?: boolean;
  /** 这一版按旧设置 / 旧模板出的，后台正在按新设置重出 */
  outdated?: boolean;
};

const props = defineProps<{
  /** 样本 id（路由 query 带进来） */
  sampleId: string | number;
  /** 当前页签对应的 docKind（下划线，字典 lqg_doc_kind 同名） */
  docKind: DocKind;
  /** 该文档当前的 doc_status（draft / published）—— 只用来提示「改完要重新同步」 */
  docStatus?: string;
  /** 进页时先看哪一版（从首页异常清单点进来时是出问题的那一版；默认内部版） */
  initialAudience?: DocAudience;
}>();

/** `changed` = 面板里发生了会影响文档的事（渲染完成等）；`busy` = 正在渲染（父组件据此转按钮） */
const emit = defineEmits<{ (e: 'changed'): void; (e: 'busy', value: boolean): void }>();

const { t } = useI18n();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

/** 内外部版切换（UI:admin.doc.preview：可切换对照看；外部版里内部编号一格跟着「合作单位可见内部编号」开关走，关着为空、开着印出来） */
const audience = ref<DocAudience>(props.initialAudience === 'external' ? 'external' : 'internal');
const status = ref<'none' | 'pending' | 'done' | 'failed'>('none');
const errorMsg = ref<string | null>(null);
const pages = ref<{ pageNo: number; url: string }[]>([]);
/** 内部版照出但取不到的图（#217）：张数 + 明细 */
const missingImageCount = ref(0);
const missingImages = ref<string | null>(null);
/** 这一版「内部编号」一格印没印（外部版随开关） */
const internalNoShown = ref(false);
/** 这一版是按旧设置出的、后台在重出（面板照显示旧版并轮询） */
const outdated = ref(false);
const busy = ref(false);
/** 读到 pending 时在后台轮询（不占 busy，不挡用户切版本 / 下载） */
let polling = false;
/** 正在下载哪一份（docx / pdf / merged-docx / merged-pdf） */
const downloading = ref<string>('');

/** 轮询参数：每 2.5 秒看一眼，最多 40 次（≈100 秒）—— 后端单份渲染有 60 秒预算 */
const POLL_INTERVAL_MS = 2500;
const POLL_MAX = 40;

const pageUrls = computed(() => pages.value.map((page) => page.url));
const audienceHint = computed(() => {
  if (audience.value === 'internal') return t('lqg.qc.preview.internalHint');
  // 外部版按这一版实际印没印说；还没出好的时候只说规则（不猜这一版会是哪一种）
  if (status.value !== 'done') return t('lqg.qc.internalNo.externalRule');
  return internalNoShown.value ? t('lqg.qc.internalNo.externalShown') : t('lqg.qc.internalNo.externalHidden');
});
/** 评分表没有独立的合并件形态（合并件是「已完成的几份」拼起来的） */
const mergedSkipped = computed(() => props.docKind === 'organoid_score');

/** 「这份文档此刻该不该显示已完成」：状态机回草稿后要提示用户重新同步 */
const needsResync = computed(() => props.docStatus === 'draft' && status.value === 'done');

/**
 * 读一次页面图。
 *
 * ★ 「从没渲染过」是**正常的初始态**：后端回 `status=none`（H 批起；原来是 400 + 原因，
 *   请求层会把它弹成一条红色提示 —— Kevin 本机验收「网页工作台」第 6 行，切页签、首次进入都在弹），
 *   面板只显示「还没生成过页面图 [预览]」这个空态，不是 failed、也不弹任何提示。
 *   请求本身失败（网络 / 权限）时请求层已经说过原因，这里同样落到 `none`，不再补一条。
 * ★ 同一时刻只认最后一次：快速来回切页签时，先发的请求晚回来不许把后切的那一份盖掉（loadSeq）。
 */
/** 把一次 pages 的返回落到面板状态上（loadPages 与后台轮询共用） */
const applyPages = (data: DocPagesWithSettings) => {
  status.value = (data.status as typeof status.value) || 'done';
  errorMsg.value = data.errorMsg ?? null;
  pages.value = status.value === 'done' ? data.pages || [] : [];
  missingImageCount.value = Number(data.missingImageCount || 0);
  missingImages.value = data.missingImages ?? null;
  internalNoShown.value = data.internalNoShown === true;
  outdated.value = data.outdated === true;
};

let loadSeq = 0;
/** 面板已卸载（换了样本、离开页面）：后台轮询就此停下，不再替旧样本发请求 */
let disposed = false;
onBeforeUnmount(() => {
  disposed = true;
});

const loadPages = async () => {
  if (!props.sampleId || disposed) return;
  const seq = ++loadSeq;
  try {
    const res = await getDocPages(props.sampleId, props.docKind, audience.value);
    if (seq !== loadSeq) return;
    applyPages(res.data as DocPagesWithSettings);
  } catch (e: any) {
    if (seq !== loadSeq) return;
    status.value = 'none';
    errorMsg.value = e?.message ?? null;
    pages.value = [];
    missingImageCount.value = 0;
    missingImages.value = null;
    internalNoShown.value = false;
    outdated.value = false;
  }
  // ★ V04：成员变了（撤回 / 新完成）后合并件、或在途的一份会是 pending —— 后台自己轮询到出结果，
  //   不显示上一版的页面图，也不让用户对着「已完成却 0 页」发呆。
  // ★ 设置刚改过（outdated）：先显示旧的一版，后台轮询到按新设置出的那一版再换
  if ((status.value === 'pending' || outdated.value) && !busy.value) {
    pollInBackground();
  }
};

/** pending / outdated 时的后台轮询（同一时刻只有一个） */
const pollInBackground = async () => {
  if (polling) return;
  polling = true;
  try {
    for (let i = 0; i < POLL_MAX && (status.value === 'pending' || outdated.value) && !busy.value; i++) {
      await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
      if (busy.value || disposed) break;
      const seq = ++loadSeq;
      const res = await getDocPages(props.sampleId, props.docKind, audience.value).catch(() => null);
      if (!res || seq !== loadSeq) break;
      applyPages(res.data as DocPagesWithSettings);
    }
  } finally {
    polling = false;
  }
};

/** 轮询到 done / failed 为止（后端异步渲染；pending 时页面图一定是空的） */
const waitForRender = async () => {
  for (let i = 0; i < POLL_MAX && !disposed; i++) {
    await loadPages();
    if (status.value === 'done' || status.value === 'failed') return;
    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
  }
};

/**
 * 触发一次渲染（FLOW:F-QC-01.step5 的「点预览 → 触发渲染」）。
 *
 * ★ 只渲染**当前页签**的那一份（当前选中的版本）——「完成并同步」才渲染内外部两版 + 合并件。
 * ★ `force=true` 是「重新生成」：不看缓存，一定重出（V04：旧版「重新生成」永远返回 cached 空转）。
 */
const render = async (force = false) => {
  if (!props.sampleId) return;
  busy.value = true;
  emit('busy', true);
  try {
    const res = await renderDoc(props.sampleId, props.docKind, audience.value, force === true);
    if (res.data.status === 'done') {
      // 命中缓存或立刻成功：直接取页面图
      await loadPages();
      if (force === true) {
        proxy?.$modal.msgSuccess(t('lqg.qc.integrity.regenerated'));
      }
    } else if (res.data.status === 'failed') {
      status.value = 'failed';
      errorMsg.value = res.data.errorMsg ?? null;
      pages.value = [];
      missingImageCount.value = Number(res.data.missingImageCount || 0);
      missingImages.value = res.data.missingImages ?? null;
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

/**
 * 取短时签名链接并打开（真下载；链接 10 分钟过期，所以每次都现取）。
 *
 * ★ 还没有产物（还没生成 / 正在生成 / 上次失败 / 合并件还拼不出来）后端回 400 + 一句人话，
 *   请求层已经把它弹出来了 —— 这就是「点了下载才给的那一句提示」，这里**不再补第二条**
 *   （原来补的是 `String('error')` 或「合并件还没法下载：」半句，和请求层那条叠成两条）。
 */
const download = async (format: 'docx' | 'pdf', merged = false) => {
  if (!props.sampleId) return;
  const key = merged ? `merged-${format}` : format;
  downloading.value = key;
  try {
    const res = await getDocDownload(props.sampleId, merged ? 'merged' : props.docKind, format, audience.value);
    window.open(res.data.url, '_blank');
  } catch {
    // 原因请求层已经提示过（见上）
  } finally {
    downloading.value = '';
  }
};

const downloadMerged = (format: 'docx' | 'pdf') => download(format, true);

/** 「重新生成」：一定重出一版（failed / 缺图两处的按钮都走这里） */
const regenerate = () => render(true);

/** 换页签（docKind 变了）/ 面板刚建好：重置成「还没看」并读一次状态 */
const reset = async () => {
  status.value = 'none';
  errorMsg.value = null;
  pages.value = [];
  missingImageCount.value = 0;
  missingImages.value = null;
  internalNoShown.value = false;
  outdated.value = false;
  await loadPages();
};

defineExpose({ render, reset, loadPages });

// 换页签 → 父组件改 docKind：重新读状态。★ 这是换页签时**唯一**读状态的地方
// （父组件原来在 @tab-change 里也调一次 loadPages → 一次切换两个请求、两条提示，网页工作台第 6 行）；
// 换样本不走这里：父组件按 :key（样本 + 进来时要看的版本）整块重建面板。
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
  .lqg-preview-pane__outdated {
    margin-top: 8px;
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: var(--lqg-ink-3);
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
  .lqg-preview-pane__sub {
    margin-top: 4px;
    font-size: 12px;
    color: var(--lqg-ink-3);
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
