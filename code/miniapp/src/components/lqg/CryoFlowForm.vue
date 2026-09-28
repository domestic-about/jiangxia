<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { CryoBatchRow } from '@/api/cryo'
import FieldRow from '@/components/lqg/FieldRow.vue'
import type { CryoFlowRecord, MpFlowKind } from '@/pages/cryo/flow'
import {
  EMPTY_CONFIRM_TEXT,
  addCryoFlow,
  editableQtyText,
  flowQtyMessage,
  flowQtyProblem,
  formatDateTime,
  needsEmptyConfirm,
  parseQty,
  takeFromText,
  updateCryoFlow,
} from '@/pages/cryo/flow'
// ★ 一律显式 import wd-* 的 .vue（SAMPLE-MP-001 坑 6：只靠 easycom 会静默丢掉该模块的 .js 产物）
import WdDatetimePicker from 'wot-design-uni/components/wd-datetime-picker/wd-datetime-picker.vue'

// 冻存取用登记表单（取走 / 补入 / 改一笔）· 2026-09-24 甲方「小程序和工作台界面都能操作」（G 批 B2）。
//
// 在批次详情弹层（CryoBatchSheet）里就地换成这张表单，不另开一层弹层。规则与工作台的登记弹窗一致：
//   - 支数必填、正整数；新登记的取走不能超过当前剩余（`flowQtyProblem`，与工作台同一组用例）；
//   - 用途 / 原因选填；经手人默认当前登录人；时间默认现在；
//   - 取走从哪取**不让人选**：新登记按批次当前位置自动定，改一笔时是登记那一刻的位置（后端也不收这个键）；
//   - 这一笔让剩余从大于 0 变成 0 时，提交前多问一句「登记后这一批就取空了」；
//   - 被拒（超取、改完会让后面某一步为负……）时把后端原话显示在表单里，停在这里让人改。
const props = defineProps<{
  /** 这一批（读时算的 remainingQty / location 都在上面） */
  batch: CryoBatchRow
  /** 登记类型：新登记时是点的那个按钮，改一笔时是那一笔原来的类型（改不了） */
  kind: MpFlowKind
  /** 改一笔时传那一笔；新登记不传 */
  flow?: CryoFlowRecord | null
  /** 经手人默认值（当前登录人姓名） */
  operatorName?: string
}>()

const emit = defineEmits<{
  (e: 'saved'): void
  (e: 'cancel'): void
}>()

const qty = ref('')
const purpose = ref('')
const operator = ref('')
const flowTime = ref('')
const error = ref('')
const saving = ref(false)
const pickerValue = ref<number>(Date.now())
const pickerRef = ref<{ open: () => void } | null>(null)

const editing = computed(() => !!props.flow)
const kindText = computed(() => (props.kind === 'add' ? '补入' : '取走'))
const title = computed(() => (editing.value ? `修改这一笔${kindText.value}` : `${kindText.value}登记`))
const remaining = computed(() => props.batch.remainingQty ?? 0)
/** 取走从哪取：新登记看批次当前位置，改一笔看那一笔登记时的位置 */
const fromText = computed(() => takeFromText(editing.value ? props.flow?.fromLocation : props.batch.location))

function reset() {
  const flow = props.flow
  qty.value = flow ? editableQtyText(flow) : ''
  purpose.value = flow ? String(flow.purpose ?? '') : ''
  operator.value = flow ? String(flow.operatorName ?? '') : (props.operatorName || '')
  flowTime.value = flow?.flowTime ? String(flow.flowTime) : formatDateTime(new Date())
  error.value = ''
  saving.value = false
}

watch(() => [props.batch?.id, props.kind, props.flow?.id], reset, { immediate: true })

/** 支数只收数字 */
function onQty(value: string) {
  qty.value = String(value ?? '').replace(/\D/g, '')
}

/** `yyyy-MM-dd HH:mm:ss` → 毫秒（日期控件的值） */
function toMs(value: string): number {
  const ms = new Date(String(value || '').replace(' ', 'T')).getTime()
  return Number.isNaN(ms) ? Date.now() : ms
}

function openPicker() {
  pickerValue.value = toMs(flowTime.value)
  pickerRef.value?.open()
}

function onPicked(event: { value: number | string }) {
  const ms = Number(event.value)
  if (!Number.isNaN(ms)) {
    flowTime.value = formatDateTime(new Date(ms))
  }
}

/** 取空前的那一句确认（点「确定」才继续） */
function confirmEmpty(): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title: '这一批会取空',
      content: EMPTY_CONFIRM_TEXT,
      confirmText: '确定',
      cancelText: '再想想',
      success: res => resolve(!!res.confirm),
      fail: () => resolve(false),
    })
  })
}

async function submit() {
  if (saving.value) {
    return
  }
  const n = parseQty(qty.value)
  const problem = flowQtyProblem(props.kind, n, remaining.value, editing.value)
  if (problem) {
    error.value = flowQtyMessage(problem, props.kind, remaining.value)
    return
  }
  error.value = ''
  const oldDelta = props.flow?.delta ?? 0
  if (needsEmptyConfirm(props.kind, n, remaining.value, oldDelta) && !(await confirmEmpty())) {
    return
  }
  const body = {
    flowType: props.kind,
    qty: n,
    purpose: purpose.value.trim(),
    operatorName: operator.value.trim(),
    flowTime: flowTime.value,
  }
  saving.value = true
  try {
    if (props.flow) {
      await updateCryoFlow(props.batch.id as string | number, props.flow.id, body)
      uni.showToast({ title: '已保存', icon: 'none' })
    }
    else {
      await addCryoFlow(props.batch.id as string | number, body)
      uni.showToast({ title: '已登记', icon: 'none' })
    }
    emit('saved')
  }
  catch (e) {
    // ★ 被拒：后端原话（指出超了多少 / 是哪一笔）留在表单里，不关表单
    error.value = e instanceof Error && e.message ? e.message : '没保存成功，请稍后再试'
  }
  finally {
    saving.value = false
  }
}
</script>

<template>
  <view class="cff">
    <view class="lqg-sheet__head">
      <text class="cff__title">{{ title }}</text>
      <text class="cff__cancel" @click="emit('cancel')">返回</text>
    </view>
    <text class="cff__meta">
      <text class="lqg-mono">{{ batch.cryoName || '—' }}</text>
      <text> · 现剩 {{ remaining }} 支</text>
    </text>

    <view class="cff__group">
      <FieldRow v-if="kind === 'take'" label="取自" readonly :model-value="fromText" />
      <FieldRow
        :label="`${kindText}支数`"
        control="digit"
        required
        :model-value="qty"
        placeholder="正整数"
        @update:model-value="onQty"
      />
      <FieldRow
        label="用途 / 原因"
        :maxlength="200"
        :model-value="purpose"
        placeholder="选填，如 复苏培养"
        @update:model-value="(v: string) => purpose = v"
      />
      <FieldRow
        label="经手人"
        :maxlength="50"
        :model-value="operator"
        @update:model-value="(v: string) => operator = v"
      />
      <FieldRow label="时间" control="datetime" :model-value="flowTime" @pick="openPicker" />
    </view>

    <!-- 被拒 / 填错的原因（后端原话）：提示条同款样式，贴着表单、不缩进 -->
    <view v-if="error" class="lqg-note lqg-note--danger cff__err">
      <text>{{ error }}</text>
    </view>

    <button class="cff__btn" :disabled="saving" @click="submit">
      {{ saving ? '正在保存…' : (editing ? '保存' : `登记${kindText}`) }}
    </button>

    <wd-datetime-picker
      ref="pickerRef"
      v-model="pickerValue"
      type="datetime"
      title="选择时间"
      @confirm="onPicked"
    >
      <!-- 空的默认插槽（G12）：不然 wd-datetime-picker 会自己多渲染一行「值 ›」 -->
      <view />
    </wd-datetime-picker>
  </view>
</template>

<style lang="scss" scoped>
.cff__title {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.cff__cancel {
  font-size: var(--lqg-fs-title);
  color: var(--lqg-primary);
}

.cff__meta {
  display: block;
  margin-top: var(--lqg-sp-3);
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.cff__group {
  margin-top: var(--lqg-sp-5);
  overflow: hidden;
  border-radius: var(--lqg-radius-card);
  background: var(--lqg-card);
  box-shadow: var(--lqg-shadow-sm);
}

.cff__err {
  margin: var(--lqg-sp-5) 0 0;
}

.cff__btn {
  margin-top: var(--lqg-sp-7);
  height: var(--lqg-btn-h);
  line-height: var(--lqg-btn-h);
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-on-primary);
  background: var(--lqg-primary);
  border: none;
  border-radius: var(--lqg-radius-ctl);
  box-shadow: var(--lqg-shadow-brand);
}

.cff__btn::after {
  border: none;
}
</style>
