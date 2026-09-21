<template>
  <div class="p-2 lqg-sample">
    <el-card shadow="hover">
      <template #header>
        <div class="lqg-sample__head">
          <span class="lqg-sample__title">{{ t('lqg.sample.title') }}</span>
          <span class="lqg-sample__subtitle">{{ t('lqg.sample.subtitle') }}</span>
        </div>
      </template>

      <!-- 筛选区（UI:admin.sample.list）：一行排开，供体姓名 / 住院号旁标「精确匹配」 -->
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
            <el-form-item :label="t('lqg.sample.filter.sampleKind')" prop="sampleKind">
              <el-select v-model="queryParams.sampleKind" clearable class="lqg-sample__control">
                <el-option v-for="d in sample_kind" :key="d.value" :label="d.label" :value="d.value" />
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
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.sample.filter.tissueType')" prop="tissueType">
              <el-input v-model="queryParams.tissueType" :placeholder="t('lqg.sample.filter.tissuePlaceholder')" clearable class="lqg-sample__control" />
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
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item prop="donorName">
              <template #label>
                <span>{{ t('lqg.sample.filter.donorName') }}</span>
                <span class="lqg-sample__exact">{{ t('lqg.sample.filter.exactMatch') }}</span>
              </template>
              <el-input v-model="queryParams.donorName" :placeholder="t('lqg.sample.filter.donorPlaceholder')" clearable class="lqg-sample__control" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item prop="hospitalNo">
              <template #label>
                <span>{{ t('lqg.sample.filter.hospitalNo') }}</span>
                <span class="lqg-sample__exact">{{ t('lqg.sample.filter.exactMatch') }}</span>
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

      <!-- 工具栏 -->
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-dropdown v-hasPermi="['lqg:sample:add']" @command="handleAdd">
            <el-button type="primary" plain icon="Plus">{{ t('lqg.sample.toolbar.addTissue') }}<el-icon class="el-icon--right"><arrow-down /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="tissue">{{ t('lqg.sample.toolbar.addTissue') }}</el-dropdown-item>
                <el-dropdown-item command="organoid">{{ t('lqg.sample.toolbar.addOrganoid') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-col>
        <el-col :span="1.5">
          <el-button plain icon="Download" @click="handleExportNotYet('tissue')">{{ t('lqg.sample.toolbar.exportTissue') }}</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button plain icon="Download" @click="handleExportNotYet('organoid')">{{ t('lqg.sample.toolbar.exportOrganoid') }}</el-button>
        </el-col>
      </el-row>

      <!-- 总表（宽表；待核验行浅黄底） -->
      <el-table
        v-loading="loading"
        :data="rows"
        border
        :row-class-name="rowClassName"
        :empty-text="t('lqg.sample.empty')"
      >
        <el-table-column :label="t('lqg.sample.col.internalNo')" prop="internalNo" width="120" :show-overflow-tooltip="true">
          <template #default="scope">
            <span class="lqg-sample__mono">{{ scope.row.internalNo || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.sample.col.submitNo')" prop="submitNo" width="140" :show-overflow-tooltip="true">
          <template #default="scope">
            <span class="lqg-sample__mono">{{ scope.row.submitNo }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.sample.col.sourceUnit')" prop="sourceUnitName" min-width="130" :show-overflow-tooltip="true" />
        <el-table-column :label="t('lqg.sample.col.sampleKind')" prop="sampleKind" width="100" align="center">
          <template #default="scope">
            <dict-tag :options="sample_kind" :value="scope.row.sampleKind" />
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.sample.col.submitSource')" prop="submitSource" width="90" align="center">
          <template #default="scope">
            <dict-tag :options="lqg_submit_source" :value="scope.row.submitSource" />
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.sample.col.verifyStatus')" prop="verifyStatus" width="100" align="center">
          <template #default="scope">
            <dict-tag :options="lqg_verify_status" :value="scope.row.verifyStatus" />
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.sample.col.donorName')" prop="donorName" width="110" :show-overflow-tooltip="true" />
        <el-table-column :label="t('lqg.sample.col.gender')" prop="gender" width="80" align="center">
          <template #default="scope">
            <dict-tag :options="lqg_gender" :value="scope.row.gender" />
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.sample.col.age')" prop="age" width="80" align="center" />
        <el-table-column :label="t('lqg.sample.col.hospitalNo')" prop="hospitalNo" width="140" :show-overflow-tooltip="true" />
        <el-table-column :label="t('lqg.sample.col.tissueType')" width="150" :show-overflow-tooltip="true">
          <template #default="scope">
            <span>{{ kindText(scope.row) || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.sample.col.receiveDate')" prop="receiveDate" width="115" align="center">
          <template #default="scope">{{ scope.row.receiveDate || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.sample.col.isFixed')" prop="isFixed" width="100" align="center">
          <template #default="scope">{{ flagText(scope.row.isFixed) }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.sample.col.processTime')" prop="processTime" width="165" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.processTime || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.sample.col.hasQcSheet')" prop="hasQcSheet" width="100" align="center">
          <template #default="scope">{{ flagText(scope.row.hasQcSheet) }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.sample.col.hasViabilityReport')" prop="hasViabilityReport" width="125" align="center">
          <template #default="scope">{{ flagText(scope.row.hasViabilityReport) }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.sample.col.hasPathology')" prop="hasPathology" width="105" align="center">
          <template #default="scope">{{ flagText(scope.row.hasPathology) }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.sample.col.submitterName')" prop="submitterName" width="110" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.submitterName || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.sample.col.groupName')" prop="groupName" width="120" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.groupName || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.sample.col.operatorName')" prop="operatorName" width="100" :show-overflow-tooltip="true" />
        <!-- ★ 最后修改：updateTime 为 null = 从没改过（SAMPLE-MP-001 的跨票行为变更），显式渲染 -->
        <el-table-column :label="t('lqg.sample.col.updateTime')" prop="updateTime" width="170" :show-overflow-tooltip="true">
          <template #default="scope">
            <span v-if="neverModified(scope.row)" class="lqg-sample__muted">{{ t('lqg.sample.neverModified') }}</span>
            <span v-else>{{ scope.row.updateTime }}<span class="lqg-sample__muted"> · {{ scope.row.updateByName }}</span></span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.sample.col.remark')" prop="remark" min-width="120" :show-overflow-tooltip="true">
          <template #default="scope">{{ scope.row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.sample.col.action')" width="230" align="center" fixed="right" class-name="small-padding fixed-width">
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
            <el-button link disabled :title="t('lqg.sample.rowAction.notYet')">{{ t('lqg.sample.rowAction.qcDoc') }}</el-button>
            <el-button link disabled :title="t('lqg.sample.rowAction.notYet')">{{ t('lqg.sample.rowAction.embed') }}</el-button>
            <el-button link disabled :title="t('lqg.sample.rowAction.notYet')">{{ t('lqg.sample.rowAction.cryo') }}</el-button>
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

    <sample-drawer ref="drawerRef" :units="units" @saved="getList" />
  </div>
</template>

<script setup name="LqgSample" lang="ts">
import { delSample, listSamples, neverModified } from '@/api/lqg/sample';
import type { SampleQuery, SampleVO } from '@/api/lqg/sample';
import { listGroups, listUnits } from '@/api/lqg/auth/group';
import type { SourceUnitVO, UnitGroupVO } from '@/api/lqg/auth/group';
import SampleDrawer from './SampleDrawer.vue';
import { useI18n } from 'vue-i18n';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { t } = useI18n();

// 字典全部走 useDict（ticket §2.2）；文案走 lqg.sample.*
const { sample_kind, lqg_submit_source, lqg_verify_status, lqg_gender } = toRefs<any>(
  proxy?.useDict('sample_kind', 'lqg_submit_source', 'lqg_verify_status', 'lqg_gender')
);

const loading = ref(false);
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
  sampleKind: null,
  submitSource: null,
  verifyStatus: null,
  receiveDateBegin: null,
  receiveDateEnd: null,
  tissueType: null,
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
  queryParams.sampleKind = null;
  queryParams.submitSource = null;
  queryParams.verifyStatus = null;
  queryParams.tissueType = null;
  queryParams.internalNo = null;
  queryParams.operatorName = null;
  queryParams.donorName = null;
  queryParams.hospitalNo = null;
  groups.value = [];
  handleQuery();
};

const handleAdd = (kind: string) => {
  drawerRef.value?.openAdd(kind);
};

const handleOpen = (row: SampleVO) => {
  drawerRef.value?.open(row);
};

const handleExportNotYet = (sheet: string) => {
  proxy?.$modal.msgWarning(t('lqg.sample.toolbar.exportNotYet') + ' · ' + sheet);
};

const handleDelete = async (row: SampleVO) => {
  await proxy?.$modal.confirm(t('lqg.sample.rowAction.deleteConfirm', { no: row.submitNo }));
  await delSample(row.id);
  proxy?.$modal.msgSuccess(t('lqg.sample.rowAction.deleted'));
  await getList();
};

/** 待核验行浅黄底（token；不写字面色值） */
const rowClassName = ({ row }: { row: SampleVO }) => (row.verifyStatus === 'pending' ? 'lqg-sample__row-pending' : '');

const kindText = (row: SampleVO) => (row.sampleKind === 'organoid' ? row.organoidType : row.tissueType);

const flagText = (value?: string | null) => {
  if (value === 'Y') return t('lqg.sample.flag.yes');
  if (value === 'N') return t('lqg.sample.flag.no');
  return '—';
};

onMounted(async () => {
  await loadUnits();
  await getList();
});
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
    margin-bottom: 4px;
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
