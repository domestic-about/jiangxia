<template>
  <el-drawer v-model="visible" :title="t('lqg.cryo.flow.title')" size="720px" append-to-body :close-on-click-modal="true">
    <div v-if="batch" class="lqg-cryo-flow__head">
      <div class="lqg-cryo-flow__name">
        <span class="lqg-cryo-flow__mono">{{ batch.cryoName || '—' }}</span>
        <span class="lqg-cryo-flow__muted"> · {{ t('lqg.cryo.col.passage') }} {{ batch.passage || '—' }}</span>
      </div>
      <div class="lqg-cryo-flow__muted">{{ t('lqg.cryo.flow.subtitle') }}</div>
      <!-- ★ 当前剩余取接口的 remainingQty（读时算），前端不自己把流水加起来 -->
      <div class="lqg-cryo-flow__balance">
        {{ t('lqg.cryo.flow.currentRemaining', { qty: batch.remainingQty ?? 0 }) }}
        <span v-if="batch.overdue" class="lqg-cryo-flow__badge">
          {{ t('lqg.cryo.badge.overdue', { days: batch.overdueDays ?? 0 }) }}
        </span>
      </div>
    </div>

    <div v-if="loading" class="lqg-cryo-flow__muted">{{ t('lqg.cryo.flow.loading') }}</div>
    <el-empty v-else-if="!rows.length" :description="t('lqg.cryo.flow.empty')" />

    <!-- 时间线：每行显示操作后剩余与「已改 · 某某 时间」，带「修改」「删除」 -->
    <el-timeline v-else>
      <el-timeline-item
        v-for="row in rows"
        :key="String(row.id)"
        :timestamp="row.flowTime || '—'"
        placement="top"
        :type="row.flowType === 'take' ? 'danger' : row.flowType === 'add' ? 'success' : 'warning'"
      >
        <div class="lqg-cryo-flow__row">
          <div class="lqg-cryo-flow__line">
            <span class="lqg-cryo-flow__type">{{ t('lqg.cryo.flowType.' + flowTypeKey(row.flowType)) }}</span>
            <span class="lqg-cryo-flow__delta">{{ deltaText(row) }}</span>
            <span class="lqg-cryo-flow__muted">{{ t('lqg.cryo.flow.colFrom') }}：{{ t('lqg.cryo.location.' + locationKey(row.fromLocation)) }}</span>
            <span class="lqg-cryo-flow__muted">{{ t('lqg.cryo.flow.colOperator') }}：{{ row.operatorName || '—' }}</span>
            <span class="lqg-cryo-flow__muted">
              {{ t('lqg.cryo.flow.colBalanceAfter') }}：<b>{{ row.balanceAfter ?? '—' }}</b>
            </span>
          </div>
          <div v-if="row.purpose" class="lqg-cryo-flow__muted">{{ t('lqg.cryo.flow.colPurpose') }}：{{ row.purpose }}</div>
          <div class="lqg-cryo-flow__line">
            <!-- ★ 改过要显示「已改 · 某某 时间」；没改过显示「从未修改」（不是把创建时间当修改时间） -->
            <span v-if="row.edited" class="lqg-cryo-flow__edited">
              {{ t('lqg.cryo.flow.lastModified', { name: row.updateByName || '—', time: row.updateTime || '—' }) }}
            </span>
            <span v-else class="lqg-cryo-flow__muted">{{ t('lqg.cryo.flow.lastModifiedNever') }}</span>
            <el-button v-hasPermi="['lqg:cryo:flow']" link type="primary" @click="handleEdit(row)">
              {{ t('lqg.cryo.flow.edit') }}
            </el-button>
            <el-button v-hasPermi="['lqg:cryo:flow']" link type="danger" @click="handleRemove(row)">
              {{ t('lqg.cryo.flow.remove') }}
            </el-button>
          </div>
        </div>
      </el-timeline-item>
    </el-timeline>

    <template #footer>
      <el-button @click="visible = false">{{ t('lqg.cryo.flow.close') }}</el-button>
    </template>
  </el-drawer>

  <!-- 修改：与取走 / 补入 / 调整**同款**弹窗（类型不可改） -->
  <cryo-flow-dialog ref="dialogRef" @saved="reload" />
</template>

<script setup name="LqgCryoFlowDrawer" lang="ts">
import { deleteFlow, deltaText, listFlows, locationKey, flowTypeKey } from '@/api/lqg/cryo';
import type { CryoBatchVO, CryoFlowVO } from '@/api/lqg/cryo';
import CryoFlowDialog from './CryoFlowDialog.vue';
import { failText } from './flow';
import { useI18n } from 'vue-i18n';

/**
 * 流水抽屉（UI:admin.cryo.list：「流水（抽屉，时间倒序，显示类型 / 变化量 / 取自 / 经手人 /
 * 时间 / 用途 / 操作后剩余 / 最后修改；每行『修改』『删除』）」）。
 *
 * ★ 每行的「修改」「删除」接的是 CRYO-FLOW-001 的 `PUT|DELETE …/{id}/flow/{flowId}`：
 *   改完记修改人（行上显示「已改 · 某某 时间」）、删是**软删**。被拒（会让后面某一步
 *   剩余为负）时把后端原话弹出来并指出是哪一笔，库里不变 —— 所以被拒后**仍重新拉一次**
 *   流水（后端保证没变，拉一次只是让界面与库对齐，不做乐观更新）。
 */
const emit = defineEmits<{ (e: 'changed'): void }>();

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { t } = useI18n();

const visible = ref(false);
const loading = ref(false);
const batch = ref<CryoBatchVO | null>(null);
const rows = ref<CryoFlowVO[]>([]);
const dialogRef = ref<InstanceType<typeof CryoFlowDialog>>();

const reload = async () => {
  if (!batch.value) {
    return;
  }
  loading.value = true;
  try {
    const res = await listFlows(batch.value.id);
    rows.value = (res.data ?? []) as CryoFlowVO[];
  } catch (e) {
    proxy?.$modal.msgError(failText(e, t('lqg.cryo.msg.loadFailed')));
  } finally {
    loading.value = false;
  }
  // 列表那边也要刷新（剩余与超期标记当场刷新）
  emit('changed');
};

/** 打开抽屉（row 是列表里那一行，带读时算的 remainingQty / overdue） */
const open = async (row: CryoBatchVO) => {
  batch.value = row;
  rows.value = [];
  visible.value = true;
  await reload();
};

const handleEdit = (row: CryoFlowVO) => {
  if (!batch.value) {
    return;
  }
  dialogRef.value?.openEdit(batch.value, row);
};

const handleRemove = async (row: CryoFlowVO) => {
  await proxy?.$modal.confirm(
    t('lqg.cryo.flow.removeConfirm', { type: t('lqg.cryo.flowType.' + flowTypeKey(row.flowType)), delta: deltaText(row) })
  );
  try {
    await deleteFlow(batch.value?.id as string | number, row.id);
    proxy?.$modal.msgSuccess(t('lqg.cryo.flow.removed'));
  } catch (e) {
    // ★ 被拒：后端指出是哪一笔（例如「会让后面的取走变成负数」），原话弹出来
    proxy?.$modal.msgError(failText(e, t('lqg.cryo.flow.saveFailed')));
  }
  await reload();
};

defineExpose({ open, reload });
</script>

<style scoped lang="scss">
.lqg-cryo-flow__head {
  margin-bottom: 12px;
}
.lqg-cryo-flow__name {
  font-weight: 600;
  color: var(--lqg-ink);
}
.lqg-cryo-flow__balance {
  margin-top: 4px;
  font-weight: 600;
  color: var(--lqg-ink);
}
.lqg-cryo-flow__badge {
  margin-left: 8px;
  padding: 1px 8px;
  font-weight: 600;
  color: var(--lqg-danger);
  background-color: var(--lqg-danger-soft);
  border-radius: 10px;
}
.lqg-cryo-flow__row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.lqg-cryo-flow__line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.lqg-cryo-flow__type {
  font-weight: 600;
  color: var(--lqg-ink);
}
.lqg-cryo-flow__delta {
  font-family: var(--lqg-font-mono);
  font-weight: 600;
  color: var(--lqg-danger);
}
.lqg-cryo-flow__edited {
  font-size: 12px;
  color: var(--lqg-warn);
}
.lqg-cryo-flow__mono {
  font-family: var(--lqg-font-mono);
}
.lqg-cryo-flow__muted {
  font-size: 12px;
  color: var(--lqg-ink-3);
}
</style>
