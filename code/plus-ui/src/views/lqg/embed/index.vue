<template>
  <div class="p-2 lqg-embed">
    <el-card shadow="hover">
      <template #header>
        <div class="lqg-embed__head">
          <span class="lqg-embed__title">{{ t('lqg.embed.title') }}</span>
          <span class="lqg-embed__subtitle">{{ t('lqg.embed.subtitle') }}</span>
        </div>
      </template>

      <!-- 筛选区（UI:admin.embed.list）：石蜡块编号 / 内部编号 / 染色 / 切片时间区间 / 核验状态 / 内-外部 -->
      <el-form ref="queryRef" :model="queryParams" label-width="86px" class="lqg-embed__filter">
        <el-row :gutter="12">
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
            <el-form-item :label="t('lqg.embed.filter.verifyStatus')" prop="verifyStatus">
              <el-select v-model="queryParams.verifyStatus" clearable class="lqg-embed__control">
                <el-option v-for="d in lqg_verify_status" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.embed.filter.submitSource')" prop="submitSource">
              <el-select v-model="queryParams.submitSource" clearable class="lqg-embed__control">
                <el-option v-for="d in lqg_submit_source" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
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
        <!-- 从样本总表带 sampleId 跳进来的提示 + 一键清除 -->
        <el-col v-if="queryParams.sampleId" :span="6">
          <el-tag type="info" closable class="lqg-embed__sample-tag" @close="clearSampleFilter">
            {{ t('lqg.embed.filter.sampleFilter', { id: queryParams.sampleId }) }}
          </el-tag>
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
        <el-table-column :label="t('lqg.embed.col.internalNo')" prop="internalNo" width="130" :show-overflow-tooltip="true">
          <template #default="scope">
            <span class="lqg-embed__mono">{{ scope.row.internalNo || '—' }}</span>
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

    <embed-drawer ref="drawerRef" @saved="getList" />
  </div>
</template>

<script setup name="LqgEmbed" lang="ts">
import { delEmbed, exportEmbeds, isEditable, isExternalPending, listEmbeds, neverModified } from '@/api/lqg/embed';
import type { EmbedQuery, EmbedVO } from '@/api/lqg/embed';
import EmbedDrawer from './EmbedDrawer.vue';
import { useI18n } from 'vue-i18n';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { t } = useI18n();
const route = useRoute();

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

const resetQuery = () => {
  queryRef.value?.resetFields();
  sectionTimeRange.value = null;
  queryParams.paraffinBlockNo = null;
  queryParams.internalNo = null;
  queryParams.stain = null;
  queryParams.verifyStatus = null;
  queryParams.submitSource = null;
  queryParams.sampleId = null;
  handleQuery();
};

const clearSampleFilter = () => {
  queryParams.sampleId = null;
  handleQuery();
};

const handleAdd = () => {
  drawerRef.value?.openAdd();
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
  // 从样本总表带 sampleId 跳入（样本总表「石蜡包埋」行操作）→ 自动按样本过滤
  const sampleId = route.query.sampleId;
  if (sampleId) {
    queryParams.sampleId = String(sampleId);
  }
  // 工作台首页「待核验石蜡包埋送样」卡片带 ?verifyStatus=pending 进来（SYS-HOME-001）→ 自动套上筛选
  const verifyStatus = route.query.verifyStatus;
  if (typeof verifyStatus === 'string' && ['pending', 'valid', 'invalid'].includes(verifyStatus)) {
    queryParams.verifyStatus = verifyStatus;
  }
  await getList();
});
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
  .lqg-embed__sample-tag {
    margin-top: 4px;
  }

  // 待核验行浅黄底（UI:admin.embed.list；token = --lqg-warn-soft）
  :deep(.lqg-embed__row-pending) {
    --el-table-tr-bg-color: var(--lqg-warn-soft);
    background-color: var(--lqg-warn-soft);
  }
}
</style>
