<script setup lang="ts">
import { computed, ref } from 'vue'
import LineIcon from '@/components/ui/LineIcon.vue'
import type { PersonValue, StaffOption } from '@/utils/staff'
import { EMPTY_PERSON, fetchStaffOptions, staffSheetView } from '@/utils/staff'
// ★ 显式 .vue 路径导入（SAMPLE-MP-001 坑 6：只靠 easycom 会让该模块的 .js 产物消失）
import WdInput from 'wot-design-uni/components/wd-input/wd-input.vue'
import WdPopup from 'wot-design-uni/components/wd-popup/wd-popup.vue'

// 填写页「操作人 / 包埋人 / 冻存人」的底部选人面板（CR-20261012-22；样本、类器官、石蜡包埋、冻存、两个核验页共用）。
//
// 甲方要按人统计工作量（「袁琴琴这个月包埋了多少个」）→ 这几栏不再手填，从内部人员名单里选：
//   · 列表 = 内部人员名单；搜索框只是过滤，**不能输入名单外的名字**（手填的「刘佳」「刘 佳」会被算成两个人）；
//   · 名单里没有 → 底下那行字告诉去哪加（工作台「人员与单位 → 内部人员授权」），加完重新打开面板就有；
//   · 现在的值不在名单里（历史手填的姓名 / 已撤销授权的人）也列在最后、带标注，不改就原样留着；
//   · 选填的栏可以「清空」。
// ★ 版式与种属面板（SpeciesSheet）一致；显示规则全在 `staffSheetView`（纯函数，有单测）。
// ★ 每次打开都看一眼名单要不要重取（半分钟内不重复请求）：管理员刚加的人马上选得到。
const props = withDefaults(defineProps<{
  /** 面板标题：操作人 / 包埋人 / 冻存人 */
  title?: string
  /** 现在的值 */
  modelValue?: PersonValue
  /** 只读 */
  disabled?: boolean
}>(), {
  title: '操作人',
  modelValue: () => ({ ...EMPTY_PERSON }),
  disabled: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: PersonValue): void
}>()

const show = ref(false)
const keyword = ref('')
const staff = ref<StaffOption[]>([])
const loading = ref(false)

const view = computed(() => staffSheetView(staff.value, keyword.value, props.modelValue))
const hasValue = computed(() => !!(props.modelValue?.id || props.modelValue?.name))

async function open() {
  if (props.disabled) {
    return
  }
  keyword.value = ''
  show.value = true
  loading.value = staff.value.length === 0
  staff.value = await fetchStaffOptions()
  loading.value = false
}

function pick(person: PersonValue) {
  emit('update:modelValue', { id: person.id, name: person.name })
  show.value = false
}

function clear() {
  emit('update:modelValue', { ...EMPTY_PERSON })
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
    <view class="lqg-sheet sfs">
      <view class="lqg-sheet__head">
        <text class="sfs__t">{{ title }}</text>
        <view class="sfs__acts">
          <text v-if="hasValue" class="sfs__x sfs__clear" @click="clear">清空</text>
          <text class="sfs__x" @click="show = false">取消</text>
        </view>
      </view>

      <view class="lqg-filter__chip sfs__search">
        <view class="sfs__search-icon">
          <LineIcon name="search" :size="16" />
        </view>
        <view class="sfs__input">
          <wd-input
            :model-value="keyword"
            placeholder="搜索姓名"
            no-border
            clearable
            confirm-type="search"
            :maxlength="50"
            @update:model-value="(v: string) => keyword = v"
          />
        </view>
      </view>

      <view v-if="view.rows.length" class="sfs__list">
        <view
          v-for="row in view.rows"
          :key="row.key"
          class="sfs__row"
          :class="{ 'sfs__row--on': row.selected, 'sfs__row--legacy': row.legacy }"
          @click="pick(row.person)"
        >
          <text class="sfs__v">{{ row.label }}</text>
          <view v-if="row.selected" class="sfs__check">
            <LineIcon name="check" :size="18" />
          </view>
        </view>
      </view>
      <view v-else class="sfs__empty">
        <text v-if="loading">正在取名单…</text>
        <text v-else-if="view.noMatch">名单里没有「{{ keyword.trim() }}」</text>
        <text v-else>名单里还没有人</text>
      </view>

      <text class="sfs__hint">名单里没有？请管理员在网页工作台「人员与单位 → 内部人员授权」里先加上</text>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.sfs__t {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.sfs__acts {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-5);
}

.sfs__x {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}

.sfs__clear {
  color: var(--lqg-primary);
}

.sfs__search {
  margin-top: var(--lqg-sp-5);
}

.sfs__search-icon {
  flex: none;
  color: var(--lqg-ink-3);
}

/* 输入框包一层自己的 view 吃满剩余宽度（wot 组件在小程序里不吃页面的 scoped 样式，别往它身上挂 class） */
.sfs__input {
  flex: 1;
  min-width: 0;
}

/* 人多了也不撑破弹层（落地规范 §5.10：弹层不超过屏高三分之二）：列表最高 45vh，超出在卡片里滚 */
.sfs__list {
  margin-top: var(--lqg-sp-4);
  max-height: 45vh;
  overflow-x: hidden;
  overflow-y: auto;
  background: var(--lqg-card);
  border-radius: var(--lqg-radius-ctl);
  box-shadow: var(--lqg-shadow-sm);
}

/* 一行 = 一个可点的人；行高 48，点击区够大（UX 测试 MP-09 的口径） */
.sfs__row {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-3);
  min-height: 48px;
  padding: 0 var(--lqg-sp-5);
  border-top: 1px solid var(--lqg-line);
}

.sfs__row:first-child {
  border-top: none;
}

.sfs__v {
  flex: 1;
  min-width: 0;
  font-size: var(--lqg-fs-title);
  color: var(--lqg-ink);
  word-break: break-all;
}

.sfs__row--on .sfs__v {
  color: var(--lqg-primary);
  font-weight: var(--lqg-fw-semibold);
}

.sfs__check {
  flex: none;
  color: var(--lqg-primary);
}

.sfs__empty {
  margin-top: var(--lqg-sp-4);
  padding: var(--lqg-sp-6) var(--lqg-sp-5);
  text-align: center;
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
  background: var(--lqg-card);
  border-radius: var(--lqg-radius-ctl);
}

.sfs__hint {
  display: block;
  margin-top: var(--lqg-sp-4);
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}
</style>
