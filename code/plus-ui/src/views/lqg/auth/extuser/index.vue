<template>
  <div class="p-2 lqg-extuser">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8" align="middle">
          <el-col :span="1.5">
            <el-button v-hasPermi="['lqg:auth:extuser:list']" plain icon="Refresh" @click="getList">{{ t('lqg.auth.extuser.refresh') }}</el-button>
          </el-col>
        </el-row>
      </template>

      <el-alert type="info" :closable="false" show-icon class="mb8">
        <span v-html="subtitleHtml"></span>
      </el-alert>

      <!-- 筛选：状态 + 单位（UI:admin.auth.extuser） -->
      <el-form :inline="true" class="mb8">
        <el-form-item :label="t('lqg.auth.extuser.filterStatus')">
          <el-select v-model="query.bindStatus" clearable :placeholder="t('lqg.auth.extuser.filterAll')" style="width: 160px" @change="getList">
            <el-option :label="t('lqg.auth.extuser.statusUnbound')" value="unbound" />
            <el-option :label="t('lqg.auth.extuser.statusPending')" value="pending" />
            <el-option :label="t('lqg.auth.extuser.statusVerified')" value="verified" />
            <el-option :label="t('lqg.auth.extuser.statusRejected')" value="rejected" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('lqg.auth.extuser.filterUnit')">
          <el-select v-model="query.unitId" clearable :placeholder="t('lqg.auth.extuser.filterAll')" style="width: 200px" @change="getList">
            <el-option v-for="u in units" :key="u.unitId" :label="u.unitName" :value="u.unitId" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" border :data="rows" :empty-text="t('lqg.auth.extuser.empty')">
        <el-table-column :label="t('lqg.auth.extuser.colName')" align="center" prop="name" :show-overflow-tooltip="true" />
        <el-table-column :label="t('lqg.auth.extuser.colPhone')" align="center" prop="phone" width="140">
          <template #default="scope">
            <span class="lqg-mono">{{ scope.row.phone }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.auth.extuser.colUnit')" align="center" :show-overflow-tooltip="true">
          <template #default="scope">
            <!-- 自填的单位 / 组别用斜体 + 「自填」标签（UI:admin.auth.extuser） -->
            <span :class="{ 'lqg-extuser__self': scope.row.selfInput }">{{ scope.row.unitName || t('lqg.auth.extuser.notFilled') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.auth.extuser.colGroup')" align="center" :show-overflow-tooltip="true">
          <template #default="scope">
            <span :class="{ 'lqg-extuser__self': scope.row.selfInput }">{{ scope.row.groupName || t('lqg.auth.extuser.notFilled') }}</span>
            <el-tag v-if="scope.row.selfInput" type="warning" effect="plain" size="small" class="ml8">
              {{ t('lqg.auth.extuser.selfInputTag') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.auth.extuser.colStatus')" align="center" width="110">
          <template #default="scope">
            <el-tooltip :disabled="!scope.row.rejectReason" :content="scope.row.rejectReason || ''" placement="top">
              <el-tag :type="statusTagType(scope.row.bindStatus)" effect="plain">{{ statusLabel(scope.row.bindStatus) }}</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.auth.extuser.colSampleCount')" align="center" width="110" prop="sampleCount" />
        <el-table-column :label="t('lqg.auth.extuser.colLastLogin')" align="center" width="170">
          <template #default="scope">
            <span v-if="scope.row.lastLoginTime">{{ formatTime(scope.row.lastLoginTime) }}</span>
            <span v-else class="lqg-extuser__none">{{ t('lqg.auth.extuser.neverLogin') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.auth.extuser.colAction')" align="center" width="130" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button
              v-hasPermi="['lqg:auth:extuser:verify']"
              link
              type="primary"
              icon="Finished"
              @click="handleVerify(scope.row)"
            >
              {{ scope.row.bindStatus === 'verified' ? t('lqg.auth.extuser.regroup') : t('lqg.auth.extuser.verify') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 核验弹窗：通过（自填的须选「新建」或「归并」）/ 驳回（原因必填） -->
    <el-dialog v-model="dialog.visible" :title="t('lqg.auth.extuser.dialogTitle')" width="560px" append-to-body :close-on-click-modal="false">
      <el-form label-width="110px">
        <el-form-item :label="t('lqg.auth.extuser.dialogUser')">
          <span>{{ dialog.row?.name }} · <span class="lqg-mono">{{ dialog.row?.phone }}</span></span>
        </el-form-item>
        <el-form-item :label="t('lqg.auth.extuser.dialogCurrent')">
          <span :class="{ 'lqg-extuser__self': dialog.row?.selfInput }">
            {{ dialog.row?.unitName || t('lqg.auth.extuser.notFilled') }}
            ·
            {{ dialog.row?.groupName || t('lqg.auth.extuser.notFilled') }}
          </span>
        </el-form-item>
        <el-form-item :label="t('lqg.auth.extuser.colAction')">
          <el-radio-group v-model="dialog.form.action">
            <el-radio-button value="approve">{{ t('lqg.auth.extuser.approve') }}</el-radio-button>
            <el-radio-button value="reject">{{ t('lqg.auth.extuser.reject') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- 通过 -->
        <template v-if="dialog.form.action === 'approve'">
          <el-alert type="info" :closable="false" show-icon class="mb8" :title="t('lqg.auth.extuser.approveHint')" />
          <template v-if="dialog.row?.selfInput">
            <el-alert type="warning" :closable="false" show-icon class="mb8" :title="t('lqg.auth.extuser.selfInputNotice')" />
            <el-form-item :label="t('lqg.auth.extuser.colAction')">
              <el-radio-group v-model="dialog.form.selfInputMode">
                <el-radio value="create">{{ t('lqg.auth.extuser.newOption') }}</el-radio>
                <el-radio value="merge">{{ t('lqg.auth.extuser.mergeOption') }}</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="dialog.form.selfInputMode === 'create'" :label="t('lqg.auth.extuser.newOption')">
              <span class="lqg-extuser__hint">
                {{ t('lqg.auth.extuser.newOptionHint', {
                  unit: dialog.row?.unitNameInput || dialog.row?.unitName || t('lqg.auth.extuser.notFilled'),
                  group: dialog.row?.groupNameInput || dialog.row?.groupName || t('lqg.auth.extuser.notFilled')
                }) }}
              </span>
            </el-form-item>
            <template v-else-if="dialog.form.selfInputMode === 'merge'">
              <el-form-item :label="t('lqg.auth.extuser.mergeUnit')">
                <el-select v-model="dialog.form.unitId" style="width: 100%" @change="handleMergeUnitChange">
                  <el-option v-for="u in selectableUnits" :key="u.unitId" :label="u.unitName" :value="u.unitId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('lqg.auth.extuser.mergeGroup')">
                <el-select v-model="dialog.form.groupId" style="width: 100%" :placeholder="dialog.form.unitId ? '' : t('lqg.auth.extuser.mergeGroupFirst')">
                  <el-option v-for="g in selectableGroups" :key="g.groupId" :label="g.groupName" :value="g.groupId" />
                </el-select>
              </el-form-item>
            </template>
          </template>
          <!-- 已归口的档案：允许改归组（verified→verified） -->
          <template v-else-if="dialog.row?.bindStatus === 'verified'">
            <el-form-item :label="t('lqg.auth.extuser.colUnit')">
              <el-select v-model="dialog.form.unitId" style="width: 100%" @change="handleMergeUnitChange">
                <el-option v-for="u in selectableUnits" :key="u.unitId" :label="u.unitName" :value="u.unitId" />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('lqg.auth.extuser.colGroup')">
              <el-select v-model="dialog.form.groupId" style="width: 100%">
                <el-option v-for="g in selectableGroups" :key="g.groupId" :label="g.groupName" :value="g.groupId" />
              </el-select>
            </el-form-item>
          </template>
        </template>

        <!-- 驳回 -->
        <template v-else>
          <el-form-item :label="t('lqg.auth.extuser.rejectReason')" required>
            <el-input v-model="dialog.form.reason" type="textarea" :rows="3" maxlength="200" show-word-limit :placeholder="t('lqg.auth.extuser.rejectReasonPlaceholder')" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="dialog.submitting" @click="submitVerify">确 定</el-button>
          <el-button @click="dialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="LqgAuthExtUser" lang="ts">
import { listExtUsers, listGroups, listUnits, verifyExtUser } from '@/api/lqg/auth/group';
import type { ExtUserVO, SourceUnitVO, UnitGroupVO } from '@/api/lqg/auth/group';
import { useI18n } from 'vue-i18n';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { t } = useI18n();
const route = useRoute();

const loading = ref(false);
const rows = ref<ExtUserVO[]>([]);
const units = ref<SourceUnitVO[]>([]);
const query = reactive<{ bindStatus?: string; unitId?: string | number }>({ bindStatus: undefined, unitId: undefined });

const subtitleHtml = computed(() =>
  t('lqg.auth.extuser.subtitle')
    .replace(/[<>&]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;' })[c] as string)
    .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
);

const statusLabel = (status: string) =>
  status === 'verified'
    ? t('lqg.auth.extuser.statusVerified')
    : status === 'pending'
      ? t('lqg.auth.extuser.statusPending')
      : status === 'rejected'
        ? t('lqg.auth.extuser.statusRejected')
        : t('lqg.auth.extuser.statusUnbound');

const statusTagType = (status: string) =>
  status === 'verified' ? 'success' : status === 'pending' ? 'warning' : status === 'rejected' ? 'danger' : 'info';

const formatTime = (value?: string | null) => (value ? String(value).replace('T', ' ').slice(0, 19) : '');

// ── 读 ──────────────────────────────────────────────────────────────────────
const getList = async () => {
  loading.value = true;
  try {
    const res = await listExtUsers({ bindStatus: query.bindStatus, unitId: query.unitId });
    rows.value = res.data ?? [];
  } finally {
    loading.value = false;
  }
};

const getUnits = async () => {
  const res = await listUnits();
  units.value = (res.data ?? []).filter((u) => u.unitStatus === 'active');
};

// ── 核验弹窗 ────────────────────────────────────────────────────────────────
const dialog = reactive<{
  visible: boolean;
  submitting: boolean;
  row: ExtUserVO | null;
  form: {
    action: '' | 'approve' | 'reject';
    selfInputMode: '' | 'create' | 'merge';
    unitId?: string | number | null;
    groupId?: string | number | null;
    reason?: string;
  };
}>({
  visible: false,
  submitting: false,
  row: null,
  form: { action: 'approve', selfInputMode: 'create', unitId: null, groupId: null, reason: '' }
});

const selectableUnits = computed(() => units.value);
const groupsOfUnit = ref<UnitGroupVO[]>([]);
const selectableGroups = computed(() => (dialog.form.unitId ? groupsOfUnit.value : []));

const loadGroupsOfUnit = async (unitId: string | number) => {
  const res = await listGroups(unitId);
  groupsOfUnit.value = (res.data ?? []).filter((g) => g.groupStatus === 'active');
};

const handleMergeUnitChange = async (unitId: string | number) => {
  dialog.form.groupId = null;
  if (unitId) {
    await loadGroupsOfUnit(unitId);
  } else {
    groupsOfUnit.value = [];
  }
};

const handleVerify = async (row: ExtUserVO) => {
  dialog.row = row;
  // 已核验的进来默认「改归组」：approve + 预选当前单位 / 组别。
  // 待核验的**不预选**（工作台 UX 测试 WEB-03：原来默认「通过 + 新建单位」，点一下确定就不可逆地通过、还建出重复单位）
  const verified = row.bindStatus === 'verified';
  dialog.form = {
    action: verified ? 'approve' : '',
    selfInputMode: '',
    unitId: row.unitId ?? null,
    groupId: row.groupId ?? null,
    reason: ''
  };
  if (row.unitId) {
    await loadGroupsOfUnit(row.unitId);
  }
  dialog.visible = true;
};

const submitVerify = async () => {
  if (!dialog.row) {
    return;
  }
  const form = dialog.form;
  if (!form.action) {
    proxy?.$modal.msgWarning(t('lqg.ux.extVerify.pickAction'));
    return;
  }
  if (form.action === 'approve' && dialog.row.selfInput && !form.selfInputMode) {
    proxy?.$modal.msgWarning(t('lqg.ux.extVerify.pickMode'));
    return;
  }
  const payload: Record<string, unknown> = { action: form.action };
  if (form.action === 'reject') {
    if (!form.reason || !String(form.reason).trim()) {
      proxy?.$modal.msgError(t('lqg.auth.extuser.rejectReasonRequired'));
      return;
    }
    payload.reason = String(form.reason).trim();
  } else if (dialog.row.selfInput) {
    // 自填的必须二选一（服务端也会拒，这里先给人话）
    if (form.selfInputMode === 'create') {
      payload.createUnit = true;
      payload.createGroup = true;
    } else {
      if (!form.unitId || !form.groupId) {
        proxy?.$modal.msgError(t('lqg.auth.extuser.mergeRequired'));
        return;
      }
      payload.unitId = form.unitId;
      payload.groupId = form.groupId;
    }
  } else if (dialog.row.bindStatus === 'verified') {
    // 改归组
    if (form.unitId && form.groupId) {
      payload.unitId = form.unitId;
      payload.groupId = form.groupId;
    }
  }
  // 通过待核验的人是不可逆的（还可能新建单位 / 组别）：写清楚后果再确认一次
  if (form.action === 'approve' && dialog.row.bindStatus !== 'verified') {
    const createLine =
      dialog.row.selfInput && form.selfInputMode === 'create'
        ? t('lqg.ux.extVerify.confirmCreate', {
            unit: dialog.row.unitNameInput || dialog.row.unitName || '—',
            group: dialog.row.groupNameInput || dialog.row.groupName || '—'
          })
        : '';
    try {
      await ElMessageBox.confirm(t('lqg.ux.extVerify.confirmApprove', { name: dialog.row.name || '' }) + createLine, t('lqg.ux.extVerify.confirmTitle'), {
        confirmButtonText: t('lqg.auth.extuser.approve'),
        type: 'warning'
      });
    } catch {
      return;
    }
  }
  dialog.submitting = true;
  try {
    await verifyExtUser(dialog.row.userId, payload as never);
    proxy?.$modal.msgSuccess(t('lqg.auth.extuser.approved'));
    dialog.visible = false;
    await getList();
  } finally {
    dialog.submitting = false;
  }
};

onMounted(async () => {
  // 工作台首页「待核验外部用户」卡片带 ?bindStatus=pending 进来（SYS-HOME-001）→ 自动套上筛选
  const bindStatus = route.query.bindStatus;
  if (typeof bindStatus === 'string' && ['unbound', 'pending', 'verified', 'rejected'].includes(bindStatus)) {
    query.bindStatus = bindStatus;
  }
  await getUnits();
  await getList();
});
</script>

<style scoped lang="scss">
.lqg-extuser {
  .lqg-mono {
    font-family: var(--lqg-font-mono);
  }
  /* 自填的单位 / 组别用斜体（UI:admin.auth.extuser 要求） */
  .lqg-extuser__self {
    font-style: italic;
    color: var(--el-color-warning);
  }
  .lqg-extuser__none {
    color: var(--el-text-color-secondary);
  }
  .lqg-extuser__hint {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
  .ml8 {
    margin-left: 8px;
  }
}
</style>
