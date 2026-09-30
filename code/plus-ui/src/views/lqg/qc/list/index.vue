<template>
  <div class="p-2 lqg-qclist">
    <el-card shadow="hover">
      <template #header>
        <div class="lqg-qclist__head">
          <span class="lqg-qclist__title">{{ t('lqg.qc.list.title') }}</span>
          <span class="lqg-qclist__subtitle">{{ t('lqg.qc.list.subtitle') }}</span>
        </div>
      </template>

      <!-- 筛选：关键字 / 样本类别 / 填写进度 / 收样日期区间 -->
      <el-form :model="queryParams" label-width="86px" class="lqg-qclist__filter" @submit.prevent>
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.qc.list.filter.keyword')">
              <el-input
                v-model="queryParams.keyword"
                :placeholder="t('lqg.qc.list.filter.keywordPlaceholder')"
                clearable
                class="lqg-qclist__control"
                @keyup.enter="handleQuery"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.qc.list.filter.sampleKind')">
              <el-select v-model="queryParams.sampleKind" clearable class="lqg-qclist__control" @change="handleQuery">
                <el-option :label="t('lqg.qc.list.kind.tissue')" value="tissue" />
                <el-option :label="t('lqg.qc.list.kind.organoid')" value="organoid" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.qc.list.filter.progress')">
              <el-select v-model="queryParams.progress" clearable class="lqg-qclist__control" @change="handleQuery">
                <el-option v-for="p in PROGRESSES" :key="p" :label="t(`lqg.qc.list.progress.${p}`)" :value="p" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.qc.list.filter.receiveRange')">
              <el-date-picker
                v-model="receiveRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                :start-placeholder="t('lqg.qc.list.filter.receiveBegin')"
                :end-placeholder="t('lqg.qc.list.filter.receiveEnd')"
                class="lqg-qclist__control"
                clearable
                @change="handleQuery"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item label=" ">
              <el-button type="primary" icon="Search" @click="handleQuery">{{ t('lqg.qc.list.search') }}</el-button>
              <el-button icon="Refresh" @click="resetQuery">{{ t('lqg.qc.list.reset') }}</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-table v-loading="loading" :data="rows" border :empty-text="t('lqg.qc.list.empty')" @row-dblclick="openQc">
        <el-table-column :label="t('lqg.qc.list.col.internalNo')" prop="internalNo" min-width="110" :show-overflow-tooltip="true">
          <template #default="scope">
            <el-link type="primary" underline="never" class="lqg-qclist__mono" @click="openQc(scope.row)">
              {{ scope.row.internalNo || '—' }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.qc.list.col.sampleKind')" prop="sampleKind" width="125">
          <template #default="scope">{{ t(`lqg.qc.list.kind.${scope.row.sampleKind}`) }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.qc.list.col.sourceUnit')" prop="sourceUnitName" min-width="100" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.sourceUnitName || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.qc.list.col.receiveDate')" prop="receiveDate" width="100" align="center">
          <template #default="scope">{{ scope.row.receiveDate || '—' }}</template>
        </el-table-column>
        <el-table-column v-for="c in DOC_COLUMNS" :key="c.key" :label="t(c.labelKey)" width="135" align="center">
          <template #default="scope">
            <el-tag size="small" :type="statusTagType(scope.row[c.key])" effect="light">
              {{ t(`lqg.qc.list.status.${scope.row[c.key] || 'none'}`) }}
            </el-tag>
            <span v-if="c.key === 'scoreStatus' && scope.row.totalScore != null" class="lqg-qclist__score">
              {{ t('lqg.qc.list.totalScore', { n: scope.row.totalScore }) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.qc.list.col.progress')" prop="progress" width="115" align="center">
          <template #default="scope">
            <span :class="['lqg-qclist__progress', `is-${scope.row.progress}`]">
              {{
                scope.row.progress === 'doing'
                  ? t('lqg.qc.list.doneCount', { n: scope.row.publishedCount })
                  : t(`lqg.qc.list.progress.${scope.row.progress}`)
              }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.qc.list.col.lastUpdate')" prop="lastUpdateTime" width="170" align="center">
          <!-- 只显示到分钟（秒没有意义，而且整串会被右侧固定列压住） -->
          <template #default="scope">{{ scope.row.lastUpdateTime ? scope.row.lastUpdateTime.slice(0, 16) : '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.qc.list.col.action')" width="80" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openQc(scope.row)">{{ t('lqg.qc.list.open') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup name="LqgQcList" lang="ts">
import { listQcDocs } from '@/api/lqg/qc';
import type { QcDocListQuery, QcDocListVO, QcDocStatus } from '@/api/lqg/qc';
import { useI18n } from 'vue-i18n';

// 「质控文档」板块（CR-20260930-11，飞书「网页工作台」第 17 行，甲方 2026-09-30 确认）：
// 三份质控表单独成一个板块 —— 一行一个已核验有效的样本，三列是三份表各自的状态，
// 可按关键字 / 样本类别 / 填写进度 / 收样日期筛选，点「进入」到质控文档编辑页。
// ★ 编辑页带 `from=qc-docs`：那边的「返回」据此回到本页，而不是样本表。

const { t } = useI18n();
const router = useRouter();

const PROGRESSES = ['none', 'doing', 'done'] as const;
const DOC_COLUMNS = [
  { key: 'sampleQcStatus', labelKey: 'lqg.qc.list.col.sampleQc' },
  { key: 'organoidQcStatus', labelKey: 'lqg.qc.list.col.organoidQc' },
  { key: 'scoreStatus', labelKey: 'lqg.qc.list.col.score' }
] as const;

const loading = ref(false);
const rows = ref<QcDocListVO[]>([]);
const total = ref(0);
const receiveRange = ref<[string, string] | null>(null);
const queryParams = reactive<QcDocListQuery>({
  pageNum: 1,
  pageSize: 20,
  keyword: undefined,
  sampleKind: undefined,
  progress: undefined
});

const statusTagType = (status: QcDocStatus) => (status === 'published' ? 'success' : status === 'draft' ? 'warning' : 'info');

const getList = async () => {
  loading.value = true;
  try {
    const [receiveBegin, receiveEnd] = receiveRange.value ?? [];
    const res: any = await listQcDocs({ ...queryParams, receiveBegin, receiveEnd });
    rows.value = (res.rows ?? []) as QcDocListVO[];
    total.value = res.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryParams.keyword = undefined;
  queryParams.sampleKind = undefined;
  queryParams.progress = undefined;
  receiveRange.value = null;
  handleQuery();
};

const openQc = (row: QcDocListVO) => {
  router.push({ path: '/qc-console/qc-editor', query: { sampleId: String(row.sampleId), from: 'qc-docs' } });
};

// 从编辑页「完成并同步」回来时状态要是新的：keep-alive 重新激活也拉一次。
// ★ 第一次挂载时 onMounted 与 onActivated 都会触发 —— 跳过那一次 onActivated，不发两遍请求。
let mounted = false;
onMounted(() => {
  getList();
  mounted = true;
});
onActivated(() => {
  if (mounted) {
    mounted = false;
    return;
  }
  getList();
});
</script>

<style scoped lang="scss">
.lqg-qclist {
  .lqg-qclist__head {
    display: flex;
    align-items: baseline;
    gap: 12px;
    flex-wrap: wrap;
  }
  .lqg-qclist__title {
    font-size: 16px;
    font-weight: 600;
    color: var(--lqg-ink);
  }
  .lqg-qclist__subtitle {
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
  .lqg-qclist__filter {
    margin-bottom: 16px;
  }
  .lqg-qclist__control {
    width: 100%;
  }
  .lqg-qclist__mono {
    font-family: var(--lqg-font-mono);
  }
  .lqg-qclist__score {
    margin-left: 6px;
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
  .lqg-qclist__progress {
    font-size: 13px;
    color: var(--lqg-ink-2);
    &.is-done {
      color: var(--lqg-ok, var(--el-color-success));
      font-weight: 600;
    }
    &.is-none {
      color: var(--lqg-ink-3);
    }
  }
}
</style>
