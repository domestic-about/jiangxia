<template>
  <div class="p-2 lqg-cryo">
    <el-card shadow="hover">
      <template #header>
        <div class="lqg-cryo__head">
          <span class="lqg-cryo__title">{{ t('lqg.cryo.title') }}</span>
          <span class="lqg-cryo__subtitle">{{ t('lqg.cryo.subtitle') }}</span>
        </div>
      </template>

      <!-- 顶部页签（UI:admin.cryo.list）：全部 / -80 超期 / 液氮。
           ★ 三个数字全部取后端响应里的 tabCounts（整表口径，翻页、换筛选都不变）；
             对当前页 rows 自己数会在翻页时立刻错（accept 2 counterfeit 第一条）。 -->
      <el-tabs v-model="activeTab" class="lqg-cryo__tabs">
        <el-tab-pane name="all">
          <template #label>
            {{ t('lqg.cryo.tab.all') }}
            <span class="lqg-cryo__count">{{ tabCounts.all }}</span>
          </template>
        </el-tab-pane>
        <el-tab-pane name="overdue">
          <template #label>
            {{ t('lqg.cryo.tab.overdue') }}
            <span class="lqg-cryo__count lqg-cryo__count--danger">{{ tabCounts.overdue }}</span>
          </template>
        </el-tab-pane>
        <el-tab-pane name="ln2">
          <template #label>
            {{ t('lqg.cryo.tab.ln2') }}
            <span class="lqg-cryo__count">{{ tabCounts.ln2 }}</span>
          </template>
        </el-tab-pane>
      </el-tabs>

      <!-- 筛选区（UI:admin.cryo.list）：内部编号、冻存样品、位置、只看超期、冻存时间区间 -->
      <el-form ref="queryRef" :model="queryParams" label-width="86px" class="lqg-cryo__filter">
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.cryo.filter.internalNo')" prop="internalNo">
              <el-input
                v-model="queryParams.internalNo"
                :placeholder="t('lqg.cryo.filter.internalNoPlaceholder')"
                clearable
                class="lqg-cryo__control"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.cryo.filter.cryoName')" prop="cryoName">
              <el-input
                v-model="queryParams.cryoName"
                :placeholder="t('lqg.cryo.filter.cryoNamePlaceholder')"
                clearable
                class="lqg-cryo__control"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.cryo.filter.location')" prop="location">
              <el-select v-model="queryParams.location" clearable class="lqg-cryo__control" @change="onLocationChange">
                <el-option :label="t('lqg.cryo.filter.locationMinus80')" value="minus80" />
                <el-option :label="t('lqg.cryo.filter.locationLn2')" value="ln2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.cryo.filter.freezeTimeRange')">
              <el-date-picker
                v-model="freezeTimeRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                :start-placeholder="t('lqg.cryo.filter.freezeTimeBegin')"
                :end-placeholder="t('lqg.cryo.filter.freezeTimeEnd')"
                class="lqg-cryo__control"
                clearable
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item label=" ">
              <!-- 与「-80 超期」页签同一个参数（overdueOnly）：两处不能各留一份真相 -->
              <el-checkbox v-model="overdueOnlyModel">{{ t('lqg.cryo.filter.overdueOnly') }}</el-checkbox>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item label=" ">
              <el-button type="primary" icon="Search" @click="handleQuery">{{ t('lqg.cryo.search') }}</el-button>
              <el-button icon="Refresh" @click="resetQuery">{{ t('lqg.cryo.reset') }}</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 工具栏 -->
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button v-hasPermi="['lqg:cryo:add']" type="primary" plain icon="Plus" @click="handleAdd">
            {{ t('lqg.cryo.toolbar.add') }}
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button v-hasPermi="['lqg:cryo:export']" plain icon="Download" :loading="exporting" @click="handleExport">
            {{ t('lqg.cryo.toolbar.export') }}
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button plain icon="Refresh" @click="getList">{{ t('lqg.cryo.toolbar.refresh') }}</el-button>
        </el-col>
        <!-- 从样本总表带 sampleId 跳进来的提示 + 一键清除 -->
        <el-col v-if="queryParams.sampleId" :span="6">
          <el-tag type="info" closable class="lqg-cryo__sample-tag" @close="clearSampleFilter">
            {{ t('lqg.cryo.filter.sampleFilter', { id: queryParams.sampleId }) }}
          </el-tag>
        </el-col>
      </el-row>

      <!-- 宽表：模板 9 列 + 内部编号 / 代数 / 当前剩余 / 当前位置 + 最后修改 + 操作。
           超期行整行浅红 +「已超 N 天」徽标，并由后端置顶（前端不重排）。 -->
      <el-table
        v-loading="loading"
        :data="rows"
        border
        :row-class-name="rowClassName"
        :empty-text="t('lqg.cryo.empty')"
      >
        <el-table-column :label="t('lqg.cryo.col.freezeTime')" prop="freezeTime" width="150" align="center" fixed="left">
          <template #default="scope">
            <div class="lqg-cryo__cell">
              <span>{{ scope.row.freezeTime || '—' }}</span>
              <span v-if="overdueBadge(scope.row)" class="lqg-cryo__badge">{{ overdueBadge(scope.row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.cryo.col.cryoName')" prop="cryoName" min-width="210" :show-overflow-tooltip="true">
          <template #default="scope">
            <span class="lqg-cryo__mono">{{ scope.row.cryoName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.cryo.col.initQty')" prop="initQty" width="110" align="center">
          <template #default="scope">{{ scope.row.initQty ?? '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.cryo.col.density')" prop="density" width="100" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.density || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.cryo.col.inMinus80')" prop="inMinus80" width="160" align="center">
          <template #default="scope">{{ flagText(scope.row.inMinus80) }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.cryo.col.frozenBy')" prop="frozenBy" width="100" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.frozenBy || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.cryo.col.toLn2Time')" prop="toLn2Time" width="180" align="center">
          <template #default="scope">
            <span v-if="scope.row.toLn2Time">{{ scope.row.toLn2Time }}</span>
            <!-- 还在 -80 的批次：就地给一个「转液氮」入口（行操作之一，UI:admin.cryo.list） -->
            <el-button v-else v-hasPermi="['lqg:cryo:edit']" link type="primary" @click="openToLn2(scope.row)">
              {{ t('lqg.cryo.rowAction.toLn2') }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.cryo.col.ln2Location')" prop="ln2Location" width="150" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.ln2Location || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.cryo.col.remark')" prop="remark" min-width="140" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.cryo.col.internalNo')" prop="internalNo" width="120" :show-overflow-tooltip="true">
          <template #default="scope">
            <span class="lqg-cryo__mono">{{ scope.row.internalNo || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.cryo.col.passage')" prop="passage" width="80" align="center">
          <template #default="scope">{{ scope.row.passage || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.cryo.col.remainingQty')" prop="remainingQty" width="120" align="center">
          <template #default="scope">
            <span :class="{ 'lqg-cryo__empty-qty': (scope.row.remainingQty ?? 0) <= 0 }">
              {{ scope.row.remainingQty ?? '—' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.cryo.col.location')" prop="location" width="110" align="center">
          <template #default="scope">{{ t('lqg.cryo.location.' + locationKey(scope.row.location)) }}</template>
        </el-table-column>
        <!-- ★ 最后修改：updateTime 为 null = 从没改过（跨票语义），显式渲染 -->
        <el-table-column :label="t('lqg.cryo.col.updateTime')" prop="updateTime" width="180" :show-overflow-tooltip="true">
          <template #default="scope">
            <span v-if="neverModified(scope.row)" class="lqg-cryo__muted">{{ t('lqg.cryo.drawer.lastModifiedNever') }}</span>
            <span v-else>{{ scope.row.updateTime }}<span class="lqg-cryo__muted"> · {{ scope.row.updateByName || '—' }}</span></span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.cryo.col.action')" width="260" align="center" fixed="right" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-hasPermi="['lqg:cryo:flow']" link type="primary" @click="openFlowDialog('take', scope.row)">
              {{ t('lqg.cryo.rowAction.take') }}
            </el-button>
            <el-button v-hasPermi="['lqg:cryo:flow']" link type="primary" @click="openFlowDialog('add', scope.row)">
              {{ t('lqg.cryo.rowAction.add') }}
            </el-button>
            <el-button v-hasPermi="['lqg:cryo:flow']" link type="primary" @click="openFlowDialog('adjust', scope.row)">
              {{ t('lqg.cryo.rowAction.adjust') }}
            </el-button>
            <el-button v-hasPermi="['lqg:cryo:query']" link type="info" @click="openFlows(scope.row)">
              {{ t('lqg.cryo.rowAction.flow') }}
            </el-button>
            <el-button v-hasPermi="['lqg:cryo:edit']" link type="primary" icon="Edit" @click="handleEdit(scope.row)">
              {{ t('lqg.cryo.rowAction.edit') }}
            </el-button>
            <el-button v-hasPermi="['lqg:cryo:remove']" link type="danger" icon="Delete" @click="handleDelete(scope.row)"></el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </el-card>

    <cryo-drawer ref="drawerRef" @saved="getList" />
    <cryo-flow-drawer ref="flowDrawerRef" @changed="getList" />
    <cryo-flow-dialog ref="flowDialogRef" @saved="getList" />
    <cryo-to-ln2-dialog ref="toLn2Ref" @saved="getList" />
  </div>
</template>

<script setup name="LqgCryo" lang="ts">
import {
  LOCATION_LN2,
  delBatch,
  exportBatches,
  listBatches,
  locationKey,
  neverModified,
  overdueDaysText
} from '@/api/lqg/cryo';
import type { CryoBatchVO, CryoQuery } from '@/api/lqg/cryo';
import CryoDrawer from './CryoDrawer.vue';
import CryoFlowDialog from './CryoFlowDialog.vue';
import CryoFlowDrawer from './CryoFlowDrawer.vue';
import CryoToLn2Dialog from './CryoToLn2Dialog.vue';
import type { FlowKind } from './flow';
import { useI18n } from 'vue-i18n';

/**
 * 工作台「-80 冻存管理」（UI:admin.cryo.list）。
 *
 * ★ 页签数字取后端响应顶层的 **tabCounts**（整表口径）：`all / overdue / ln2`。
 *   它是 CRYO-REMIND-001 用**唯一的超期判定函数**算的，与超期清单、工作台首页待办、
 *   小程序表格页页签同一处；前端**不许**对当前页 rows 自己数（一翻页就错）。
 * ★ 超期行由后端置顶（默认排序 `ORDER BY CASE WHEN 超期 …`），前端只负责**整行浅红 +
 *   「已超 N 天」徽标**。`overdueDays` 未超期时是 null —— 所以判据是 `overdue`，
 *   不是 `overdueDays >= 0`（否则每行都会标「已超 0 天」）。
 * ★ 剩余支数（remainingQty）是**读时算**的：取走 / 补入 / 调整 / 改删登记之后
 *   重新拉一次列表就当场刷新，没有任何前端累加。
 */
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { t } = useI18n();
const route = useRoute();

const loading = ref(false);
const exporting = ref(false);
const rows = ref<CryoBatchVO[]>([]);
const total = ref(0);
/** ★ 页签数字的唯一来源：后端 tabCounts（初始给整表 0，拉到就覆盖） */
const tabCounts = reactive<{ all: number; overdue: number; ln2: number }>({ all: 0, overdue: 0, ln2: 0 });
const freezeTimeRange = ref<[string, string] | null>(null);

const drawerRef = ref<InstanceType<typeof CryoDrawer>>();
const flowDrawerRef = ref<InstanceType<typeof CryoFlowDrawer>>();
const flowDialogRef = ref<InstanceType<typeof CryoFlowDialog>>();
const toLn2Ref = ref<InstanceType<typeof CryoToLn2Dialog>>();

const queryRef = ref<ElFormInstance>();
const queryParams = reactive<CryoQuery>({
  pageNum: 1,
  pageSize: 10,
  internalNo: null,
  cryoName: null,
  sampleId: null,
  location: null,
  overdueOnly: null,
  freezeTimeBegin: null,
  freezeTimeEnd: null
});

// ★ 页签 / 位置下拉 / 只看超期勾选**共用一个真相源**：queryParams.location 与
//   queryParams.overdueOnly。页签是派生值（computed），不是另一份状态 ——
//   两份状态迟早会不一致（点了「液氮」页签但下拉还显示「全部」）。
const activeTab = computed<'all' | 'overdue' | 'ln2'>({
  get: () => (queryParams.overdueOnly ? 'overdue' : queryParams.location === LOCATION_LN2 ? 'ln2' : 'all'),
  set: (value) => {
    queryParams.overdueOnly = value === 'overdue' ? true : null;
    queryParams.location = value === 'ln2' ? LOCATION_LN2 : null;
    handleQuery();
  }
});

/** 「只看超期」勾选框：与「-80 超期」页签同一个参数 */
const overdueOnlyModel = computed({
  get: () => queryParams.overdueOnly === true,
  set: (value: boolean) => {
    queryParams.overdueOnly = value ? true : null;
    if (value) {
      // 超期与液氮是互斥的两档（液氮批次永不超期），别叠加成空集
      queryParams.location = null;
    }
    handleQuery();
  }
});

/** 位置下拉：选了液氮 / -80 就把「只看超期」关掉，页签随之切换（同一个真相源） */
const onLocationChange = (value: string | null) => {
  if (value) {
    queryParams.overdueOnly = null;
  }
  handleQuery();
};

const getList = async () => {
  loading.value = true;
  try {
    queryParams.freezeTimeBegin = freezeTimeRange.value?.[0] ?? null;
    queryParams.freezeTimeEnd = freezeTimeRange.value?.[1] ?? null;
    // ★ res 的静态类型是 AxiosResponse<CryoBatchPage>（src/types/axios.d.ts 只补了
    //   code/msg/rows/total 四个键），所以这里按响应体读、再逐键收窄 —— 不去动那个
    //   全仓共用的类型声明文件（改它会牵动所有页面）。
    const res: any = await listBatches(queryParams);
    rows.value = (res.rows ?? []) as CryoBatchVO[];
    total.value = (res.total ?? 0) as number;
    // ★ 页签数字来自后端（整表口径）；缺键时保持上一次的值，不用 rows 长度顶替
    const counts = res.tabCounts as { all: number; overdue: number; ln2: number } | undefined;
    if (counts) {
      tabCounts.all = counts.all ?? 0;
      tabCounts.overdue = counts.overdue ?? 0;
      tabCounts.ln2 = counts.ln2 ?? 0;
    }
  } finally {
    loading.value = false;
  }
};

/** 页签切换已由 activeTab 的 setter 收窄参数并重新拉取（不再另加 watch） */
const handleQuery = () => {
  queryParams.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryRef.value?.resetFields();
  freezeTimeRange.value = null;
  queryParams.internalNo = null;
  queryParams.cryoName = null;
  queryParams.sampleId = null;
  queryParams.location = null;
  queryParams.overdueOnly = null;
  handleQuery();
};

const clearSampleFilter = () => {
  queryParams.sampleId = null;
  handleQuery();
};

const handleAdd = () => {
  drawerRef.value?.openAdd();
};

const handleEdit = (row: CryoBatchVO) => {
  drawerRef.value?.open(row);
};

const openFlowDialog = (kind: FlowKind, row: CryoBatchVO) => {
  flowDialogRef.value?.openCreate(kind, row);
};

const openFlows = (row: CryoBatchVO) => {
  flowDrawerRef.value?.open(row);
};

const openToLn2 = (row: CryoBatchVO) => {
  toLn2Ref.value?.open(row);
};

const handleDelete = async (row: CryoBatchVO) => {
  await proxy?.$modal.confirm(t('lqg.cryo.rowAction.deleteConfirm', { name: row.cryoName || row.id }));
  try {
    await delBatch(row.id);
    proxy?.$modal.msgSuccess(t('lqg.cryo.rowAction.deleted'));
  } catch (e) {
    proxy?.$modal.msgError(e instanceof Error && e.message && e.message !== 'error' ? e.message : t('lqg.cryo.msg.saveFailed'));
  }
  await getList();
};

/** 按当前筛选导出（POST /lqg/cryo/batch/export；表头 = 模板 9 列 + 代数 + 当前剩余/支） */
const handleExport = async () => {
  exporting.value = true;
  try {
    queryParams.freezeTimeBegin = freezeTimeRange.value?.[0] ?? null;
    queryParams.freezeTimeEnd = freezeTimeRange.value?.[1] ?? null;
    const res: any = await exportBatches(queryParams);
    const blob = new Blob([res.data ?? res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    if (!blob.size) {
      proxy?.$modal.msgWarning(t('lqg.cryo.toolbar.exportEmpty'));
      return;
    }
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = t('lqg.cryo.title') + '.xlsx';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    proxy?.$modal.msgSuccess(t('lqg.cryo.toolbar.exportDone'));
  } finally {
    exporting.value = false;
  }
};

/** 超期行整行浅红（token = --lqg-danger-soft；不写字面色值） */
const rowClassName = ({ row }: { row: CryoBatchVO }) => (row.overdue ? 'lqg-cryo__row-overdue' : '');

/** 暂存 -80 的 Y / N → 是 / 否（模板那一格写的就是「是 否」） */
const flagText = (value?: string | null) => {
  if (value === 'Y') return t('lqg.cryo.flag.yes');
  if (value === 'N') return t('lqg.cryo.flag.no');
  return '—';
};

/**
 * 「已超 N 天」徽标的文案；**没超期返回 null**（不渲染徽标）。
 *
 * ★ 判据是 `overdue` 布尔、不是 `overdueDays >= 0`：未超期时后端给的是 null，
 *   用后者会给每一行都标上「已超 0 天」（阈值当天才是 0）。
 */
const overdueBadge = (row: CryoBatchVO) => {
  const days = overdueDaysText(row);
  if (days === null) {
    return null;
  }
  return days === '0' ? t('lqg.cryo.badge.overdueToday') : t('lqg.cryo.badge.overdue', { days });
};

onMounted(async () => {
  // 从样本总表带 sampleId 跳入（样本总表「冻存」行操作）→ 自动按样本过滤
  const sampleId = route.query.sampleId;
  if (sampleId) {
    queryParams.sampleId = String(sampleId);
  }
  await getList();
});
</script>

<style scoped lang="scss">
.lqg-cryo {
  .lqg-cryo__head {
    display: flex;
    align-items: baseline;
    gap: 12px;
    flex-wrap: wrap;
  }
  .lqg-cryo__title {
    font-size: 16px;
    font-weight: 600;
    color: var(--lqg-ink);
  }
  .lqg-cryo__subtitle {
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
  .lqg-cryo__tabs {
    margin-bottom: 4px;
  }
  .lqg-cryo__count {
    display: inline-block;
    min-width: 20px;
    margin-left: 4px;
    padding: 0 6px;
    font-size: 12px;
    line-height: 18px;
    text-align: center;
    color: var(--lqg-ink-2);
    background-color: var(--lqg-bg);
    border-radius: 9px;
  }
  .lqg-cryo__count--danger {
    color: var(--lqg-danger);
    background-color: var(--lqg-danger-soft);
  }
  .lqg-cryo__filter {
    margin-bottom: 4px;
  }
  .lqg-cryo__control {
    width: 100%;
  }
  .lqg-cryo__cell {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
  }
  .lqg-cryo__badge {
    padding: 1px 8px;
    font-size: 12px;
    font-weight: 600;
    color: var(--lqg-danger);
    background-color: var(--lqg-danger-soft);
    border-radius: 10px;
  }
  .lqg-cryo__mono {
    font-family: var(--lqg-font-mono);
  }
  .lqg-cryo__muted {
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
  .lqg-cryo__empty-qty {
    font-weight: 600;
    color: var(--lqg-ink-3);
  }
  .lqg-cryo__sample-tag {
    margin-top: 4px;
  }

  // 超期行整行浅红（UI:admin.cryo.list；token = --lqg-danger-soft）
  :deep(.lqg-cryo__row-overdue) {
    --el-table-tr-bg-color: var(--lqg-danger-soft);
    background-color: var(--lqg-danger-soft);
  }
}
</style>
