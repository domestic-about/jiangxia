<script setup lang="ts">
import { ref, watch } from 'vue'
import type { CryoBatchRow } from '@/api/cryo'
import FieldRow from '@/components/lqg/FieldRow.vue'
import { formatDay, ln2Problem, registerCryoLn2 } from '@/pages/cryo/flow'
// ★ 一律显式 import wd-* 的 .vue（SAMPLE-MP-001 坑 6：只靠 easycom 会静默丢掉该模块的 .js 产物）
import WdDatetimePicker from 'wot-design-uni/components/wd-datetime-picker/wd-datetime-picker.vue'

// 登记转液氮表单 · 2026-09-24 甲方「-80 是暂存、液氮是长时间存放」+「小程序和工作台界面都能操作」（G 批 B2）。
//
// 只在批次还在 -80、没转过时由批次详情弹层打开（`canRegisterLn2`）。与工作台的转液氮弹窗同一条规则：
// 转移时间默认今天、不得早于冻存时间；液氮储存位置必填（东西进了液氮罐却没人知道在哪，比没登记更糟）。
// 保存后这一批当场变成「液氮」、退出超期提醒（超期是读时算的，不等定时任务）。
const props = defineProps<{
  batch: CryoBatchRow
}>()

const emit = defineEmits<{
  (e: 'saved'): void
  (e: 'cancel'): void
}>()

const toLn2Time = ref('')
const ln2Location = ref('')
const error = ref('')
const saving = ref(false)
const pickerValue = ref<number>(Date.now())
const pickerRef = ref<{ open: () => void } | null>(null)

function reset() {
  toLn2Time.value = formatDay(new Date())
  ln2Location.value = String(props.batch?.ln2Location ?? '')
  error.value = ''
  saving.value = false
}

watch(() => props.batch?.id, reset, { immediate: true })

function openPicker() {
  const ms = new Date(`${toLn2Time.value}T00:00:00`).getTime()
  pickerValue.value = Number.isNaN(ms) ? Date.now() : ms
  pickerRef.value?.open()
}

function onPicked(event: { value: number | string }) {
  const ms = Number(event.value)
  if (!Number.isNaN(ms)) {
    toLn2Time.value = formatDay(new Date(ms))
  }
}

async function submit() {
  if (saving.value) {
    return
  }
  const problem = ln2Problem(toLn2Time.value, ln2Location.value, props.batch.freezeTime)
  if (problem) {
    error.value = problem
    return
  }
  error.value = ''
  saving.value = true
  try {
    await registerCryoLn2(props.batch.id as string | number, {
      toLn2Time: toLn2Time.value,
      ln2Location: ln2Location.value.trim(),
    })
    uni.showToast({ title: '已登记转液氮', icon: 'none' })
    emit('saved')
  }
  catch (e) {
    error.value = e instanceof Error && e.message ? e.message : '没保存成功，请稍后再试'
  }
  finally {
    saving.value = false
  }
}
</script>

<template>
  <view class="clf">
    <view class="lqg-sheet__head">
      <text class="clf__title">转液氮</text>
      <text class="clf__cancel" @click="emit('cancel')">返回</text>
    </view>
    <text class="clf__meta">
      <text class="lqg-mono">{{ batch.cryoName || '—' }}</text>
      <text> · 冻存 {{ String(batch.freezeTime || '').slice(0, 10) || '—' }}</text>
    </text>

    <view class="clf__group">
      <FieldRow label="转移至液氮时间" control="date" required :model-value="toLn2Time" @pick="openPicker" />
      <FieldRow
        label="液氮储存位置"
        required
        :maxlength="100"
        :model-value="ln2Location"
        placeholder="例如 1号罐-1架-A2"
        @update:model-value="(v: string) => ln2Location = v"
      />
    </view>

    <text class="clf__tip">登记后这一批的位置改为液氮，不再提醒转液氮。</text>

    <view v-if="error" class="lqg-note lqg-note--danger clf__err">
      <text>{{ error }}</text>
    </view>

    <button class="clf__btn" :disabled="saving" @click="submit">
      {{ saving ? '正在保存…' : '登记转液氮' }}
    </button>

    <wd-datetime-picker
      ref="pickerRef"
      v-model="pickerValue"
      type="date"
      title="转移至液氮时间"
      @confirm="onPicked"
    >
      <!-- 空的默认插槽（G12）：不然 wd-datetime-picker 会自己多渲染一行「值 ›」 -->
      <view />
    </wd-datetime-picker>
  </view>
</template>

<style lang="scss" scoped>
.clf__title {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.clf__cancel {
  font-size: var(--lqg-fs-title);
  color: var(--lqg-primary);
}

.clf__meta {
  display: block;
  margin-top: var(--lqg-sp-3);
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.clf__group {
  margin-top: var(--lqg-sp-5);
  overflow: hidden;
  border-radius: var(--lqg-radius-card);
  background: var(--lqg-card);
  box-shadow: var(--lqg-shadow-sm);
}

.clf__tip {
  display: block;
  margin-top: var(--lqg-sp-4);
  font-size: var(--lqg-fs-xs);
  color: var(--lqg-ink-3);
}

.clf__err {
  margin: var(--lqg-sp-5) 0 0;
}

.clf__btn {
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

.clf__btn::after {
  border: none;
}
</style>
