<template>
  <el-dialog v-model="visible" :title="title" width="520px" append-to-body :close-on-click-modal="true">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="112px">
      <!-- ★ 登记类型不可改（CR-20260917-04）：新增时是选定的类型，修改时是原来的类型 -->
      <el-form-item :label="t('lqg.cryo.flow.colType')">
        <span class="lqg-cryo-flow-dialog__type">{{ t('lqg.cryo.flowType.' + kind) }}</span>
        <span v-if="editing" class="lqg-cryo-flow-dialog__muted">{{ t('lqg.cryo.flow.flowTypeLocked') }}</span>
      </el-form-item>

      <el-form-item :label="qtyLabel" prop="qty">
        <div class="lqg-cryo-flow-dialog__stack">
          <el-input-number v-model="form.qty" :precision="0" :step="1" controls-position="right" class="lqg-cryo-flow-dialog__control" />
          <!-- 剩余提示走接口读时算的 remainingQty，前端不自己累加流水 -->
          <span v-if="!editing && kind !== 'adjust'" class="lqg-cryo-flow-dialog__muted">
            {{ t('lqg.cryo.flow.balanceTipCurrent', { qty: remainingLabel }) }}
          </span>
        </div>
      </el-form-item>

      <el-form-item :label="t('lqg.cryo.flow.purpose')" prop="purpose">
        <el-input
          v-model="form.purpose"
          type="textarea"
          :rows="2"
          maxlength="200"
          :placeholder="t('lqg.cryo.flow.purposePlaceholder')"
        />
      </el-form-item>

      <el-form-item :label="t('lqg.cryo.flow.operatorName')">
        <el-input v-model="form.operatorName" maxlength="50" clearable />
      </el-form-item>

      <el-form-item :label="t('lqg.cryo.flow.flowTime')">
        <el-date-picker
          v-model="form.flowTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          class="lqg-cryo-flow-dialog__control"
          clearable
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button type="primary" :loading="submitting" @click="submit">{{ t('lqg.cryo.flow.save') }}</el-button>
      <el-button @click="visible = false">{{ t('lqg.cryo.flow.cancel') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup name="LqgCryoFlowDialog" lang="ts">
import { addFlow, updateFlow } from '@/api/lqg/cryo';
import type { CryoBatchVO, CryoFlowVO } from '@/api/lqg/cryo';
import { failText, isFlowKind, needsEmptyConfirm, purposeProblem, qtyProblem, editableQtyOf } from './flow';
import type { FlowKind } from './flow';
import { useI18n } from 'vue-i18n';

/**
 * 取走 / 补入 / 盘点调整 / 修改登记 —— 四个形态共用**同一个弹窗**
 * （ticket §0 口径复述 3：「修改弹窗与取走 / 补入 / 调整同款（类型不可改）」）。
 *
 * ★ 类型在打开时定死、界面上只显示不可编辑（要换类型只能删掉重登）。
 * ★ 被拒时把**后端原话**弹出来（「已取走 N 支…」「会让后面某一步剩余为负…」），不静默。
 * ★ 取走让这一批从有变成 0 支时，提交前多问一句「登记后这一批就取空了（剩 0 支），确定吗？」
 *   （2026-09-24 甲方「支数取空的要提示」；判据 needsEmptyConfirm 与小程序同一组用例）。
 */
const emit = defineEmits<{ (e: 'saved'): void }>();

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { t } = useI18n();

const visible = ref(false);
const submitting = ref(false);
const editing = ref(false);
const kind = ref<FlowKind>('take');
const batchId = ref<string | number | null>(null);
const flowId = ref<string | number | null>(null);
const remaining = ref<number | null>(null);
/** 批次**现在**的剩余（取空确认用；修改时由流水抽屉把最新的传进来） */
const currentRemaining = ref<number | null>(null);
/** 改一笔时，那一笔原来的带符号支数（新登记为 0） */
const oldDelta = ref(0);

const formRef = ref<ElFormInstance>();
const form = reactive<{ qty: number | null; purpose: string | null; operatorName: string | null; flowTime: string | null }>({
  qty: null,
  purpose: null,
  operatorName: null,
  flowTime: null
});

const title = computed(() => {
  if (editing.value) {
    return t('lqg.cryo.flow.dialogEdit');
  }
  if (kind.value === 'add') {
    return t('lqg.cryo.flow.dialogAdd');
  }
  if (kind.value === 'adjust') {
    return t('lqg.cryo.flow.dialogAdjust');
  }
  return t('lqg.cryo.flow.dialogTake');
});

const qtyLabel = computed(() =>
  kind.value === 'add'
    ? t('lqg.cryo.flow.qtyAdd')
    : kind.value === 'adjust'
      ? t('lqg.cryo.flow.qtyAdjust')
      : t('lqg.cryo.flow.qtyTake')
);

const remainingLabel = computed(() => (remaining.value === null ? '—' : String(remaining.value)));

/** 支数必填（取走 / 补入 / 调整三档的判据都在 flow.ts 的 qtyProblem 里） */
const rules = computed<ElFormRules>(() => ({
  qty: [{ required: true, message: t('lqg.cryo.flow.' + (qtyProblem(kind.value, null) ?? 'qtyTakeRequired')), trigger: 'change' }],
  purpose:
    kind.value === 'adjust'
      ? [{ required: true, message: t('lqg.cryo.flow.purposeRequired'), trigger: 'blur' }]
      : []
}));

/** 打开「取走 / 补入 / 盘点调整」 */
const openCreate = (next: FlowKind, batch: CryoBatchVO, presetPurpose?: string | null) => {
  editing.value = false;
  kind.value = next;
  batchId.value = batch?.id ?? null;
  flowId.value = null;
  remaining.value = batch?.remainingQty ?? null;
  currentRemaining.value = batch?.remainingQty ?? null;
  oldDelta.value = 0;
  form.qty = null;
  form.purpose = presetPurpose ?? null;
  form.operatorName = null;
  form.flowTime = null;
  visible.value = true;
};

/**
 * 打开「修改」（类型不可改；qty 按原类型解释）。
 *
 * @param nowRemaining 批次现在的剩余（流水抽屉拿最新一次 flows 响应里的；取空确认用，不做超取判断）
 */
const openEdit = (batch: CryoBatchVO, flow: CryoFlowVO, nowRemaining?: number | null) => {
  editing.value = true;
  kind.value = isFlowKind(flow?.flowType) ? flow.flowType : 'adjust';
  batchId.value = batch?.id ?? null;
  flowId.value = flow?.id ?? null;
  remaining.value = null; // 改的是历史账，「那一刻的余额」只有后端算得出来
  currentRemaining.value = nowRemaining ?? batch?.remainingQty ?? null;
  oldDelta.value = flow?.delta ?? 0;
  form.qty = editableQtyOf(flow);
  form.purpose = flow?.purpose ?? null;
  form.operatorName = flow?.operatorName ?? null;
  form.flowTime = flow?.flowTime ?? null;
  visible.value = true;
};

const submit = async () => {
  const problem = qtyProblem(kind.value, form.qty, editing.value ? null : remaining.value);
  if (problem) {
    proxy?.$modal.msgWarning(t('lqg.cryo.flow.' + problem, { qty: remainingLabel.value }));
    return;
  }
  const reasonProblem = purposeProblem(kind.value, form.purpose);
  if (reasonProblem) {
    proxy?.$modal.msgWarning(t('lqg.cryo.flow.' + reasonProblem));
    return;
  }
  // ★ 这一笔会让批次从有变成 0 支 → 提交前多问一句（点「取消」就停在弹窗里）
  if (needsEmptyConfirm(kind.value, form.qty, currentRemaining.value, oldDelta.value)) {
    try {
      await proxy?.$modal.confirm(t('lqg.cryo.flow.emptyConfirm'));
    } catch {
      return;
    }
  }
  submitting.value = true;
  try {
    if (editing.value) {
      await updateFlow(batchId.value as string | number, flowId.value as string | number, {
        qty: form.qty,
        purpose: form.purpose,
        operatorName: form.operatorName,
        flowTime: form.flowTime,
        // 原类型原样回传：传了不同的后端才拒（这里传的是打开时那一笔的类型，必然相同）
        flowType: kind.value
      });
      proxy?.$modal.msgSuccess(t('lqg.cryo.flow.editSaved'));
    } else {
      await addFlow(batchId.value as string | number, {
        flowType: kind.value,
        qty: form.qty as number,
        purpose: form.purpose,
        operatorName: form.operatorName,
        flowTime: form.flowTime
      });
      proxy?.$modal.msgSuccess(t('lqg.cryo.flow.saved'));
    }
    visible.value = false;
    emit('saved');
  } catch (e) {
    // ★ 被拒：把后端那句原话弹出来（指出是哪一笔 / 超了多少），不关弹窗、不静默
    proxy?.$modal.msgError(failText(e, t('lqg.cryo.flow.saveFailed')));
  } finally {
    submitting.value = false;
  }
};

defineExpose({ openCreate, openEdit });
</script>

<style scoped lang="scss">
// ★ 模板根是 el-dialog（teleport 到 body），没有外层包裹元素 ——
//   所以不能用 `.lqg-cryo-flow-dialog { &__x {} }` 的嵌套写法（父选择器匹配不到任何节点，
//   整段样式会静默失效）。这里直接按 class 写，scoped 的 data-v 属性在元素本身上。
.lqg-cryo-flow-dialog__control {
  width: 100%;
}
.lqg-cryo-flow-dialog__stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
}
.lqg-cryo-flow-dialog__type {
  font-weight: 600;
  color: var(--lqg-ink);
}
.lqg-cryo-flow-dialog__muted {
  font-size: 12px;
  color: var(--lqg-ink-3);
}
</style>
