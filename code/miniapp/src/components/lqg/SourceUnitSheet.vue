<script setup lang="ts">
import { computed, ref } from 'vue'
import type { SelectorUnit } from '@/api/unit-group'
import FieldRow from '@/components/lqg/FieldRow.vue'
// ★ 显式 .vue 路径导入（SAMPLE-MP-001 坑 6：只靠 easycom 会让该模块的 .js 产物消失）
import WdInput from 'wot-design-uni/components/wd-input/wd-input.vue'
import WdPopup from 'wot-design-uni/components/wd-popup/wd-popup.vue'

// 填写页「来源单位」的底部选择面板（组织样本、类器官两张表共用；落地规范 §5.4 / §5.10）。
//
// ★ 列哪些单位由页面算好传进来（`unitOptionsFor`）：内部 = 全部启用单位；外部 = 只有本人绑定的那一个
//   （后端只收本人绑定单位的 id）。两种身份都有「列表里没有，手动填写」—— 手填只落单位名、不带 id。
// ★ 组件不改表单：选中 / 手填 / 改名都抛事件，由页面写回 id 与名字（名字是快照，导出那一列读它）。
// ★ 单位多于 6 个时顶上出搜索框（按名称包含匹配，只过滤传进来的列表，不另发请求）。
// ★ 单位格子沿用 `org__unit` / `org__unit--on` 这组类名：类器官表单抽出本组件之前就是这个 DOM，
//   doc/waves/regression 下的历史回归脚本按它定位，别改名。
const props = withDefaults(defineProps<{
  /** 可选的单位（页面按身份算好） */
  units: SelectorUnit[]
  /** 当前选中的单位 id（手填时为 null） */
  unitId?: string | number | null
  /** 当前单位名（选中时 = 单位名快照；手填时 = 手填的名字） */
  unitName?: string
  /** 是否处在「手动填写」 */
  manual?: boolean
  /** 只读 */
  disabled?: boolean
  /** 列表为空时的一句说明（外部：档案里还没绑定单位） */
  emptyHint?: string
}>(), {
  unitId: null,
  unitName: '',
  manual: false,
  disabled: false,
  emptyHint: '',
})

const emit = defineEmits<{
  (e: 'pick', unit: SelectorUnit): void
  (e: 'manual'): void
  (e: 'update:unitName', name: string): void
}>()

const show = ref(false)
const keyword = ref('')

const filtered = computed<SelectorUnit[]>(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) {
    return props.units
  }
  return props.units.filter(u => String(u.unitName || '').toLowerCase().includes(kw))
})

function open() {
  if (props.disabled) {
    return
  }
  keyword.value = ''
  show.value = true
}

function pick(unit: SelectorUnit) {
  emit('pick', unit)
  show.value = false
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
        <text class="sus__t">来源单位</text>
        <text class="sus__x" @click="show = false">{{ manual ? '完成' : '关闭' }}</text>
      </view>
      <view v-if="units.length > 6" class="lqg-filter__chip sus__search">
        <wd-input
          :model-value="keyword"
          placeholder="搜单位名称"
          no-border
          clearable
          @update:model-value="(v: string) => keyword = v"
        />
      </view>
      <view class="org__units sus__units">
        <text
          v-for="unit in filtered"
          :key="String(unit.unitId)"
          class="lqg-filter__chip org__unit sus__unit"
          :class="{ 'org__unit--on sus__unit--on': !manual && String(unitId) === String(unit.unitId) }"
          @click="pick(unit)"
        >{{ unit.unitName }}</text>
        <text
          class="lqg-filter__chip org__unit sus__unit"
          :class="{ 'org__unit--on sus__unit--on': manual }"
          @click="emit('manual')"
        >列表里没有，手动填写</text>
      </view>
      <text v-if="units.length === 0 && emptyHint" class="sus__hint">{{ emptyHint }}</text>
      <text v-else-if="units.length > 0 && filtered.length === 0" class="sus__hint">没有名称里含「{{ keyword.trim() }}」的单位，可以手动填写</text>
      <FieldRow
        v-if="manual"
        label="单位名称"
        :readonly="disabled"
        :model-value="unitName"
        :maxlength="100"
        placeholder="请填写单位名称"
        @update:model-value="(v: string) => emit('update:unitName', v)"
      />
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.sus__t {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.sus__x {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}

.sus__search {
  margin-top: var(--lqg-sp-5);
}

.sus__units {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lqg-sp-3);
  margin: var(--lqg-sp-5) 0;
}

.sus__unit {
  font-size: var(--lqg-fs-body);
}

.sus__unit--on {
  background: var(--lqg-primary-soft);
  color: var(--lqg-primary);
  font-weight: var(--lqg-fw-semibold);
  box-shadow: inset 0 0 0 1px var(--lqg-primary);
}

.sus__hint {
  display: block;
  margin-bottom: var(--lqg-sp-5);
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}
</style>
