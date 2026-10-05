<template>
  <div class="p-2 lqg-unit">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8" align="middle">
          <el-col :span="1.5">
            <el-button v-hasPermi="['lqg:auth:unit:add']" type="primary" plain icon="Plus" @click="handleAddUnit">{{ t('lqg.auth.unit.addUnit') }}</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['lqg:auth:unit:list']" plain icon="Refresh" @click="refreshAll">{{ t('lqg.auth.unit.refresh') }}</el-button>
          </el-col>
        </el-row>
      </template>

      <el-alert type="info" :closable="false" show-icon class="mb8">
        <span v-html="subtitleHtml"></span>
      </el-alert>

      <!-- 主从布局：左单位、右该单位的组别（UI:admin.auth.unit） -->
      <el-row :gutter="16">
        <!-- 左：单位 -->
        <el-col :xs="24" :md="10">
          <div class="lqg-unit__panel-title">{{ t('lqg.auth.unit.unitPanel') }}</div>
          <el-table
            v-loading="loadingUnits"
            border
            highlight-current-row
            :data="units"
            :empty-text="t('lqg.auth.unit.unitEmpty')"
            @current-change="handleSelectUnit"
          >
            <el-table-column :label="t('lqg.auth.unit.colUnitName')" prop="unitName" :show-overflow-tooltip="true">
              <template #default="scope">
                <span>{{ scope.row.unitName }}</span>
                <el-tag v-if="scope.row.unitStatus === 'pending'" type="warning" effect="plain" size="small" class="ml8">
                  {{ t('lqg.auth.unit.statusPending') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('lqg.auth.unit.colGroupCount')" align="center" width="90" prop="groupCount" />
            <el-table-column :label="t('lqg.auth.unit.colUnitStatus')" align="center" width="90">
              <template #default="scope">
                <el-tag :type="statusTagType(scope.row.unitStatus)" effect="plain">{{ statusLabel(scope.row.unitStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('lqg.auth.extuser.colAction')" align="center" width="150" class-name="small-padding fixed-width">
              <template #default="scope">
                <el-tooltip :content="t('lqg.auth.extuser.colAction')" placement="top">
                  <el-button v-hasPermi="['lqg:auth:unit:edit']" link type="primary" icon="Edit" @click.stop="handleEditUnit(scope.row)"></el-button>
                </el-tooltip>
                <!-- 不物理删：只有启用 / 停用 -->
                <el-button
                  v-hasPermi="['lqg:auth:unit:toggle']"
                  link
                  :type="scope.row.unitStatus === 'disabled' ? 'success' : 'danger'"
                  :icon="scope.row.unitStatus === 'disabled' ? 'CircleCheck' : 'CircleClose'"
                  @click.stop="handleToggleUnit(scope.row)"
                ></el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-col>

        <!-- 右：该单位的组别 -->
        <el-col :xs="24" :md="14">
          <div class="lqg-unit__panel-title">
            {{ t('lqg.auth.unit.groupPanel') }}
            <span v-if="currentUnit" class="lqg-unit__panel-hint">{{ t('lqg.auth.unit.groupPanelHint', { unit: currentUnit.unitName }) }}</span>
          </div>
          <el-row v-if="currentUnit" class="mb8">
            <el-col :span="1.5">
              <el-button v-hasPermi="['lqg:auth:group:add']" type="primary" plain icon="Plus" size="small" @click="handleAddGroup">
                {{ t('lqg.auth.unit.addGroup') }}
              </el-button>
            </el-col>
          </el-row>
          <el-table
            v-if="currentUnit"
            v-loading="loadingGroups"
            border
            :data="groups"
            :empty-text="t('lqg.auth.unit.groupEmpty')"
          >
            <el-table-column :label="t('lqg.auth.unit.colGroupName')" prop="groupName" :show-overflow-tooltip="true">
              <template #default="scope">
                <span>{{ scope.row.groupName }}</span>
                <el-tag v-if="scope.row.groupStatus === 'pending'" type="warning" effect="plain" size="small" class="ml8">
                  {{ t('lqg.auth.unit.statusPending') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('lqg.auth.unit.colVerifiedCount')" align="center" width="110" prop="verifiedCount" />
            <el-table-column :label="t('lqg.auth.unit.colGroupStatus')" align="center" width="90">
              <template #default="scope">
                <el-tag :type="statusTagType(scope.row.groupStatus)" effect="plain">{{ statusLabel(scope.row.groupStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('lqg.auth.extuser.colAction')" align="center" width="150" class-name="small-padding fixed-width">
              <template #default="scope">
                <el-tooltip :content="t('lqg.auth.extuser.colAction')" placement="top">
                  <el-button v-hasPermi="['lqg:auth:group:edit']" link type="primary" icon="Edit" @click.stop="handleEditGroup(scope.row)"></el-button>
                </el-tooltip>
                <el-button
                  v-hasPermi="['lqg:auth:group:toggle']"
                  link
                  :type="scope.row.groupStatus === 'disabled' ? 'success' : 'danger'"
                  :icon="scope.row.groupStatus === 'disabled' ? 'CircleCheck' : 'CircleClose'"
                  @click.stop="handleToggleGroup(scope.row)"
                ></el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else :description="t('lqg.auth.unit.groupPanelNone')" />
        </el-col>
      </el-row>
    </el-card>

    <!-- 单位弹窗 -->
    <el-dialog v-model="unitDialog.visible" :title="unitDialog.isEdit ? t('lqg.auth.unit.unitDialogEdit') : t('lqg.auth.unit.unitDialogAdd')" width="480px" append-to-body :close-on-click-modal="false">
      <el-form ref="unitFormRef" :model="unitDialog.form" :rules="unitDialog.rules" label-width="100px">
        <el-form-item :label="t('lqg.auth.unit.unitName')" prop="unitName">
          <el-input v-model="unitDialog.form.unitName" :placeholder="t('lqg.auth.unit.unitNamePlaceholder')" maxlength="100" clearable />
        </el-form-item>
        <el-form-item :label="t('lqg.auth.unit.remark')" prop="remark">
          <el-input v-model="unitDialog.form.remark" type="textarea" :rows="2" :placeholder="t('lqg.auth.unit.remarkPlaceholder')" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="unitDialog.submitting" @click="submitUnit">确 定</el-button>
          <el-button @click="unitDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 组别弹窗 -->
    <el-dialog v-model="groupDialog.visible" :title="groupDialog.isEdit ? t('lqg.auth.unit.groupDialogEdit') : t('lqg.auth.unit.groupDialogAdd')" width="480px" append-to-body :close-on-click-modal="false">
      <el-form ref="groupFormRef" :model="groupDialog.form" :rules="groupDialog.rules" label-width="100px">
        <el-form-item :label="t('lqg.auth.unit.groupUnit')">
          <span>{{ currentUnit?.unitName }}</span>
        </el-form-item>
        <el-form-item :label="t('lqg.auth.unit.groupName')" prop="groupName">
          <el-input v-model="groupDialog.form.groupName" :placeholder="t('lqg.auth.unit.groupNamePlaceholder')" maxlength="100" clearable />
        </el-form-item>
        <el-form-item :label="t('lqg.auth.unit.remark')" prop="remark">
          <el-input v-model="groupDialog.form.remark" type="textarea" :rows="2" :placeholder="t('lqg.auth.unit.remarkPlaceholder')" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="groupDialog.submitting" @click="submitGroup">确 定</el-button>
          <el-button @click="groupDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="LqgAuthUnit" lang="ts">
import {
  addGroup,
  addUnit,
  listGroups,
  listUnits,
  toggleGroupStatus,
  toggleUnitStatus,
  updateGroup,
  updateUnit
} from '@/api/lqg/auth/group';
import type { SourceUnitForm, SourceUnitVO, UnitGroupForm, UnitGroupVO } from '@/api/lqg/auth/group';
import { useI18n } from 'vue-i18n';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { t } = useI18n();

const loadingUnits = ref(false);
const loadingGroups = ref(false);
const units = ref<SourceUnitVO[]>([]);
const groups = ref<UnitGroupVO[]>([]);
const currentUnit = ref<SourceUnitVO | null>(null);

const subtitleHtml = computed(() =>
  t('lqg.auth.unit.subtitle')
    .replace(/[<>&]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;' })[c] as string)
    .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
);

const statusLabel = (status: string) =>
  status === 'active' ? t('lqg.auth.unit.statusActive') : status === 'pending' ? t('lqg.auth.unit.statusPending') : t('lqg.auth.unit.statusDisabled');

const statusTagType = (status: string) => (status === 'active' ? 'success' : status === 'pending' ? 'warning' : 'info');

// ── 读 ──────────────────────────────────────────────────────────────────────
const getUnits = async () => {
  loadingUnits.value = true;
  try {
    const res = await listUnits();
    units.value = res.data ?? [];
    // 保持选中项：单位列表刷新后 currentUnit 要指向新对象（否则右栏状态是旧的）
    if (currentUnit.value) {
      const again = units.value.find((u) => String(u.unitId) === String(currentUnit.value?.unitId));
      currentUnit.value = again ?? null;
      if (again) {
        await getGroups(again.unitId);
      } else {
        groups.value = [];
      }
    }
  } finally {
    loadingUnits.value = false;
  }
};

const getGroups = async (unitId: string | number) => {
  loadingGroups.value = true;
  try {
    const res = await listGroups(unitId);
    groups.value = res.data ?? [];
  } finally {
    loadingGroups.value = false;
  }
};

const refreshAll = async () => {
  await getUnits();
};

const handleSelectUnit = async (row: SourceUnitVO | null) => {
  if (!row) {
    return;
  }
  currentUnit.value = row;
  await getGroups(row.unitId);
};

// ── 单位增改 ────────────────────────────────────────────────────────────────
const unitFormRef = ref<ElFormInstance>();
const unitDialog = reactive<{ visible: boolean; submitting: boolean; isEdit: boolean; unitId: string | number; form: SourceUnitForm; rules: ElFormRules }>({
  visible: false,
  submitting: false,
  isEdit: false,
  unitId: '',
  form: { unitName: '', remark: '' },
  rules: {
    unitName: [{ required: true, message: t('lqg.auth.unit.unitNameRequired'), trigger: 'blur' }]
  }
});

const handleAddUnit = () => {
  unitDialog.isEdit = false;
  unitDialog.unitId = '';
  unitDialog.form = { unitName: '', remark: '' };
  unitDialog.visible = true;
};

const handleEditUnit = (row: SourceUnitVO) => {
  unitDialog.isEdit = true;
  unitDialog.unitId = row.unitId;
  unitDialog.form = { unitName: row.unitName, remark: row.remark ?? '' };
  unitDialog.visible = true;
};

const submitUnit = () => {
  unitFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    unitDialog.submitting = true;
    try {
      if (unitDialog.isEdit) {
        await updateUnit(unitDialog.unitId, { ...unitDialog.form });
      } else {
        await addUnit({ ...unitDialog.form });
      }
      proxy?.$modal.msgSuccess(t('lqg.auth.unit.unitSaved'));
      unitDialog.visible = false;
      await getUnits();
    } finally {
      unitDialog.submitting = false;
    }
  });
};

const handleToggleUnit = async (row: SourceUnitVO) => {
  const next: 'active' | 'disabled' = row.unitStatus === 'disabled' ? 'active' : 'disabled';
  const action = next === 'active' ? t('lqg.auth.unit.toggleEnable') : t('lqg.auth.unit.toggleDisable');
  await proxy?.$modal.confirm(t('lqg.auth.unit.toggleUnitConfirm', { name: row.unitName, action }));
  await toggleUnitStatus(row.unitId, next);
  proxy?.$modal.msgSuccess(next === 'active' ? t('lqg.auth.unit.unitEnabled') : t('lqg.auth.unit.unitDisabled'));
  await getUnits();
};

// ── 组别增改 ────────────────────────────────────────────────────────────────
const groupFormRef = ref<ElFormInstance>();
const groupDialog = reactive<{ visible: boolean; submitting: boolean; isEdit: boolean; groupId: string | number; form: UnitGroupForm; rules: ElFormRules }>({
  visible: false,
  submitting: false,
  isEdit: false,
  groupId: '',
  form: { unitId: '', groupName: '', remark: '' },
  rules: {
    groupName: [{ required: true, message: t('lqg.auth.unit.groupNameRequired'), trigger: 'blur' }]
  }
});

const handleAddGroup = () => {
  if (!currentUnit.value) {
    return;
  }
  groupDialog.isEdit = false;
  groupDialog.groupId = '';
  groupDialog.form = { unitId: currentUnit.value.unitId, groupName: '', remark: '' };
  groupDialog.visible = true;
};

const handleEditGroup = (row: UnitGroupVO) => {
  groupDialog.isEdit = true;
  groupDialog.groupId = row.groupId;
  groupDialog.form = { unitId: row.unitId, groupName: row.groupName, remark: row.remark ?? '' };
  groupDialog.visible = true;
};

const submitGroup = () => {
  groupFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    groupDialog.submitting = true;
    try {
      if (groupDialog.isEdit) {
        await updateGroup(groupDialog.groupId, { ...groupDialog.form });
      } else {
        await addGroup({ ...groupDialog.form });
      }
      proxy?.$modal.msgSuccess(t('lqg.auth.unit.groupSaved'));
      groupDialog.visible = false;
      if (currentUnit.value) {
        await getGroups(currentUnit.value.unitId);
      }
      await getUnits();
    } finally {
      groupDialog.submitting = false;
    }
  });
};

const handleToggleGroup = async (row: UnitGroupVO) => {
  const next: 'active' | 'disabled' = row.groupStatus === 'disabled' ? 'active' : 'disabled';
  const action = next === 'active' ? t('lqg.auth.unit.toggleEnable') : t('lqg.auth.unit.toggleDisable');
  await proxy?.$modal.confirm(t('lqg.auth.unit.toggleGroupConfirm', { name: row.groupName, action }));
  await toggleGroupStatus(row.groupId, next);
  proxy?.$modal.msgSuccess(next === 'active' ? t('lqg.auth.unit.groupEnabled') : t('lqg.auth.unit.groupDisabled'));
  if (currentUnit.value) {
    await getGroups(currentUnit.value.unitId);
  }
};

onMounted(async () => {
  await getUnits();
  // 默认选中第一个单位（右栏不留空，少一次点击）
  if (!currentUnit.value && units.value.length > 0) {
    await handleSelectUnit(units.value[0]);
  }
});
</script>

<style scoped lang="scss">
.lqg-unit {
  .lqg-unit__panel-title {
    font-weight: 600;
    margin-bottom: 8px;
  }
  .lqg-unit__panel-hint {
    font-weight: 400;
    color: var(--el-text-color-secondary);
  }
  .ml8 {
    margin-left: 8px;
  }
}
</style>
