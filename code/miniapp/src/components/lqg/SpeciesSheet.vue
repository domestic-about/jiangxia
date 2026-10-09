<script setup lang="ts">
import { computed, ref } from 'vue'
import FieldRow from '@/components/lqg/FieldRow.vue'
import { SPECIES_MAX, isManualSpecies, speciesChoices } from '@/utils/species'
// ★ 显式 .vue 路径导入（SAMPLE-MP-001 坑 6：只靠 easycom 会让该模块的 .js 产物消失）
import WdPopup from 'wot-design-uni/components/wd-popup/wd-popup.vue'

// 填写页「种属」的底部选择面板（CR-20261009-18；组织样本、类器官、核验页共用）。
//
// 甲方：「比如说人啊、鼠兔、移植猪、鸡。但是呢，它还可以添加其他的。」
// ★ 格子 = 字典 lqg_species 的常用值（页面从 `/mp/dict/hints?type=species` 取好传进来；拉不到就用内置的四个）；
//   最后一格「列表里没有，手动填写」—— 手填的值原样存（去首尾空白，最多 50 字）。
// ★ 组件不改表单：点格子 / 手填都抛 `update:modelValue`，由页面写回。
// ★ 样式与「来源单位」面板（SourceUnitSheet）同一套：lqg-sheet + lqg-filter__chip。
const props = withDefaults(defineProps<{
  /** 当前种属 */
  modelValue?: string
  /** 字典里的常用值（页面拉好传进来） */
  options?: string[]
  /** 只读 */
  disabled?: boolean
}>(), {
  modelValue: '',
  options: () => [],
  disabled: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const show = ref(false)
/** 是否处在「手动填写」：打开时按当前值算（值不在常用值里 = 上次是手填的） */
const manual = ref(false)

const choices = computed(() => speciesChoices(props.options))

function open() {
  if (props.disabled) {
    return
  }
  manual.value = isManualSpecies(props.modelValue, choices.value)
  show.value = true
}

function pick(value: string) {
  manual.value = false
  emit('update:modelValue', value)
  show.value = false
}

function toManual() {
  if (!manual.value) {
    // 从「选了常用值」切到手填：清掉旧值，免得手填框里一上来就是「人」
    if (!isManualSpecies(props.modelValue, choices.value)) {
      emit('update:modelValue', '')
    }
    manual.value = true
  }
}

defineExpose({ open })
</script>

<template>
  <wd-popup
    v-model="show"
    position="bottom"
    custom-style="border-radius: var(--lqg-radius-sheet) var(--lqg-radius-sheet) 0 0"
    @close="show = false"
  >
    <view class="lqg-sheet">
      <view class="lqg-sheet__head">
        <text class="sps__t">种属</text>
        <text class="sps__x" @click="show = false">{{ manual ? '完成' : '关闭' }}</text>
      </view>
      <view class="sps__chips">
        <text
          v-for="item in choices"
          :key="item"
          class="lqg-filter__chip sps__chip"
          :class="{ 'sps__chip--on': !manual && modelValue === item }"
          @click="pick(item)"
        >{{ item }}</text>
        <text
          class="lqg-filter__chip sps__chip"
          :class="{ 'sps__chip--on': manual }"
          @click="toManual"
        >列表里没有，手动填写</text>
      </view>
      <FieldRow
        v-if="manual"
        label="种属"
        :readonly="disabled"
        :model-value="modelValue"
        :maxlength="SPECIES_MAX"
        placeholder="如：食蟹猴"
        @update:model-value="(v: string) => emit('update:modelValue', v)"
      />
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.sps__t {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.sps__x {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}

.sps__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lqg-sp-3);
  margin: var(--lqg-sp-5) 0;
}

.sps__chip {
  font-size: var(--lqg-fs-body);
}

.sps__chip--on {
  background: var(--lqg-primary-soft);
  color: var(--lqg-primary);
  font-weight: var(--lqg-fw-semibold);
  box-shadow: inset 0 0 0 1px var(--lqg-primary);
}
</style>
