<template>
  <div class="p-2 lqg-doc">
    <el-card shadow="hover">
      <template #header>
        <div class="lqg-doc__head">
          <span class="lqg-doc__title">文档渲染状态</span>
          <span class="lqg-doc__subtitle">failed 时显示原因，点「重新生成」重试（DOC-PDF-001 · FLOW:F-DOC-01.step6）</span>
        </div>
      </template>

      <el-form :model="query" label-width="90px" inline>
        <el-form-item label="样本 id">
          <el-input v-model="query.sampleId" placeholder="9000001001" style="width: 160px" />
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
          <el-button type="primary" :loading="loading" @click="loadPages">刷新状态</el-button>
        </el-form-item>
      </el-form>

      <!-- 状态条：pending / done / failed 三种，failed 时给原因 + 「重新生成」 -->
      <el-alert
        v-if="pages.status === 'failed'"
        type="error"
        :closable="false"
        show-icon
        title="这份文档渲染失败了"
        class="mb-2"
      >
        <div class="lqg-doc__reason">{{ pages.errorMsg || '（后端没给原因）' }}</div>
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
        v-else-if="pages.status === 'pending'"
        type="warning"
        :closable="false"
        show-icon
        title="正在生成…"
        class="mb-2"
      />

      <!-- 产物下载：docx 与 pdf 走同一个 download 接口（短时签名链接） -->
      <div v-if="pages.status === 'done'" class="lqg-doc__downloads">
        <el-button size="small" @click="download('docx')">下载 Word</el-button>
        <el-button size="small" @click="download('pdf')">下载 PDF</el-button>
      </div>

      <!-- 页码清单（不在这里渲染图片：小程序预览页是 DOC-MP-002，工作台预览是 DOC-PUBLISH-001） -->
      <div v-if="(pages.pages || []).length" class="lqg-doc__pages">
        <el-tag v-for="p in pages.pages" :key="p.pageNo" class="m-1">第 {{ p.pageNo }} 页</el-tag>
      </div>
    </el-card>
  </div>
</template>

<script setup name="LqgDocConsole" lang="ts">
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { DocAudience, DocKind, DocPagesVO, getDocDownload, getDocPages, renderDoc } from '@/api/lqg/doc';

const docKinds: { label: string; value: DocKind }[] = [
  { label: '样本质控表', value: 'sample_qc' },
  { label: '类器官质控表', value: 'organoid_qc' },
  { label: '类器官质量评分表', value: 'organoid_score' },
  { label: '合并件', value: 'merged' }
];

const query = reactive<{ sampleId: string; docKind: DocKind; audience: DocAudience }>({
  sampleId: '9000001001',
  docKind: 'organoid_score',
  audience: 'internal'
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
  } catch (e: any) {
    // 400（还没生成过 / 样本不存在）也走这里：把后端的 msg 原样显示，不吞
    pages.value = { status: 'failed', errorMsg: e?.message || String(e) };
  } finally {
    loading.value = false;
  }
}

/** 「重新生成」= 再调一次 render：指纹没变就直接返回 done，失败则拿回新的 errorMsg */
async function regenerate() {
  rendering.value = true;
  try {
    const res = await renderDoc(query.sampleId, query.docKind, query.audience);
    pages.value = { ...pages.value, status: res.data.status, errorMsg: res.data.errorMsg, contentHash: res.data.contentHash };
    if (res.data.status === 'done') {
      ElMessage.success('已重新生成');
      await loadPages();
    } else if (res.data.status === 'failed') {
      ElMessage.error('还是失败：' + (res.data.errorMsg || '（后端没给原因）'));
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

loadPages();
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
