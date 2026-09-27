<template>
  <div class="p-2 lqg-sample">
    <el-card shadow="hover">
      <template #header>
        <div class="lqg-sample__head">
          <span class="lqg-sample__title">{{ t(page.titleKey) }}</span>
          <span class="lqg-sample__subtitle">{{ t(`lqg.sample.page.${kind}.subtitle`) }}</span>
        </div>
      </template>

      <!-- 筛选区（UI:admin.sample.list）：一行排开，供体姓名 / 住院号旁标「精确匹配」。
           ★ 没有「类别」一格：类别由页面钉死（样本记录信息表 = tissue，类器官收样记录 = organoid，CR-20260924-10） -->
      <el-form ref="queryRef" :model="queryParams" label-width="76px" class="lqg-sample__filter">
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.sample.filter.sourceUnit')" prop="sourceUnitId">
              <el-select
                v-model="queryParams.sourceUnitId"
                :placeholder="t('lqg.sample.filter.sourceUnitPlaceholder')"
                clearable
                filterable
                class="lqg-sample__control"
                @change="handleUnitChange"
              >
                <el-option v-for="unit in units" :key="String(unit.unitId)" :label="unit.unitName" :value="unit.unitId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.sample.filter.group')" prop="groupId">
              <el-select
                v-model="queryParams.groupId"
                :placeholder="queryParams.sourceUnitId ? t('lqg.sample.filter.groupPlaceholder') : t('lqg.sample.filter.groupPlaceholderNoUnit')"
                clearable
                filterable
                class="lqg-sample__control"
                :disabled="!queryParams.sourceUnitId"
              >
                <el-option v-for="group in groups" :key="String(group.groupId)" :label="group.groupName" :value="group.groupId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.sample.filter.submitSource')" prop="submitSource">
              <el-select v-model="queryParams.submitSource" clearable class="lqg-sample__control">
                <el-option v-for="d in lqg_submit_source" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.sample.filter.verifyStatus')" prop="verifyStatus">
              <el-select v-model="queryParams.verifyStatus" clearable class="lqg-sample__control">
                <el-option v-for="d in lqg_verify_status" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.sample.filter.receiveDateRange')">
              <el-date-picker
                v-model="receiveDateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                :start-placeholder="t('lqg.sample.filter.receiveDateBegin')"
                :end-placeholder="t('lqg.sample.filter.receiveDateEnd')"
                class="lqg-sample__control"
                clearable
              />
            </el-form-item>
          </el-col>
          <!-- 类型一格按页面：组织样本筛组织类型，类器官筛类器官类型（都是模糊） -->
          <el-col v-if="isTissue" :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.sample.filter.tissueType')" prop="tissueType">
              <el-input v-model="queryParams.tissueType" :placeholder="t('lqg.sample.filter.tissuePlaceholder')" clearable class="lqg-sample__control" />
            </el-form-item>
          </el-col>
          <el-col v-else :xs="24" :sm="12" :md="8" :lg="6">
            <!-- 「类器官类型」五个字，76px 放不下会折行 -->
            <el-form-item :label="t('lqg.sample.filter.organoidType')" prop="organoidType" label-width="90px">
              <el-input v-model="queryParams.organoidType" :placeholder="t('lqg.sample.filter.organoidPlaceholder')" clearable class="lqg-sample__control" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.sample.filter.internalNo')" prop="internalNo">
              <el-input v-model="queryParams.internalNo" :placeholder="t('lqg.sample.filter.internalNoPlaceholder')" clearable class="lqg-sample__control" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.sample.filter.operatorName')" prop="operatorName">
              <el-input v-model="queryParams.operatorName" :placeholder="t('lqg.sample.filter.operatorPlaceholder')" clearable class="lqg-sample__control" />
            </el-form-item>
          </el-col>
          <!-- 供体姓名 / 住院号只有组织样本有（类器官收样记录没有这两列） -->
          <el-col v-if="isTissue" :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item prop="donorName">
              <template #label>
                <!-- 独立验收（2026-09-28 飞书「小程序/工作台」问题行）：标签不再挂「精确匹配」后缀 —— 76px 的
                     label-width 放不下，会被迫折成两行。精确匹配的口径保留在输入框占位符里
                     （`donorPlaceholder` = 「供体姓名（精确匹配）」），信息不丢。 -->
                <span>{{ t('lqg.sample.filter.donorName') }}</span>
              </template>
              <el-input v-model="queryParams.donorName" :placeholder="t('lqg.sample.filter.donorPlaceholder')" clearable class="lqg-sample__control" />
            </el-form-item>
          </el-col>
          <el-col v-if="isTissue" :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item prop="hospitalNo">
              <template #label>
                <span>{{ t('lqg.sample.filter.hospitalNo') }}</span>
              </template>
              <el-input v-model="queryParams.hospitalNo" :placeholder="t('lqg.sample.filter.hospitalPlaceholder')" clearable class="lqg-sample__control" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item label=" ">
              <el-button type="primary" icon="Search" @click="handleQuery">{{ t('lqg.sample.search') }}</el-button>
              <el-button icon="Refresh" @click="resetQuery">{{ t('lqg.sample.reset') }}</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 工具栏：本页只新增、只导出本页这一类（导出沿用原端点，带当前筛选） -->
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button v-hasPermi="['lqg:sample:add']" type="primary" plain icon="Plus" @click="handleAdd">
            {{ isTissue ? t('lqg.sample.toolbar.addTissue') : t('lqg.sample.toolbar.addOrganoid') }}
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button v-hasPermi="['lqg:sample:export']" plain icon="Download" :loading="exporting" @click="handleExport">
            {{ isTissue ? t('lqg.sample.toolbar.exportTissue') : t('lqg.sample.toolbar.exportOrganoid') }}
          </el-button>
        </el-col>
      </el-row>

      <!-- 宽表（列 = pages.ts 的 sampleColumns：冻结列 + 前置管理列 + 模板列与插入列 + 后置管理列；待核验行浅黄底） -->
      <el-table
        v-loading="loading"
        :data="rows"
        border
        :row-class-name="rowClassName"
        :empty-text="t('lqg.sample.empty')"
      >
        <el-table-column
          v-for="column in columns"
          :key="column.key"
          :label="t(column.labelKey)"
          :prop="column.key"
          :width="column.width"
          :min-width="column.minWidth"
          :align="column.align"
          :show-overflow-tooltip="column.tooltip === true"
        >
          <template #default="scope">
            <span v-if="column.cell === 'mono'" class="lqg-sample__mono">{{ scope.row[column.key] || '—' }}</span>
            <span v-else-if="column.cell === 'flag'">{{ flagText(scope.row[column.key]) }}</span>
            <dict-tag v-else-if="column.cell === 'gender'" :options="lqg_gender" :value="scope.row[column.key]" />
            <dict-tag v-else-if="column.cell === 'submitSource'" :options="lqg_submit_source" :value="scope.row[column.key]" />
            <dict-tag v-else-if="column.cell === 'verifyStatus'" :options="lqg_verify_status" :value="scope.row[column.key]" />
            <!-- ★ 切片染色提示（SAMPLE-HINT-001 / UI:admin.sample.list.hint）：读时计算、不可编辑；
                 悬停再查石蜡块明细、点击带 sampleId 跳石蜡包埋页 —— 都在组件里。
                 块数在右侧「石蜡包埋 / 冻存」一列（2026-09-24 本机验收），这里只写已切片 / 未切片与染色 -->
            <HintBadges v-else-if="column.cell === 'hint'" :hint="scope.row.hint" :sample-id="scope.row.id" />
            <!-- ★ 最后修改：updateTime 为 null = 从没改过（SAMPLE-MP-001 的跨票行为变更），显式渲染 -->
            <template v-else-if="column.cell === 'updateTime'">
              <span v-if="neverModified(scope.row)" class="lqg-sample__muted">{{ t('lqg.sample.neverModified') }}</span>
              <span v-else>{{ scope.row.updateTime }}<span class="lqg-sample__muted"> · {{ scope.row.updateByName }}</span></span>
            </template>
            <span v-else>{{ scope.row[column.key] || '—' }}</span>
          </template>
        </el-table-column>
        <!-- ★ 石蜡包埋 / 冻存（Kevin 2026-09-24 本机验收「四种表之间的关系看着有点乱」）：
             一个样本名下可以有多个石蜡块、多个冻存批次。以前「操作」列里的「石蜡包埋」「冻存」两个按钮
             点过去是整张列表、看不出筛过也回不来；现在单独一列写「蜡块 N · 待核验 N · 冻存 N 批」，
             数字可点（带 sampleId 过去，那边顶部有「只看××」提示条、「新增」默认挂这个样本、行上的
             样本编号能点回来）。数量来自列表同一次请求（hint.blockCount + relation），不逐行请求。 -->
        <el-table-column :label="t('lqg.sample.col.relation')" width="150" fixed="right">
          <template #default="scope">
            <RelationLinks :row="scope.row" />
          </template>
        </el-table-column>
        <!-- 「操作」只放对这一条样本本身的动作：编辑（待核验 = 核验）、质控文档、删除 -->
        <el-table-column :label="t('lqg.sample.col.action')" width="210" align="center" fixed="right" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button
              v-hasPermi="[scope.row.verifyStatus === 'pending' ? 'lqg:sample:verify' : 'lqg:sample:edit']"
              link
              type="primary"
              :icon="scope.row.verifyStatus === 'pending' ? 'Stamp' : 'Edit'"
              @click="handleOpen(scope.row)"
            >
              {{ scope.row.verifyStatus === 'pending' ? t('lqg.sample.rowAction.verify') : t('lqg.sample.rowAction.edit') }}
            </el-button>
            <!-- ★ 质控文档入口（QC-WEB-001 点亮）：只对已核验有效的样本可点
                 （后端 GET /lqg/qc/{sampleId} 对非 valid 样本直接 400） -->
            <el-button
              v-hasPermi="['lqg:qc:query']"
              link
              type="primary"
              :disabled="scope.row.verifyStatus !== 'valid'"
              :title="scope.row.verifyStatus !== 'valid' ? t('lqg.sample.rowAction.qcDocInvalid') : ''"
              @click="handleQcDoc(scope.row)"
            >
              {{ t('lqg.sample.rowAction.qcDoc') }}
            </el-button>
            <el-button v-hasPermi="['lqg:sample:remove']" link type="danger" icon="Delete" @click="handleDelete(scope.row)"></el-button>
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

    <sample-drawer ref="drawerRef" :units="units" :kind="kind" @saved="handleSaved" />
  </div>
</template>

<script setup name="LqgSample" lang="ts">
// ============================================================================
// 工作台「样本记录信息表」（菜单 5210，/sample，component = lqg/sample/index）+ 两页共用的列表组件
// （UI:admin.sample.list）
//
// ★ CR-20260924-10（甲方 2026-09-24 第 25 行：组织样本与类器官样本应该是分开的表）：原「样本总表」
//   拆成两个菜单页 —— 本文件直接做路由页时 kind 缺省 = tissue（样本记录信息表）；
//   「类器官收样记录」（菜单 5220，/sample-organoid）是 organoid.vue，它只是 `<SampleIndex kind="organoid" />`。
//   列表只有这一份实现，类别由 `kind` 钉死：
//     · 列表与导出**显式带** sampleKind（后端不带 = 两类都查，不能靠那个默认）；重置筛选也不清它；
//     · 筛选里没有「类别」，表格里也没有「类别」列（一页只有一类，每行都一样）；
//     · 工具栏只有本类的「新增」与本类的「导出」（导出沿用 /lqg/sample/export/{tissue|organoid}）；
//     · 核验抽屉、编辑、待核验浅黄底照旧（SampleDrawer.vue）。
// ============================================================================
import { delSample, getSample, listSamples, neverModified } from '@/api/lqg/sample';
import type { SampleQuery, SampleVO } from '@/api/lqg/sample';
// ★ 两张导出（SAMPLE-EXPORT-001）：与列表同一组筛选参数，走 query 参数 POST，responseType=blob
import { exportOrganoidSamples, exportTissueSamples } from '@/api/lqg/sample/export';
import { listGroups, listUnits } from '@/api/lqg/auth/group';
import type { SourceUnitVO, UnitGroupVO } from '@/api/lqg/auth/group';
import { useLqgTodoStore } from '@/store/modules/lqgTodo';
import SampleDrawer from './SampleDrawer.vue';
// ★ 切片染色提示（SAMPLE-HINT-001）：徽标组 + 悬停明细 + 点击跳石蜡包埋页
import HintBadges from './HintBadges.vue';
// ★ 石蜡包埋 / 冻存一列（2026-09-24 本机验收）：数量 + 可点，去向全在 relation.ts
import RelationLinks from './RelationLinks.vue';
import { normalizeSampleKind, sampleColumns, samplePageOf } from './pages';
import { queryWithout, sampleIdOfQuery } from './relation';
import type { SampleKind } from './pages';
import { useI18n } from 'vue-i18n';

const props = withDefaults(
  defineProps<{
    /** 本页的样本类别（页面钉死，不给改）；直接做路由页（/sample）时是组织样本 */
    kind?: SampleKind;
  }>(),
  { kind: 'tissue' }
);

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { t } = useI18n();
const router = useRouter();
const route = useRoute();
const todoStore = useLqgTodoStore();

// 字典全部走 useDict（ticket §2.2）；文案走 lqg.sample.*
const { lqg_submit_source, lqg_verify_status, lqg_gender } = toRefs<any>(
  proxy?.useDict('lqg_submit_source', 'lqg_verify_status', 'lqg_gender')
);

const isTissue = computed(() => props.kind === 'tissue');
const page = computed(() => samplePageOf(props.kind));
const columns = computed(() => sampleColumns(props.kind));

const loading = ref(false);
const exporting = ref(false);
const rows = ref<SampleVO[]>([]);
const total = ref(0);
const units = ref<SourceUnitVO[]>([]);
const groups = ref<UnitGroupVO[]>([]);
const receiveDateRange = ref<[string, string] | null>(null);
const drawerRef = ref<InstanceType<typeof SampleDrawer>>();

const queryRef = ref<ElFormInstance>();
const queryParams = reactive<SampleQuery>({
  pageNum: 1,
  pageSize: 10,
  sourceUnitId: null,
  groupId: null,
  // ★ 类别钉死在本页（列表、导出都带；resetQuery 不清）
  sampleKind: props.kind,
  submitSource: null,
  verifyStatus: null,
  receiveDateBegin: null,
  receiveDateEnd: null,
  tissueType: null,
  organoidType: null,
  internalNo: null,
  operatorName: null,
  donorName: null,
  hospitalNo: null
});

const getList = async () => {
  loading.value = true;
  try {
    // 日期区间两端都含（后端 ge / le），区间清掉时把两个参数一起清掉
    queryParams.receiveDateBegin = receiveDateRange.value?.[0] ?? null;
    queryParams.receiveDateEnd = receiveDateRange.value?.[1] ?? null;
    queryParams.sampleKind = props.kind;
    const res = await listSamples(queryParams);
    rows.value = (res.rows ?? []) as SampleVO[];
    total.value = res.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const loadUnits = async () => {
  const res = await listUnits();
  units.value = res.data ?? [];
};

const handleUnitChange = async (unitId: string | number | null) => {
  queryParams.groupId = null;
  groups.value = [];
  if (unitId) {
    const res = await listGroups(unitId);
    groups.value = res.data ?? [];
  }
};

const handleQuery = () => {
  queryParams.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryRef.value?.resetFields();
  receiveDateRange.value = null;
  queryParams.sourceUnitId = null;
  queryParams.groupId = null;
  queryParams.submitSource = null;
  queryParams.verifyStatus = null;
  queryParams.tissueType = null;
  queryParams.organoidType = null;
  queryParams.internalNo = null;
  queryParams.operatorName = null;
  queryParams.donorName = null;
  queryParams.hospitalNo = null;
  groups.value = [];
  handleQuery();
};

const handleAdd = () => {
  drawerRef.value?.openAdd(props.kind);
};

const handleOpen = (row: SampleVO) => {
  drawerRef.value?.open(row);
};

/** 保存 / 核验之后：重拉本页，并刷新首页与菜单角标共用的那一份待办数（刚核验掉的一条要马上从角标里减掉） */
const handleSaved = () => {
  getList();
  todoStore.refresh();
};

/**
 * 导出用的查询条件：**与列表同一份筛选**（SAMPLE-EXPORT-001 ticket §2）。
 *
 * ★ 日期区间住在 `receiveDateRange` 这个本地 ref 里，导出前必须先落进 queryParams
 *   —— 否则「按收样日期区间筛出来再导出」会静默导成不带日期条件的全量。
 * ★ pageNum / pageSize 原样带着也无妨：后端导出走 `selectList`（不分页），那两个参数被忽略。
 */
const buildExportQuery = (): SampleQuery => {
  queryParams.receiveDateBegin = receiveDateRange.value?.[0] ?? null;
  queryParams.receiveDateEnd = receiveDateRange.value?.[1] ?? null;
  queryParams.sampleKind = props.kind;
  return queryParams;
};

/** 导出共用的下载动作（blob → a[download]；空文件给提示而不是下一个 0 字节的 xlsx） */
const downloadExport = async (
  fetcher: (query: SampleQuery) => Promise<any>,
  fileName: string,
  doneKey: string
) => {
  exporting.value = true;
  try {
    const res: any = await fetcher(buildExportQuery());
    const blob = new Blob([res?.data ?? res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    if (!blob.size) {
      proxy?.$modal.msgWarning(t('lqg.sample.toolbar.exportEmpty'));
      return;
    }
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName + '.xlsx';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    proxy?.$modal.msgSuccess(t(doneKey));
  } finally {
    exporting.value = false;
  }
};

/**
 * 导出本页这一类：「样本记录信息表」（tissue 14 列）/「类器官收样记录」（organoid 模板 7 列 + 代数）；
 * 类别由后端端点决定（页面上的 sampleKind 会被端点覆盖，两边一致）。
 */
const handleExport = () =>
  isTissue.value
    ? downloadExport(exportTissueSamples, t('lqg.sample.toolbar.exportTissueFile'), 'lqg.sample.toolbar.exportTissueDone')
    : downloadExport(exportOrganoidSamples, t('lqg.sample.toolbar.exportOrganoidFile'), 'lqg.sample.toolbar.exportOrganoidDone');

/**
 * 「质控文档」行操作：带 sampleId 跳到工作台质控文档编辑页（QC-WEB-001）。
 *
 * ★ 路径是隐藏菜单 5510 的**完整路由**：父目录 5500 的 path 'qc-console' 会做前缀
 *   （菜单 5510 自己的 path 仍是票面要求的 'qc-editor'）→ 实际路由 `/qc-console/qc-editor`。
 * ★ 只对已核验有效的样本可点：后端 `GET /lqg/qc/{sampleId}` 对非 valid 样本直接 400
 *   （待核验样本还没有内部编号，也没有可编辑的质控文档）。
 */
const handleQcDoc = (row: SampleVO) => {
  if (row.verifyStatus !== 'valid') {
    return;
  }
  router.push({ path: '/qc-console/qc-editor', query: { sampleId: String(row.id) } });
};

const handleDelete = async (row: SampleVO) => {
  await proxy?.$modal.confirm(t('lqg.sample.rowAction.deleteConfirm', { no: row.submitNo }));
  await delSample(row.id);
  proxy?.$modal.msgSuccess(t('lqg.sample.rowAction.deleted'));
  await getList();
  todoStore.refresh();
};

/** 待核验行浅黄底（token；不写字面色值） */
const rowClassName = ({ row }: { row: SampleVO }) => (row.verifyStatus === 'pending' ? 'lqg-sample__row-pending' : '');

const flagText = (value?: string | null) => {
  if (value === 'Y') return t('lqg.sample.flag.yes');
  if (value === 'N') return t('lqg.sample.flag.no');
  return '—';
};

/** 首页待办卡（待核验样本记录 / 待核验类器官收样）带 ?verifyStatus=pending 进来 → 自动套上筛选 */
const applyRouteQuery = () => {
  // ★ 只认已知的状态值，避免把任意 query 直接塞进查询参数。
  const verifyStatus = route.query.verifyStatus;
  if (typeof verifyStatus === 'string' && ['pending', 'valid', 'invalid'].includes(verifyStatus)) {
    queryParams.verifyStatus = verifyStatus;
  }
};

/**
 * 从石蜡包埋 / 冻存管理页一行的「样本编号」点回来（`?sampleId=`，2026-09-24 本机验收「反向可回」）：
 * 直接打开这条样本的抽屉（待核验的开核验抽屉），列表与筛选不动。
 *
 * ★ 打开后把 sampleId 从地址里拿掉（replace，不新增历史）：刷新 / 从标签页回来不会再弹一次抽屉，
 *   浏览器后退照样回到来的那一页。
 * ★ 类别不对（手敲的地址、或样本被改了类别）就换到它所在的那一页再打开，不在这页开别的类别的样本。
 */
const openFromRoute = async () => {
  const sampleId = sampleIdOfQuery(route.query.sampleId);
  if (!sampleId || route.path !== page.value.path) {
    return;
  }
  router.replace({ path: route.path, query: queryWithout(route.query, ['sampleId']) });
  let detail: SampleVO | null = null;
  try {
    const res = await getSample(sampleId);
    detail = (res.data ?? null) as SampleVO | null;
  } catch {
    detail = null;
  }
  if (!detail) {
    proxy?.$modal.msgWarning(t('lqg.sample.relation.sampleGone'));
    return;
  }
  if (normalizeSampleKind(detail.sampleKind) !== props.kind) {
    router.push({ path: samplePageOf(detail.sampleKind).path, query: { sampleId } });
    return;
  }
  drawerRef.value?.open(detail);
};

onMounted(async () => {
  applyRouteQuery();
  await loadUnits();
  await getList();
  await openFromRoute();
});

// ★ 页面被 keep-alive 缓存时（已经打开过这一页），再从首页待办卡带 ?verifyStatus=pending 点进来
//   onMounted 不会再跑 —— 路由 query 变了就重新套一次筛选并重拉。
watch(
  () => route.query.verifyStatus,
  (value, old) => {
    if (route.path !== page.value.path || value === old || value === undefined) {
      return;
    }
    applyRouteQuery();
    handleQuery();
  }
);

// ★ 同理：已经打开过这一页，再从石蜡包埋 / 冻存那边点「样本编号」回来（`?sampleId=`）→ 打开它的抽屉。
//   看的是整条地址：从 /embed?sampleId=X 回到 /sample?sampleId=X 时单看 query.sampleId 是不变的。
watch(
  () => route.fullPath,
  () => {
    openFromRoute();
  }
);
</script>

<style scoped lang="scss">
.lqg-sample {
  .lqg-sample__head {
    display: flex;
    align-items: baseline;
    gap: 12px;
    flex-wrap: wrap;
  }
  .lqg-sample__title {
    font-size: 16px;
    font-weight: 600;
    color: var(--lqg-ink);
  }
  .lqg-sample__subtitle {
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
  .lqg-sample__filter {
    margin-bottom: 16px; // 2026-09-28 飞书问题行：筛选与下方（工具栏/表格）多留 12px
  }
  .lqg-sample__control {
    width: 100%;
  }
  .lqg-sample__exact {
    margin-left: 4px;
    font-size: 11px;
    color: var(--lqg-ink-3);
  }
  .lqg-sample__mono {
    font-family: var(--lqg-font-mono);
  }
  .lqg-sample__muted {
    color: var(--lqg-ink-3);
  }

  // 待核验行浅黄底（UI:admin.sample.list；token = --lqg-warn-soft）
  :deep(.lqg-sample__row-pending) {
    --el-table-tr-bg-color: var(--lqg-warn-soft);
    background-color: var(--lqg-warn-soft);
  }
}
</style>
