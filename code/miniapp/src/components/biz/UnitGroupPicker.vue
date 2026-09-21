<script setup lang="ts">
import type { SelectorGroup, SelectorUnit } from '@/api/unit-group'

// UnitGroupPicker（UI:mp.me.profile，AUTH-GROUP-001）
//
// 单位 → 组别**联动**：选了单位才出组别的选项；每个选择器的末项是
// 「列表里没有，手动填写」——选中后出对应名字的输入框。
//
// 两套写法互斥（后端 PUT /mp/ext/profile 的口径）：
//   选了列表项 → 传 unitId + groupId（自填名清空）
//   手动填写   → 传 unitNameInput + groupNameInput（两个 id 清空）
// 组件对外的 modelValue 是**一个合并对象**，由页面决定怎么提交。
const props = withDefaults(defineProps<{
  /** 可选的启用单位（含各自启用的组别） */
  units: SelectorUnit[]
  /** 已选单位 id（v-model:unitId） */
  unitId?: string | number | null
  /** 已选组别 id（v-model:groupId） */
  groupId?: string | number | null
  /** 自填的单位名（v-model:unitNameInput） */
  unitNameInput?: string | null
  /** 自填的组别名（v-model:groupNameInput） */
  groupNameInput?: string | null
}>(), {
  unitId: null,
  groupId: null,
  unitNameInput: '',
  groupNameInput: '',
})

const emit = defineEmits<{
  (e: 'update:unitId', value: string | number | null): void
  (e: 'update:groupId', value: string | number | null): void
  (e: 'update:unitNameInput', value: string): void
  (e: 'update:groupNameInput', value: string): void
}>()

/** 手动填写哨兵值（不会与真 id 撞：id 都是数字 / 数字串） */
const MANUAL = '__manual__'

const unitSheet = ref(false)
const groupSheet = ref(false)

/** 自填模式：选了哨兵项之后保持住；外部传入的 unitId 为空也算（历史自填档案） */
const unitManual = ref(false)
const groupManual = ref(false)

const currentUnit = computed(() => props.units.find(u => String(u.unitId) === String(props.unitId)) || null)

const groups = computed<SelectorGroup[]>(() => currentUnit.value?.groups ?? [])

const unitLabel = computed(() => {
  if (unitManual.value || !props.unitId) {
    return props.unitNameInput || ''
  }
  return currentUnit.value?.unitName || ''
})

const groupLabel = computed(() => {
  if (groupManual.value || !props.groupId) {
    return props.groupNameInput || ''
  }
  return groups.value.find(g => String(g.groupId) === String(props.groupId))?.groupName || ''
})

watch(() => props.unitId, (value) => {
  if (value) {
    unitManual.value = false
  }
})

watch(() => props.groupId, (value) => {
  if (value) {
    groupManual.value = false
  }
})

// ★ 进入页面时按已有档案初始化「手动填写」模式：
// 档案是自填的（没有 unit_id / group_id，但有名字）→ 必须直接出两个输入框并回填名字。
// 不初始化的话，被驳回的人回来只看到「请选择单位」，原来填过的名字不见了（实测踩过）。
watch([() => props.unitId, () => props.unitNameInput, () => props.groupNameInput], () => {
  if (!props.unitId && !!props.unitNameInput) {
    unitManual.value = true
  }
  if (!props.groupId && !!props.groupNameInput) {
    groupManual.value = true
  }
}, { immediate: true })

function openUnitSheet() {
  unitSheet.value = true
}

function openGroupSheet() {
  // 单位还没选（也没在自填）时先选单位：组别是随单位联动的
  if (!props.unitId && !unitManual.value) {
    uni.showToast({ title: '请先选择单位', icon: 'none' })
    unitSheet.value = true
    return
  }
  groupSheet.value = true
}

function pickUnit(unit: SelectorUnit) {
  unitManual.value = false
  emit('update:unitId', unit.unitId)
  // 换单位 = 原来选的组别不再成立（后端要求 group 必须属于 unit）
  emit('update:groupId', null)
  emit('update:unitNameInput', '')
  emit('update:groupNameInput', '')
  unitSheet.value = false
  // 换单位后如果原来那个组别不属于新单位，顺手把组别面板也关掉（避免两个面板叠着）
  groupSheet.value = false
}

function pickManualUnit() {
  unitManual.value = true
  groupManual.value = true
  emit('update:unitId', null)
  emit('update:groupId', null)
  // 手动填写模式：组别没有「列表联动」可谈，把两个面板都关掉
  // （留着一个打开的面板会挡住页面底部的保存按钮 —— H5 实测点不动「保存」）
  unitSheet.value = false
  groupSheet.value = false
}

function pickGroup(group: SelectorGroup) {
  groupManual.value = false
  emit('update:groupId', group.groupId)
  emit('update:groupNameInput', '')
  groupSheet.value = false
}

function pickManualGroup() {
  groupManual.value = true
  emit('update:groupId', null)
  groupSheet.value = false
}

function onUnitInput(event: { detail: { value: string } }) {
  emit('update:unitNameInput', event.detail.value)
}

function onGroupInput(event: { detail: { value: string } }) {
  emit('update:groupNameInput', event.detail.value)
}
</script>

<template>
  <view class="picker">
    <!-- 单位 -->
    <view class="picker__field" @click="openUnitSheet">
      <text class="picker__label">单位</text>
      <view class="picker__value">
        <text v-if="unitLabel" class="picker__text">{{ unitLabel }}</text>
        <text v-else class="picker__ph">请选择单位</text>
        <text class="picker__arrow">›</text>
      </view>
    </view>

    <!-- 组别（随单位联动） -->
    <view class="picker__field picker__field--line" @click="openGroupSheet">
      <text class="picker__label">组别</text>
      <view class="picker__value">
        <text v-if="groupLabel" class="picker__text">{{ groupLabel }}</text>
        <text v-else class="picker__ph">{{ currentUnit || unitManual ? '请选择组别' : '先选单位' }}</text>
        <text class="picker__arrow">›</text>
      </view>
    </view>

    <!-- 手动填写：单位 / 组别名 -->
    <view v-if="unitManual" class="picker__field picker__field--line picker__field--input">
      <text class="picker__label">单位名</text>
      <input class="picker__input" :value="unitNameInput || ''" placeholder="列表里没有，请填写单位全称" maxlength="100" @input="onUnitInput" />
    </view>
    <view v-if="unitManual" class="picker__field picker__field--line picker__field--input">
      <text class="picker__label">组别名</text>
      <input class="picker__input" :value="groupNameInput || ''" placeholder="请填写组别名" maxlength="100" @input="onGroupInput" />
    </view>

    <!-- 单位选择面板 -->
    <!-- ★ z-index 必须显式抬起来：wd-popup 默认 10，会被页面底部的固定条（`.lqg-bar` 的保存按钮）
         盖住 —— H5 实测「面板开着但点不动选项」（保存按钮 intercepts pointer events）。
         ★ 不用 root-portal：H5 上它让面板不跟随 v-model 关闭（实测：选完单位后面板还在，
         下一次点「组别」时两个面板叠着）。小程序上固定的底部弹层用 wd-popup 自带的 fixed 就够。 -->
    <wd-popup v-model="unitSheet" position="bottom" :z-index="1000" safe-area-inset-bottom custom-style="border-radius: 16px 16px 0 0;">
      <view class="sheet">
        <view class="sheet__title">选择单位</view>
        <scroll-view class="sheet__list" scroll-y>
          <view
            v-for="u in units"
            :key="u.unitId"
            class="sheet__item"
            :class="{ 'sheet__item--on': !unitManual && String(unitId) === String(u.unitId) }"
            @click="pickUnit(u)"
          >
            <text>{{ u.unitName }}</text>
          </view>
          <view class="sheet__item sheet__item--manual" :class="{ 'sheet__item--on': unitManual }" @click="pickManualUnit">
            <text>列表里没有，手动填写</text>
          </view>
          <view v-if="units.length === 0" class="sheet__empty">
            <text>还没有可选单位，请手动填写</text>
          </view>
        </scroll-view>
      </view>
    </wd-popup>

    <!-- 组别选择面板（只列该单位下启用的组别） -->
    <wd-popup v-model="groupSheet" position="bottom" :z-index="1000" safe-area-inset-bottom custom-style="border-radius: 16px 16px 0 0;">
      <view class="sheet">
        <view class="sheet__title">选择组别</view>
        <view class="sheet__sub">{{ currentUnit?.unitName || unitNameInput }}</view>
        <scroll-view class="sheet__list" scroll-y>
          <view
            v-for="g in groups"
            :key="g.groupId"
            class="sheet__item"
            :class="{ 'sheet__item--on': !groupManual && String(groupId) === String(g.groupId) }"
            @click="pickGroup(g)"
          >
            <text>{{ g.groupName }}</text>
          </view>
          <view v-if="groups.length === 0 && currentUnit" class="sheet__empty">
            <text>该单位下还没有组别</text>
          </view>
          <view class="sheet__item sheet__item--manual" :class="{ 'sheet__item--on': groupManual }" @click="pickManualGroup">
            <text>列表里没有，手动填写</text>
          </view>
        </scroll-view>
      </view>
    </wd-popup>
  </view>
</template>

<style lang="scss" scoped>
.picker {
  background: var(--lqg-card);
  border-radius: var(--lqg-radius-card);
  box-shadow: var(--lqg-shadow-sm);
  overflow: hidden;
}

.picker__field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lqg-sp-5);
  min-height: var(--lqg-cell-h);
  padding: var(--lqg-sp-5) var(--lqg-sp-6);
}

.picker__field--line {
  border-top: 1px solid var(--lqg-line);
}

.picker__field--input {
  align-items: center;
}

.picker__label {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-2);
  flex: none;
}

.picker__value {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-3);
  min-width: 0;
  flex: 1;
  justify-content: flex-end;
}

.picker__text {
  font-size: var(--lqg-fs-title);
  color: var(--lqg-ink);
  text-align: right;
}

.picker__ph {
  font-size: var(--lqg-fs-title);
  color: var(--lqg-ink-3);
}

.picker__arrow {
  font-size: var(--lqg-fs-lg);
  color: var(--lqg-ink-3);
}

.picker__input {
  flex: 1;
  min-width: 0;
  text-align: right;
  font-size: var(--lqg-fs-title);
  color: var(--lqg-ink);
}

.sheet {
  padding: var(--lqg-sp-6) var(--lqg-sp-6) calc(var(--lqg-sp-6) + env(safe-area-inset-bottom));
}

.sheet__title {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
  text-align: center;
  margin-bottom: var(--lqg-sp-4);
}

.sheet__sub {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
  text-align: center;
  margin-bottom: var(--lqg-sp-4);
}

.sheet__list {
  max-height: 52vh;
}

.sheet__item {
  min-height: var(--lqg-cell-h);
  display: flex;
  align-items: center;
  padding: 0 var(--lqg-sp-5);
  font-size: var(--lqg-fs-title);
  color: var(--lqg-ink);
  border-radius: var(--lqg-radius-seg);
}

.sheet__item--on {
  background: var(--lqg-primary-soft);
  color: var(--lqg-primary);
  font-weight: var(--lqg-fw-semibold);
}

.sheet__item--manual {
  margin-top: var(--lqg-sp-3);
  border-top: 1px solid var(--lqg-line);
  color: var(--lqg-primary);
}

.sheet__empty {
  padding: var(--lqg-sp-6) var(--lqg-sp-5);
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
  text-align: center;
}
</style>
