<template>
  <div class="p-2 lqg-embed">
    <el-card shadow="hover">
      <template #header>
        <div class="lqg-embed__head">
          <span class="lqg-embed__title">{{ t('lqg.embed.title') }}</span>
          <span class="lqg-embed__subtitle">{{ t('lqg.embed.subtitle') }}</span>
        </div>
      </template>

      <!-- ★ 带 sampleId 进来（样本表「石蜡包埋 / 冻存」一列点数字，2026-09-24 本机验收）：
           顶部写明「只看××的石蜡包埋记录 · 共 N 条」，一眼看得出是被这个样本筛过的；
           「看全部」只清这个样本筛选，「打开样本」回到它所在那一页并打开它 -->
      <div v-if="queryParams.sampleId" class="lqg-embed__scope">
        <i18n-t keypath="lqg.embed.scope.only" tag="span" class="lqg-embed__scope-text">
          <template #sample>
            <strong class="lqg-embed__scope-sample">{{ scopeLabel }}</strong>
          </template>
        </i18n-t>
        <span class="lqg-embed__scope-count">{{ t('lqg.embed.scope.total', { n: total }) }}</span>
        <el-button link type="primary" @click="openScopeSample">{{ t('lqg.embed.scope.openSample') }}</el-button>
        <el-button link type="primary" @click="clearSampleFilter">{{ t('lqg.embed.scope.showAll') }}</el-button>
      </div>

      <!-- 筛选区（UI:admin.embed.list）：核验状态 / 来源 / 石蜡块编号 / 内部编号 / 染色 / 切片时间区间。
           ★ 核验状态、来源排最前（2026-09-24 本机验收「外部送来的记录好找」）：合作单位送来的在这里核验 -->
      <el-form ref="queryRef" :model="queryParams" label-width="86px" class="lqg-embed__filter">
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.embed.filter.verifyStatus')" prop="verifyStatus">
              <el-select v-model="queryParams.verifyStatus" clearable class="lqg-embed__control">
                <el-option v-for="d in lqg_verify_status" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <!-- 来源：值仍是 lqg_submit_source 的 external / internal，选项写成人话「合作单位 / 中心内部」 -->
            <el-form-item :label="t('lqg.embed.filter.submitSource')" prop="submitSource">
              <el-select v-model="queryParams.submitSource" clearable class="lqg-embed__control">
                <el-option :label="t('lqg.embed.filter.sourceExternal')" value="external" />
                <el-option :label="t('lqg.embed.filter.sourceInternal')" value="internal" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.embed.filter.paraffinBlockNo')" prop="paraffinBlockNo">
              <el-input
                v-model="queryParams.paraffinBlockNo"
                :placeholder="t('lqg.embed.filter.paraffinBlockNoPlaceholder')"
                clearable
                class="lqg-embed__control"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.embed.filter.internalNo')" prop="internalNo">
              <el-input
                v-model="queryParams.internalNo"
                :placeholder="t('lqg.embed.filter.internalNoPlaceholder')"
                clearable
                class="lqg-embed__control"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.embed.filter.stain')" prop="stain">
              <el-select v-model="queryParams.stain" clearable class="lqg-embed__control">
                <el-option v-for="d in lqg_stain_type" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.embed.filter.sectionTimeRange')">
              <el-date-picker
                v-model="sectionTimeRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                :start-placeholder="t('lqg.embed.filter.sectionTimeBegin')"
                :end-placeholder="t('lqg.embed.filter.sectionTimeEnd')"
                class="lqg-embed__control"
                clearable
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item label=" ">
              <el-button type="primary" icon="Search" @click="handleQuery">{{ t('lqg.embed.search') }}</el-button>
              <el-button icon="Refresh" @click="resetQuery">{{ t('lqg.embed.reset') }}</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 工具栏 -->
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button v-hasPermi="['lqg:embed:add']" type="primary" plain icon="Plus" @click="handleAdd">
            {{ t('lqg.embed.toolbar.add') }}
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button v-hasPermi="['lqg:embed:export']" plain icon="Download" :loading="exporting" @click="handleExport">
            {{ t('lqg.embed.toolbar.export') }}
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button plain icon="Refresh" @click="getList">{{ t('lqg.embed.toolbar.refresh') }}</el-button>
        </el-col>
        <!-- 一键「合作单位送来、待核验」= 来源 external + 核验状态 pending（再点一下取消）；
             数字与首页卡片、左侧菜单红点同一份（lqgTodo 的 pendingEmbeds） -->
        <el-col :span="1.5">
          <el-button type="warning" :plain="!partnerPendingOn" icon="Stamp" @click="togglePartnerPending">
            {{ t('lqg.embed.toolbar.partnerPending') }}
            <span v-if="pendingEmbeds > 0" class="lqg-embed__quick-count">{{ pendingEmbeds }}</span>
          </el-button>
        </el-col>
      </el-row>

      <!-- 宽表：模板 16 列 + 前面两个徽标列；待核验行浅黄底且置顶（后端排序） -->
      <el-table
        v-loading="loading"
        :data="rows"
        border
        :row-class-name="rowClassName"
        :empty-text="t('lqg.embed.empty')"
      >
        <el-table-column :label="t('lqg.embed.col.submitSource')" prop="submitSource" width="90" align="center">
          <template #default="scope">
            <dict-tag :options="lqg_submit_source" :value="scope.row.submitSource" />
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.embed.col.verifyStatus')" prop="verifyStatus" width="100" align="center">
          <template #default="scope">
            <dict-tag :options="lqg_verify_status" :value="scope.row.verifyStatus" />
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.embed.col.paraffinBlockNo')" prop="paraffinBlockNo" width="130" :show-overflow-tooltip="true">
          <template #default="scope">
            <span v-if="scope.row.paraffinBlockNo" class="lqg-embed__mono">{{ scope.row.paraffinBlockNo }}</span>
            <span v-else class="lqg-embed__pending">{{ t('lqg.embed.badge.pendingBlockNo') }}</span>
          </template>
        </el-table-column>
        <!-- ★ 样本编号点回样本（2026-09-24 本机验收「反向可回」）：按样本类别回到它所在那一页并打开它；
             待核验的样本还没有内部编号 → 显示送检单号（灰字），同样能点回去 -->
        <el-table-column :label="t('lqg.embed.col.internalNo')" prop="internalNo" width="130" :show-overflow-tooltip="true">
          <template #default="scope">
            <el-link
              v-if="scope.row.sampleId && (scope.row.internalNo || scope.row.submitNo)"
              type="primary"
              underline="never"
              :class="['lqg-embed__mono', { 'lqg-embed__submit-no': !scope.row.internalNo }]"
              :title="scope.row.internalNo ? t('lqg.embed.cell.openSample') : t('lqg.embed.cell.openSampleBySubmitNo')"
              @click="openSample(scope.row)"
            >
              {{ scope.row.internalNo || scope.row.submitNo }}
            </el-link>
            <span v-else class="lqg-embed__mono">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.embed.col.sampleType')" prop="sampleType" width="100" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.sampleType || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.embed.col.organoidSourceType')" prop="organoidSourceType" min-width="140" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.organoidSourceType || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.embed.col.tissueReceiveTime')" prop="tissueReceiveTime" width="125" align="center">
          <template #default="scope">{{ scope.row.tissueReceiveTime || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.embed.col.tissueProcessTime')" prop="tissueProcessTime" width="125" align="center">
          <template #default="scope">{{ scope.row.tissueProcessTime || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.embed.col.agaroseEmbedTime')" prop="agaroseEmbedTime" width="150" align="center">
          <template #default="scope">{{ scope.row.agaroseEmbedTime || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.embed.col.embedBy')" prop="embedBy" width="100" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.embedBy || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.embed.col.dehydrateTime')" prop="dehydrateTime" width="125" align="center">
          <template #default="scope">{{ scope.row.dehydrateTime || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.embed.col.agaroseSendTime')" prop="agaroseSendTime" width="150" align="center">
          <template #default="scope">{{ scope.row.agaroseSendTime || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.embed.col.paraffinEmbedTime')" prop="paraffinEmbedTime" width="135" align="center">
          <template #default="scope">{{ scope.row.paraffinEmbedTime || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.embed.col.sectionTime')" prop="sectionTime" width="125" align="center">
          <template #default="scope">
            <span v-if="scope.row.sectionTime">{{ scope.row.sectionTime }}</span>
            <span v-else class="lqg-embed__muted">{{ t('lqg.embed.rowAction.notSectioned') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.embed.col.stain')" width="180" :show-overflow-tooltip="true">
          <template #default="scope">{{ stainText(scope.row) }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.embed.col.markerExpression')" min-width="180" :show-overflow-tooltip="true">
          <template #default="scope">{{ markerText(scope.row) }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.embed.col.operatorName')" prop="operatorName" width="100" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.operatorName || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.embed.col.remark')" prop="remark" min-width="120" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.remark || '—' }}</template>
        </el-table-column>
        <!-- ★ 最后修改：updateTime 为 null = 从没改过（SAMPLE-MP-001 的跨票语义），显式渲染 -->
        <el-table-column :label="t('lqg.embed.col.updateTime')" prop="updateTime" width="170" :show-overflow-tooltip="true">
          <template #default="scope">
            <span v-if="neverModified(scope.row)" class="lqg-embed__muted">{{ t('lqg.embed.drawer.lastModifiedNever') }}</span>
            <span v-else>{{ scope.row.updateTime }}<span class="lqg-embed__muted"> · {{ scope.row.updateByName }}</span></span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.embed.col.action')" width="180" align="center" fixed="right" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button
              v-hasPermi="[isExternalPending(scope.row) ? 'lqg:embed:verify' : 'lqg:embed:edit']"
              link
              type="primary"
              :icon="isExternalPending(scope.row) ? 'Stamp' : 'Edit'"
              @click="handleOpen(scope.row)"
            >
              {{ isExternalPending(scope.row) ? t('lqg.embed.rowAction.verify') : t('lqg.embed.rowAction.edit') }}
            </el-button>
            <!-- 待核验 / 无效的送样不能走普通保存（后端 400），如实置灰并说明 -->
            <el-button v-if="!isExternalPending(scope.row)" link disabled :title="t('lqg.embed.rowAction.readonlyTip')">
              {{ t('lqg.embed.rowAction.verify') }}
            </el-button>
            <el-button v-hasPermi="['lqg:embed:remove']" link type="danger" icon="Delete" @click="handleDelete(scope.row)"></el-button>
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

    <embed-drawer ref="drawerRef" @saved="handleSaved" />
  </div>
</template>

<script setup name="LqgEmbed" lang="ts">
import { delEmbed, exportEmbeds, isEditable, isExternalPending, listEmbeds, neverModified } from '@/api/lqg/embed';
import type { EmbedQuery, EmbedVO } from '@/api/lqg/embed';
import { useLqgTodoStore } from '@/store/modules/lqgTodo';
// ★ 四张表之间的来回（2026-09-24 本机验收）：路径、query 解析、提示条样本名都在 relation.ts
import {
  EMBED_PATH,
  SUBMIT_SOURCES,
  VERIFY_STATUSES,
  oneOfQuery,
  queryWithout,
  routeKeyOf,
  sampleIdOfQuery,
  sampleOf,
  sampleScopeLabel
} from '@/views/lqg/sample/relation';
import { useScopeSample } from '@/views/lqg/sample/useScopeSample';
import EmbedDrawer from './EmbedDrawer.vue';
import { useI18n } from 'vue-i18n';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const todoStore = useLqgTodoStore();

// ★ useDict 的参数必须是库里真名（lqg_stain_type / lqg_verify_status / lqg_submit_source）——
//   写错一个名字，下拉永远「无数据」而页面不报错（SAMPLE-WEB-001 踩过）
const { lqg_stain_type, lqg_verify_status, lqg_submit_source, lqg_marker_expr } = toRefs<any>(
  proxy?.useDict('lqg_stain_type', 'lqg_verify_status', 'lqg_submit_source', 'lqg_marker_expr')
);

const loading = ref(false);
const exporting = ref(false);
const rows = ref<EmbedVO[]>([]);
const total = ref(0);
const sectionTimeRange = ref<[string, string] | null>(null);
const drawerRef = ref<InstanceType<typeof EmbedDrawer>>();

const queryRef = ref<ElFormInstance>();
const queryParams = reactive<EmbedQuery>({
  pageNum: 1,
  pageSize: 10,
  paraffinBlockNo: null,
  internalNo: null,
  sampleId: null,
  stain: null,
  sectionTimeBegin: null,
  sectionTimeEnd: null,
  verifyStatus: null,
  submitSource: null
});

const getList = async () => {
  loading.value = true;
  try {
    // 切片时间区间两端都含（后端 ge / le），区间清掉时两个参数一起清掉
    queryParams.sectionTimeBegin = sectionTimeRange.value?.[0] ?? null;
    queryParams.sectionTimeEnd = sectionTimeRange.value?.[1] ?? null;
    const res = await listEmbeds(queryParams);
    rows.value = (res.rows ?? []) as EmbedVO[];
    total.value = res.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.pageNum = 1;
  getList();
};

/** 清空全部筛选（不发请求；「重置」与「按地址重新套筛选」共用） */
const clearFilters = () => {
  queryRef.value?.resetFields();
  sectionTimeRange.value = null;
  queryParams.paraffinBlockNo = null;
  queryParams.internalNo = null;
  queryParams.stain = null;
  queryParams.verifyStatus = null;
  queryParams.submitSource = null;
  queryParams.sampleId = null;
};

// ── 地址里带的筛选（样本表「石蜡包埋 / 冻存」一列、首页待核验卡片） ────────────────
//
// ★ 页面被 keep-alive 缓存（key 是 route.path，不含 query）：已经打开过本页，再从别的样本点「蜡块 N」
//   进来，onMounted 不会再跑。所以看整条地址：地址里的筛选（sampleId / verifyStatus / submitSource）
//   与上一次套上的不一样，才清掉旧筛选、按地址重新套一遍并重拉；一样就不动（从标签页切回来保留手动筛选）。

/** 本页认的地址筛选；`add` 不在里面（它只是「到了就开新增抽屉」的一次性动作） */
const ROUTE_KEYS = ['sampleId', 'verifyStatus', 'submitSource'];

/** 上一次按地址套上的筛选指纹；null = 还没套过 */
let appliedRouteKey: string | null = null;

/** 带 sampleId 进来时那条样本（提示条上的名字、「新增」预选、「打开样本」都用它） */
const { sample: scopeSample, load: loadScopeSample } = useScopeSample();

const scopeLabel = computed(() =>
  sampleScopeLabel(scopeSample.value, t('lqg.embed.scope.sampleFallback', { id: queryParams.sampleId ?? '' }))
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
  // 工作台首页「待核验石蜡包埋送样」卡片带 ?verifyStatus=pending 进来（SYS-HOME-001）、
  // 样本表「待核验 N」带 sampleId + verifyStatus=pending 进来 → 自动套上筛选（只认字典里的值）
  queryParams.verifyStatus = oneOfQuery(route.query.verifyStatus, VERIFY_STATUSES);
  queryParams.submitSource = oneOfQuery(route.query.submitSource, SUBMIT_SOURCES);
  queryParams.pageNum = 1;
  await Promise.all([getList(), loadScopeSample(queryParams.sampleId)]);
  return true;
};

/**
 * 样本表数量为 0 时的「新增」带 `?add=1` 进来：列表与样本都就绪后直接打开新增抽屉（样本已预选），
 * 然后把 add 从地址里拿掉（replace）—— 刷新 / 从标签页回来不会再弹一次。
 */
const openAddFromRoute = () => {
  if (route.path !== EMBED_PATH || route.query.add !== '1') {
    return;
  }
  router.replace({ path: EMBED_PATH, query: queryWithout(route.query, ['add']) });
  handleAdd();
};

const resetQuery = () => {
  clearFilters();
  syncRouteScope(['sampleId', 'verifyStatus', 'submitSource', 'add']);
  handleQuery();
};

/**
 * 「看全部」：只清这个样本筛选，别的筛选原样留着；地址里的 sampleId 一并拿掉（replace）——
 * 刷新不会又筛回去，浏览器后退仍回到样本表。
 */
const clearSampleFilter = () => {
  queryParams.sampleId = null;
  syncRouteScope(['sampleId', 'add']);
  handleQuery();
};

/**
 * 页面上清掉了筛选之后，让地址跟上：把 `keys` 从地址里拿掉（replace），并把拿掉之后的地址记成
 * 「已套上」—— 否则 watch 看到地址变了，会把页面上别的手动筛选又清一遍。
 */
const syncRouteScope = (keys: string[]) => {
  if (route.path !== EMBED_PATH || !keys.some((key) => route.query[key] !== undefined)) {
    return;
  }
  const query = queryWithout(route.query, keys);
  appliedRouteKey = routeKeyOf(query, ROUTE_KEYS);
  router.replace({ path: EMBED_PATH, query });
};

/** 「打开样本」：回到这个样本所在那一页并打开它（push：后退能回到这里） */
const openScopeSample = () => {
  if (!queryParams.sampleId) {
    return;
  }
  router.push(sampleOf(scopeSample.value?.sampleKind, queryParams.sampleId));
};

/** 行上的「样本编号」点回样本（组织 → 样本记录信息表，类器官 → 类器官收样记录） */
const openSample = (row: EmbedVO) => {
  router.push(sampleOf(row.sampleKind, row.sampleId));
};

/**
 * 「新增」：只看某个样本时默认挂这个样本（抽屉里已选好，可改）。
 * ★ 只预选已核验有效的样本 —— 抽屉的样本下拉本来就只列有效样本，待核验 / 无效的样本不能挂石蜡块。
 */
const handleAdd = () => {
  const preset = queryParams.sampleId && scopeSample.value?.verifyStatus === 'valid' ? scopeSample.value : null;
  drawerRef.value?.openAdd(preset);
};

/** 保存 / 核验之后：重拉本页，并刷新首页卡片与菜单红点共用的待办数（刚核验掉的要马上减掉） */
const handleSaved = () => {
  getList();
  todoStore.refresh();
};

// ── 「合作单位送来、待核验」一键筛选 ─────────────────────────────────────────

const pendingEmbeds = computed(() => Number(todoStore.todo.pendingEmbeds) || 0);

const partnerPendingOn = computed(() => queryParams.submitSource === 'external' && queryParams.verifyStatus === 'pending');

const togglePartnerPending = () => {
  if (partnerPendingOn.value) {
    queryParams.submitSource = null;
    queryParams.verifyStatus = null;
  } else {
    queryParams.submitSource = 'external';
    queryParams.verifyStatus = 'pending';
  }
  handleQuery();
};

/** 行操作：待核验 / 无效的外部送样 = 核验抽屉；其余 = 编辑抽屉 */
const handleOpen = (row: EmbedVO) => {
  if (!isExternalPending(row) && !isEditable(row)) {
    proxy?.$modal.msgWarning(t('lqg.embed.rowAction.readonlyTip'));
    return;
  }
  drawerRef.value?.open(row);
};

const handleDelete = async (row: EmbedVO) => {
  await proxy?.$modal.confirm(t('lqg.embed.rowAction.deleteConfirm', { no: row.paraffinBlockNo || row.internalNo || row.id }));
  await delEmbed(row.id);
  proxy?.$modal.msgSuccess(t('lqg.embed.rowAction.deleted'));
  await getList();
  todoStore.refresh();
};

/** 按当前筛选导出（POST /lqg/embed/export，筛选走 query 参数） */
const handleExport = async () => {
  exporting.value = true;
  try {
    queryParams.sectionTimeBegin = sectionTimeRange.value?.[0] ?? null;
    queryParams.sectionTimeEnd = sectionTimeRange.value?.[1] ?? null;
    const res: any = await exportEmbeds(queryParams);
    const blob = new Blob([res.data ?? res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    if (!blob.size) {
      proxy?.$modal.msgWarning(t('lqg.embed.toolbar.exportEmpty'));
      return;
    }
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = t('lqg.embed.title') + '.xlsx';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    proxy?.$modal.msgSuccess(t('lqg.embed.toolbar.exportDone'));
  } finally {
    exporting.value = false;
  }
};

/** 待核验行浅黄底（token；不写字面色值） */
const rowClassName = ({ row }: { row: EmbedVO }) => (row.verifyStatus === 'pending' ? 'lqg-embed__row-pending' : '');

/** 染色一格：字典中文标签、顿号连接；「其他」带出具体名称（与导出口径一致） */
const stainText = (row: EmbedVO) => {
  const values = row.stainTypes ?? [];
  if (!values.length) {
    return t('lqg.embed.cell.noStain');
  }
  const labels = values.map((value: string) => {
    const found = (lqg_stain_type.value ?? []).find((d: any) => d.value === value);
    const label = found?.label ?? value;
    if (value === 'OTHER') {
      return row.stainOther ? t('lqg.embed.cell.stainOtherFull', { name: row.stainOther }) : label;
    }
    return label;
  });
  return labels.join('、');
};

/** 「mark的表达情况」一格：名称：表达，中文分号连接；没有名称的只写表达 */
const markerText = (row: EmbedVO) => {
  const markers = row.markers ?? [];
  if (!markers.length) {
    return '—';
  }
  const parts = markers
    .map((marker: any) => {
      const name = (marker.markerName ?? '').trim();
      const found = (lqg_marker_expr.value ?? []).find((d: any) => d.value === marker.expression);
      const expression = found?.label ?? marker.expression ?? '';
      if (!name) {
        return expression;
      }
      return expression ? name + '：' + expression : name;
    })
    .filter((part: string) => part);
  return parts.length ? parts.join('；') : '—';
};

onMounted(async () => {
  todoStore.ensureLoaded();
  await applyRouteScope();
  openAddFromRoute();
});

// 已经打开过本页（keep-alive），再带着别的筛选进来 → 按新地址重新套（只认本页路径）
watch(
  () => route.fullPath,
  async () => {
    if (route.path !== EMBED_PATH) {
      return;
    }
    await applyRouteScope();
    openAddFromRoute();
  }
);
</script>

<style scoped lang="scss">
.lqg-embed {
  .lqg-embed__head {
    display: flex;
    align-items: baseline;
    gap: 12px;
    flex-wrap: wrap;
  }
  .lqg-embed__title {
    font-size: 16px;
    font-weight: 600;
    color: var(--lqg-ink);
  }
  .lqg-embed__subtitle {
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
  .lqg-embed__filter {
    margin-bottom: 4px;
  }
  .lqg-embed__control {
    width: 100%;
  }
  .lqg-embed__mono {
    font-family: var(--lqg-font-mono);
  }
  .lqg-embed__muted {
    color: var(--lqg-ink-3);
  }
  .lqg-embed__pending {
    color: var(--lqg-warn);
    font-weight: 600;
  }
  // 「只看××的石蜡包埋记录」提示条（主色浅底 + 左边线；token，不写字面色值）
  .lqg-embed__scope {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 4px 12px;
    margin-bottom: 12px;
    padding: 8px 12px;
    border-left: 3px solid var(--lqg-primary);
    border-radius: 4px;
    background: var(--lqg-primary-soft);
    color: var(--lqg-ink);
  }
  .lqg-embed__scope-sample {
    margin: 0 4px;
    font-family: var(--lqg-font-mono);
    font-weight: 600;
  }
  .lqg-embed__scope-count {
    color: var(--lqg-ink-2);
  }
  .lqg-embed__submit-no {
    opacity: 0.75;
  }
  .lqg-embed__quick-count {
    margin-left: 4px;
    font-weight: 600;
  }

  // 待核验行浅黄底（UI:admin.embed.list；token = --lqg-warn-soft）
  :deep(.lqg-embed__row-pending) {
    --el-table-tr-bg-color: var(--lqg-warn-soft);
    background-color: var(--lqg-warn-soft);
  }
}
</style>
