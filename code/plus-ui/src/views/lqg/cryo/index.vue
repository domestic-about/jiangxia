<template>
  <div class="p-2 lqg-cryo">
    <el-card shadow="hover">
      <template #header>
        <div class="lqg-cryo__head">
          <span class="lqg-cryo__title">{{ t('lqg.cryo.title') }}</span>
          <span class="lqg-cryo__subtitle">{{ t('lqg.cryo.subtitle') }}</span>
        </div>
      </template>

      <!-- ★ 带 sampleId 进来（样本表「石蜡包埋 / 冻存」一列点「冻存 N 批」，2026-09-24 本机验收）：
           顶部写明「只看××的冻存批次 · 共 N 批」；「看全部」只清这个样本筛选，「打开样本」回到它所在那一页。
           页签上的数字仍是整表口径（后端 tabCounts），提示条上的「共 N 批」才是这个样本的 -->
      <div v-if="queryParams.sampleId" class="lqg-cryo__scope">
        <i18n-t keypath="lqg.cryo.scope.only" tag="span" class="lqg-cryo__scope-text">
          <template #sample>
            <strong class="lqg-cryo__scope-sample">{{ scopeLabel }}</strong>
          </template>
        </i18n-t>
        <span class="lqg-cryo__scope-count">{{ t('lqg.cryo.scope.total', { n: total }) }}</span>
        <el-button link type="primary" @click="openScopeSample">{{ t('lqg.cryo.scope.openSample') }}</el-button>
        <el-button link type="primary" @click="clearSampleFilter">{{ t('lqg.cryo.scope.showAll') }}</el-button>
      </div>

      <!-- 顶部页签（UI:admin.cryo.list）：全部 / -80 超期 / 液氮 / 已取空（2026-09-24 甲方「支数取空的要提示」）。
           ★ 四个数字全部取后端响应里的 tabCounts（整表口径，翻页、换筛选都不变）；
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
        <el-tab-pane name="emptied">
          <template #label>
            {{ t('lqg.cryo.tab.emptied') }}
            <span class="lqg-cryo__count lqg-cryo__count--warn">{{ tabCounts.emptied }}</span>
          </template>
        </el-tab-pane>
      </el-tabs>

      <!-- 筛选区（UI:admin.cryo.list）：内部编号、冻存样品、位置、只看超期、冻存时间区间 -->
      <el-form ref="queryRef" :model="queryParams" label-width="86px" class="lqg-cryo__filter" @submit.prevent @keyup.enter="handleQuery">
        <!-- 筛选框里按回车 = 点「搜索」（工作台 UX 测试 WEB-23；与质控文档列表一致） -->
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
          <!-- 种属：所挂样本的（CR-20261009-18） -->
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.species.label')" prop="species">
              <SpeciesSelect v-model="queryParams.species" filter class="lqg-cryo__control" />
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
          <!-- 日期区间占两格（区间框最窄约 260px，一格只有约 170px，以前会盖住右边那一项的标签 / 按钮） -->
          <el-col :xs="24" :sm="24" :md="16" :lg="12">
            <el-form-item :label="t('lqg.cryo.filter.freezeTimeRange')">
              <el-date-picker
                v-model="freezeTimeRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                :start-placeholder="t('lqg.cryo.filter.freezeTimeBegin')"
                :end-placeholder="t('lqg.cryo.filter.freezeTimeEnd')"
                class="lqg-cryo__control lqg-range"
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
      </el-row>

      <!-- 宽表：模板 9 列（先后逐字照甲方 -80 冻存模板）+ 代数 / 当前剩余（与导出同序）
           + 内部编号 / 当前位置 / 最后修改 + 操作。
           超期行整行浅红 +「已超 N 天」徽标，并由后端置顶（前端不重排）；已取空的「当前剩余」旁标「已取空」。 -->
      <el-table
        v-loading="loading"
        :data="rows"
        border
        :row-class-name="rowClassName"
       
      >
        <template #empty><TableEmpty :error="loadError" :text="t('lqg.cryo.empty')" @retry="getList" /></template>
        <el-table-column :label="t('lqg.cryo.col.freezeTime')" prop="freezeTime" width="150" align="center" fixed="left">
          <template #default="scope">
            <div class="lqg-cryo__cell">
              <span>{{ scope.row.freezeTime || '—' }}</span>
              <span v-if="overdueBadge(scope.row)" class="lqg-cryo__badge">{{ overdueBadge(scope.row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.cryo.col.cryoName')" prop="cryoName" width="260" :show-overflow-tooltip="true" fixed="left">
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
        <el-table-column :label="t('lqg.cryo.col.passage')" prop="passage" width="80" align="center">
          <template #default="scope">{{ scope.row.passage || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.cryo.col.remainingQty')" prop="remainingQty" width="130" align="center">
          <template #default="scope">
            <div class="lqg-cryo__cell">
              <span :class="{ 'lqg-cryo__empty-qty': scope.row.emptied }">{{ scope.row.remainingQty ?? '—' }}</span>
              <!-- ★ 已取空（2026-09-24 甲方「支数取空的要提示」）：判据只认后端行上的 emptied，不自己拿剩余判 -->
              <span v-if="scope.row.emptied" class="lqg-cryo__badge lqg-cryo__badge--emptied">{{ t('lqg.cryo.badge.emptyQty') }}</span>
            </div>
          </template>
        </el-table-column>
        <!-- 追加列「种属」：所挂样本的种属，排在「当前剩余/支」之后（与导出同一位置，CR-20261009-18） -->
        <el-table-column :label="t('lqg.species.label')" prop="species" width="90" align="center" :show-overflow-tooltip="true">
          <template #default="scope">{{ speciesText(scope.row.species) }}</template>
        </el-table-column>
        <!-- 以下三列模板里没有，工作台自己要看：所挂样本的内部编号、当前位置、最后修改。
             ★ 内部编号点回样本（2026-09-24 本机验收「反向可回」）：按样本类别回到它所在那一页并打开它 -->
        <el-table-column :label="t('lqg.cryo.col.internalNo')" prop="internalNo" width="120" :show-overflow-tooltip="true">
          <template #default="scope">
            <el-link
              v-if="scope.row.sampleId && scope.row.internalNo"
              type="primary"
              underline="never"
              class="lqg-cryo__mono"
              :title="t('lqg.cryo.cell.openSample')"
              @click="openSample(scope.row)"
            >
              {{ scope.row.internalNo }}
            </el-link>
            <span v-else class="lqg-cryo__mono">—</span>
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

    <cryo-drawer ref="drawerRef" @saved="handleSaved" />
    <cryo-flow-drawer ref="flowDrawerRef" @changed="handleSaved" />
    <cryo-flow-dialog ref="flowDialogRef" @saved="handleSaved" />
    <cryo-to-ln2-dialog ref="toLn2Ref" @saved="handleSaved" />
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
import { useLqgTodoStore } from '@/store/modules/lqgTodo';
// ★ 四张表之间的来回（2026-09-24 本机验收）：路径、query 解析、提示条样本名都在 relation.ts
import { CRYO_PATH, flagOfQuery, queryWithout, routeKeyOf, sampleIdOfQuery, sampleOf, sampleScopeLabel } from '@/views/lqg/sample/relation';
import { useScopeSample } from '@/views/lqg/sample/useScopeSample';
import CryoDrawer from './CryoDrawer.vue';
import CryoFlowDialog from './CryoFlowDialog.vue';
import CryoFlowDrawer from './CryoFlowDrawer.vue';
import CryoToLn2Dialog from './CryoToLn2Dialog.vue';
import type { FlowKind } from './flow';
import { useI18n } from 'vue-i18n';
import TableEmpty from '@/components/lqg/TableEmpty/index.vue';
import SpeciesSelect from '@/components/lqg/SpeciesSelect/index.vue';
import { speciesText } from '@/components/lqg/SpeciesSelect/species';

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
const router = useRouter();
const todoStore = useLqgTodoStore();

const loading = ref(false);
const exporting = ref(false);
const rows = ref<CryoBatchVO[]>([]);
const total = ref(0);
/** ★ 页签数字的唯一来源：后端 tabCounts（初始给整表 0，拉到就覆盖） */
const tabCounts = reactive<{ all: number; overdue: number; ln2: number; emptied: number }>({ all: 0, overdue: 0, ln2: 0, emptied: 0 });
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
  species: null,
  cryoName: null,
  sampleId: null,
  location: null,
  overdueOnly: null,
  emptiedOnly: null,
  freezeTimeBegin: null,
  freezeTimeEnd: null
});

// ★ 页签 / 位置下拉 / 只看超期勾选**共用一个真相源**：queryParams.location、
//   queryParams.overdueOnly 与 queryParams.emptiedOnly。页签是派生值（computed），不是另一份状态 ——
//   两份状态迟早会不一致（点了「液氮」页签但下拉还显示「全部」）。
//   「已取空」与「-80 超期」互斥（取空的永不超期，叠加就是空集）。
const activeTab = computed<'all' | 'overdue' | 'ln2' | 'emptied'>({
  get: () => (queryParams.overdueOnly ? 'overdue' : queryParams.emptiedOnly ? 'emptied' : queryParams.location === LOCATION_LN2 ? 'ln2' : 'all'),
  set: (value) => {
    queryParams.overdueOnly = value === 'overdue' ? true : null;
    queryParams.emptiedOnly = value === 'emptied' ? true : null;
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
      // 超期与液氮、已取空都是互斥的两档（液氮批次、取空批次永不超期），别叠加成空集
      queryParams.location = null;
      queryParams.emptiedOnly = null;
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

// 最近一次取数失败了没有：失败时表格空白处说「没加载出来」而不是「没有数据」（工作台 UX 测试 WEB-06）
const loadError = ref(false);

const getList = async () => {
  loading.value = true;
  loadError.value = false;
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
    const counts = res.tabCounts as { all: number; overdue: number; ln2: number; emptied?: number } | undefined;
    if (counts) {
      tabCounts.all = counts.all ?? 0;
      tabCounts.overdue = counts.overdue ?? 0;
      tabCounts.ln2 = counts.ln2 ?? 0;
      tabCounts.emptied = counts.emptied ?? 0;
    }
  } catch {
    // 请求层已经弹过报错；这里把表格清空并标记失败
    loadError.value = true;
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

/** 页签切换已由 activeTab 的 setter 收窄参数并重新拉取（不再另加 watch） */
const handleQuery = () => {
  queryParams.pageNum = 1;
  getList();
};

/** 清空全部筛选（不发请求；「重置」与「按地址重新套筛选」共用） */
const clearFilters = () => {
  queryRef.value?.resetFields();
  freezeTimeRange.value = null;
  queryParams.internalNo = null;
  queryParams.species = null;
  queryParams.cryoName = null;
  queryParams.sampleId = null;
  queryParams.location = null;
  queryParams.overdueOnly = null;
  queryParams.emptiedOnly = null;
};

// ── 地址里带的筛选（样本表「石蜡包埋 / 冻存」一列、首页超期卡片、小程序同款直达） ─────────
//
// ★ 页面被 keep-alive 缓存（key 是 route.path，不含 query）：已经打开过本页，再从别的样本点「冻存 N 批」
//   进来，onMounted 不会再跑。所以看整条地址：地址里的筛选与上一次套上的不一样，才清掉旧筛选、
//   按地址重新套一遍并重拉；一样就不动（从标签页切回来保留页面上的手动筛选）。

/** 本页认的地址筛选；`add` 不在里面（它只是「到了就开新增抽屉」的一次性动作） */
const ROUTE_KEYS = ['sampleId', 'overdueOnly', 'emptiedOnly'];

/** 上一次按地址套上的筛选指纹；null = 还没套过 */
let appliedRouteKey: string | null = null;

/** 带 sampleId 进来时那条样本（提示条上的名字、「新增」预选、「打开样本」都用它） */
const { sample: scopeSample, load: loadScopeSample } = useScopeSample();

const scopeLabel = computed(() =>
  sampleScopeLabel(scopeSample.value, t('lqg.cryo.scope.sampleFallback', { id: queryParams.sampleId ?? '' }))
);

/** 按地址套筛选并重拉；地址里的筛选没变就什么都不做（返回 false） */
const applyRouteScope = async (): Promise<boolean> => {
  const key = routeKeyOf(route.query, ROUTE_KEYS);
  if (key === appliedRouteKey) {
    return false;
  }
  appliedRouteKey = key;
  clearFilters();
  // 从样本表「石蜡包埋 / 冻存」一列带 sampleId 进来 → 自动按样本过滤（只认纯数字）
  queryParams.sampleId = sampleIdOfQuery(route.query.sampleId);
  // 工作台首页「-80 超期批次」卡片带 ?overdueOnly=true 进来（SYS-HOME-001）→ 直接落在「超期」页签。
  // ★ 页签是高亮**派生**值（activeTab 由 overdueOnly 算出来），所以设这一个字段就够了。
  queryParams.overdueOnly = flagOfQuery(route.query.overdueOnly) ? true : null;
  // 与小程序表格页同一种直达写法：?emptiedOnly=true 落在「已取空」页签（与超期互斥，超期优先）
  queryParams.emptiedOnly = !queryParams.overdueOnly && flagOfQuery(route.query.emptiedOnly) ? true : null;
  queryParams.pageNum = 1;
  await Promise.all([getList(), loadScopeSample(queryParams.sampleId)]);
  return true;
};

/**
 * 样本表数量为 0 时的「新增」带 `?add=1` 进来：列表与样本都就绪后直接打开新增抽屉（样本已预选），
 * 然后把 add 从地址里拿掉（replace）—— 刷新 / 从标签页回来不会再弹一次。
 */
const openAddFromRoute = () => {
  if (route.path !== CRYO_PATH || route.query.add !== '1') {
    return;
  }
  router.replace({ path: CRYO_PATH, query: queryWithout(route.query, ['add']) });
  handleAdd();
};

/**
 * 页面上清掉了筛选之后，让地址跟上：把 `keys` 从地址里拿掉（replace），并把拿掉之后的地址记成
 * 「已套上」—— 否则 watch 看到地址变了，会把页面上别的手动筛选又清一遍。
 */
const syncRouteScope = (keys: string[]) => {
  if (route.path !== CRYO_PATH || !keys.some((key) => route.query[key] !== undefined)) {
    return;
  }
  const query = queryWithout(route.query, keys);
  appliedRouteKey = routeKeyOf(query, ROUTE_KEYS);
  router.replace({ path: CRYO_PATH, query });
};

const resetQuery = () => {
  clearFilters();
  syncRouteScope([...ROUTE_KEYS, 'add']);
  handleQuery();
};

/** 「看全部」：只清这个样本筛选（页签与别的筛选原样留着），地址里的 sampleId 一并拿掉 */
const clearSampleFilter = () => {
  queryParams.sampleId = null;
  syncRouteScope(['sampleId', 'add']);
  handleQuery();
};

/** 「打开样本」：回到这个样本所在那一页并打开它（push：后退能回到这里） */
const openScopeSample = () => {
  if (!queryParams.sampleId) {
    return;
  }
  router.push(sampleOf(scopeSample.value?.sampleKind, queryParams.sampleId));
};

/** 行上的「内部编号」点回样本（组织 → 样本记录信息表，类器官 → 类器官收样记录） */
const openSample = (row: CryoBatchVO) => {
  router.push(sampleOf(row.sampleKind, row.sampleId));
};

/**
 * 「新增」：只看某个样本时默认挂这个样本（抽屉里已选好，可改）。
 * ★ 只预选已核验有效的样本 —— 抽屉的样本下拉本来就只列有效样本。
 */
const handleAdd = () => {
  const preset = queryParams.sampleId && scopeSample.value?.verifyStatus === 'valid' ? scopeSample.value : null;
  drawerRef.value?.openAdd(preset);
};

/** 登记 / 取放 / 转液氮之后：重拉本页，并刷新首页卡片与菜单角标共用的超期数 */
const handleSaved = () => {
  getList();
  todoStore.refresh();
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
  todoStore.refresh();
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
  await applyRouteScope();
  openAddFromRoute();
});

// 已经打开过本页（keep-alive），再带着别的筛选进来 → 按新地址重新套（只认本页路径）
watch(
  () => route.fullPath,
  async () => {
    if (route.path !== CRYO_PATH) {
      return;
    }
    await applyRouteScope();
    openAddFromRoute();
  }
);
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
  .lqg-cryo__count--warn {
    color: var(--lqg-warn);
    background-color: var(--lqg-warn-soft);
  }
  .lqg-cryo__filter {
    margin-bottom: 16px; // 2026-09-28 飞书问题行：筛选与下方（工具栏/表格）多留 12px
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
  // 已取空：琥珀色（与超期的红区分开：超期是「该处理」，取空是「没了」）
  .lqg-cryo__badge--emptied {
    color: var(--lqg-warn);
    background-color: var(--lqg-warn-soft);
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
  // 「只看××的冻存批次」提示条（主色浅底 + 左边线；token，不写字面色值）
  .lqg-cryo__scope {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 4px 12px;
    margin-bottom: 8px;
    padding: 8px 12px;
    border-left: 3px solid var(--lqg-primary);
    border-radius: 4px;
    background: var(--lqg-primary-soft);
    color: var(--lqg-ink);
  }
  .lqg-cryo__scope-sample {
    margin: 0 4px;
    font-family: var(--lqg-font-mono);
    font-weight: 600;
  }
  .lqg-cryo__scope-count {
    color: var(--lqg-ink-2);
  }

  // 超期行整行浅红（UI:admin.cryo.list；token = --lqg-danger-soft）
  :deep(.lqg-cryo__row-overdue) {
    --el-table-tr-bg-color: var(--lqg-danger-soft);
    background-color: var(--lqg-danger-soft);
  }
}
</style>
