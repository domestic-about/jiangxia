<template>
  <div class="p-2 lqg-doc">
    <el-card shadow="hover">
      <template #header>
        <div class="lqg-doc__head">
          <span class="lqg-doc__title">文档渲染状态</span>
          <span class="lqg-doc__subtitle">按样本查看某一份质控文档的生成状态；失败或缺图时显示原因，可以重新生成</span>
        </div>
      </template>

      <el-form :model="query" label-width="90px" inline @submit.prevent>
        <el-form-item label="样本 id">
          <el-input v-model="query.sampleId" placeholder="两张样本表里的样本 id" clearable style="width: 200px" @keyup.enter="loadPages" />
        </el-form-item>
        <el-form-item label="文档">
          <el-select v-model="query.docKind" style="width: 180px">
            <el-option v-for="d in docKinds" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本">
          <el-select v-model="query.audience" style="width: 140px">
            <el-option label="内部版" value="internal" />
            <el-option label="外部版" value="external" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadPages">查询状态</el-button>
        </el-form-item>
      </el-form>

      <el-empty v-if="!pages.status && !loading" description="填样本 id 后点「查询状态」" :image-size="80" />

      <!-- 状态条：pending / done / failed 三种，failed 时给原因 + 「重新生成」 -->
      <el-alert
        v-if="pages.status === 'failed'"
        type="error"
        :closable="false"
        show-icon
        title="这份文档渲染失败了"
        class="mb-2"
      >
        <div class="lqg-doc__reason">{{ pages.errorMsg || '（没有记下原因）' }}</div>
        <el-button type="primary" size="small" :loading="rendering" class="mt-2" @click="regenerate">
          重新生成
        </el-button>
      </el-alert>
      <el-alert
        v-else-if="pages.status === 'done'"
        type="success"
        :closable="false"
        show-icon
        :title="`已生成：${(pages.pages || []).length} 页 · 模板版本 ${pages.templateVersion || '-'}`"
        class="mb-2"
      />
      <el-alert
        v-if="pages.status === 'done' && (pages.missingImageCount || 0) > 0"
        type="warning"
        :closable="false"
        show-icon
        :title="`内部版缺 ${pages.missingImageCount} 张图`"
        class="mb-2"
      >
        <div class="lqg-doc__reason">{{ pages.missingImages }}</div>
        <el-button type="primary" size="small" :loading="rendering" class="mt-2" @click="regenerate">重新生成</el-button>
      </el-alert>
      <el-alert
        v-if="pages.status === 'pending'"
        type="warning"
        :closable="false"
        show-icon
        title="正在生成…（好了会自动刷新）"
        class="mb-2"
      />
      <!-- 从没生成过（pages 回 status=none，不再是 400）：说清楚，并能直接生成一版 -->
      <el-alert v-if="pages.status === 'none'" type="info" :closable="false" show-icon title="这份文档还没生成过" class="mb-2">
        <el-button type="primary" size="small" :loading="rendering" class="mt-2" @click="regenerate">生成</el-button>
      </el-alert>

      <!-- 产物下载：docx 与 pdf 走同一个 download 接口（短时签名链接） -->
      <div v-if="pages.status === 'done'" class="lqg-doc__downloads">
        <el-button size="small" @click="download('docx')">下载 Word</el-button>
        <el-button size="small" @click="download('pdf')">下载 PDF</el-button>
      </div>

      <!-- 页码清单（这里只列页码；逐页看图在质控文档页的预览面板） -->
      <div v-if="(pages.pages || []).length" class="lqg-doc__pages">
        <el-tag v-for="p in pages.pages" :key="p.pageNo" class="m-1">第 {{ p.pageNo }} 页</el-tag>
      </div>
    </el-card>
  </div>
</template>

<script setup name="LqgDocConsole" lang="ts">
import { reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { DocAudience, DocKind, DocPagesVO, getDocDownload, getDocPages, renderDoc } from '@/api/lqg/doc';

// ============================================================================
// 文档渲染状态（运维用的隐藏页，菜单 5520）。
//
// ★ 独立验收 V30：不再默认填测试样本 id、页面上不露票号 —— 样本 id 由人填，或从链接带进来
//   （`?sampleId=&docKind=&audience=`）；没有样本 id 时不发请求。
// ★ 「重新生成」= render + force：一定重出一版（不会被缓存命中空转，V04）。
// ============================================================================

const docKinds: { label: string; value: DocKind }[] = [
  { label: '样本质控表', value: 'sample_qc' },
  { label: '类器官质控表', value: 'organoid_qc' },
  { label: '类器官质量评分表', value: 'organoid_score' },
  { label: '合并件', value: 'merged' }
];

const route = useRoute();
const KINDS = docKinds.map((d) => d.value);
const queryKind = route.query.docKind as DocKind;

const query = reactive<{ sampleId: string; docKind: DocKind; audience: DocAudience }>({
  sampleId: typeof route.query.sampleId === 'string' ? route.query.sampleId : '',
  docKind: KINDS.includes(queryKind) ? queryKind : 'sample_qc',
  audience: route.query.audience === 'external' ? 'external' : 'internal'
});

const pages = ref<DocPagesVO>({});
const loading = ref(false);
const rendering = ref(false);

/** 状态 = 再一次 pages 查询（status/errorMsg/pages 都从它来） */
async function loadPages() {
  if (!query.sampleId) {
    ElMessage.warning('先填样本 id');
    return;
  }
  loading.value = true;
  try {
    const res = await getDocPages(query.sampleId, query.docKind, query.audience);
    pages.value = res.data;
    if (res.data.status === 'pending') {
      // 在途 / 失效待重出：过一会儿自己再看一眼（后端读到 pending 时已排了一次重出）
      setTimeout(() => {
        if (pages.value.status === 'pending') loadPages();
      }, 3000);
    }
  } catch (e: any) {
    // 400（样本不存在等）走这里；「还没生成过」不再是 400，而是 status=none（见上面那条提示）
    pages.value = { status: 'failed', errorMsg: e?.message || String(e) };
  } finally {
    loading.value = false;
  }
}

/** 「重新生成」= render + force：一定重出一版（不再被缓存命中空转），失败则拿回新的 errorMsg */
async function regenerate() {
  rendering.value = true;
  try {
    const res = await renderDoc(query.sampleId, query.docKind, query.audience, true);
    pages.value = { ...pages.value, status: res.data.status, errorMsg: res.data.errorMsg, contentHash: res.data.contentHash };
    if (res.data.status === 'done') {
      ElMessage.success('已重新生成');
      await loadPages();
    } else if (res.data.status === 'failed') {
      ElMessage.error('还是失败：' + (res.data.errorMsg || '（没有记下原因）'));
    }
  } catch (e: any) {
    ElMessage.error(e?.message || String(e));
  } finally {
    rendering.value = false;
  }
}

async function download(format: 'docx' | 'pdf') {
  const res = await getDocDownload(query.sampleId, query.docKind, format, query.audience);
  window.open(res.data.url, '_blank');
}

if (query.sampleId) {
  loadPages();
}
</script>

<style scoped>
.lqg-doc__head {
  display: flex;
  flex-direction: column;
}
.lqg-doc__title {
  font-weight: 600;
}
.lqg-doc__subtitle {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.lqg-doc__reason {
  margin-top: 4px;
  word-break: break-all;
}
.lqg-doc__downloads,
.lqg-doc__pages {
  margin-top: 8px;
}
</style>
