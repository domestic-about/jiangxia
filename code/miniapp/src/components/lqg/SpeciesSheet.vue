<script setup lang="ts">
import { computed, ref } from 'vue'
import LineIcon from '@/components/ui/LineIcon.vue'
import { SPECIES_MAX, speciesChoices, speciesOnConfirm, speciesSheetView } from '@/utils/species'
// ★ 显式 .vue 路径导入（SAMPLE-MP-001 坑 6：只靠 easycom 会让该模块的 .js 产物消失）
import WdInput from 'wot-design-uni/components/wd-input/wd-input.vue'
import WdPopup from 'wot-design-uni/components/wd-popup/wd-popup.vue'

// 填写页「种属」的底部选择面板（CR-20261009-18；组织样本、类器官、核验页共用）。
//
// 甲方：「比如说人啊、鼠兔、移植猪、鸡。但是呢，它还可以添加其他的。」
// ★ 一个输入框既是搜索、也是「添加其他的」：不分「选」与「手填」两种模式 ——
//   · 不输入：下面一列常用值（字典 lqg_species，页面拉好传进来），点一行就选中并收起；
//   · 输入了字：列表按「包含」过滤；输入的字不是任何一行时，顶上出「使用「…」」，点它（或键盘「完成」）就用这几个字。
//   当前值是手填的也列在里面（打勾），再打开时看得见它。显示规则全在 `speciesSheetView`（纯函数，有单测）。
// ★ 版式沿用「选择样本」面板（SamplePicker）：顶部搜索框 + 白底列表；选中项青绿字 + 右侧勾（落地规范 §5.10）。
// ★ 打开时不自动弹键盘：常用值点一下就完事，键盘只在要搜 / 要添加时才出来。
const props = withDefaults(defineProps<{
  /** 当前种属 */
  modelValue?: string
  /** 字典里的常用值（页面拉好传进来；空就用内置四个） */
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
const keyword = ref('')

const view = computed(() => speciesSheetView(speciesChoices(props.options), keyword.value, props.modelValue))

function open() {
  if (props.disabled) {
    return
  }
  keyword.value = ''
  show.value = true
}

function pick(value: string) {
  emit('update:modelValue', value)
  show.value = false
}

/** 键盘「完成」：有「使用」行就用输入的字，正好对上 / 只剩一行就用那一行，否则不动 */
function onConfirm() {
  const value = speciesOnConfirm(view.value, keyword.value)
  if (value) {
    pick(value)
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
    <view class="lqg-sheet sps">
      <view class="lqg-sheet__head">
        <text class="sps__t">种属</text>
        <text class="sps__x" @click="show = false">取消</text>
      </view>

      <view class="lqg-filter__chip sps__search">
        <view class="sps__search-icon">
          <LineIcon name="search" :size="16" />
        </view>
        <view class="sps__input">
          <wd-input
            :model-value="keyword"
            placeholder="搜索，或输入列表里没有的种属"
            no-border
            clearable
            confirm-type="done"
            :maxlength="SPECIES_MAX"
            @update:model-value="(v: string) => keyword = v"
            @confirm="onConfirm"
          />
        </view>
      </view>

      <view class="sps__list">
        <view v-if="view.createValue" class="sps__row sps__row--create" @click="pick(view.createValue)">
          <view class="sps__lead">
            <LineIcon name="plus" :size="16" />
          </view>
          <text class="sps__v">使用「{{ view.createValue }}」</text>
        </view>
        <view
          v-for="row in view.rows"
          :key="row.value"
          class="sps__row"
          :class="{ 'sps__row--on': row.selected }"
          @click="pick(row.value)"
        >
          <text class="sps__v">{{ row.value }}</text>
          <view v-if="row.selected" class="sps__check">
            <LineIcon name="check" :size="18" />
          </view>
        </view>
      </view>

      <text v-if="!keyword.trim()" class="sps__hint">列表里没有的，在上面输入后点「使用」</text>
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

.sps__search {
  margin-top: var(--lqg-sp-5);
}

.sps__search-icon {
  flex: none;
  color: var(--lqg-ink-3);
}

/* 输入框包一层自己的 view 吃满剩余宽度（wot 组件在小程序里不吃页面的 scoped 样式，别往它身上挂 class） */
.sps__input {
  flex: 1;
  min-width: 0;
}

/* 字典里加多了也不撑破弹层（落地规范 §5.10：弹层不超过屏高三分之二）：列表最高 45vh，超出在卡片里滚 */
.sps__list {
  margin-top: var(--lqg-sp-4);
  max-height: 45vh;
  overflow-x: hidden;
  overflow-y: auto;
  background: var(--lqg-card);
  border-radius: var(--lqg-radius-ctl);
  box-shadow: var(--lqg-shadow-sm);
}

/* 一行 = 一个可点的种属；行高 48，点击区够大（UX 测试 MP-09 的口径） */
.sps__row {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-3);
  min-height: 48px;
  padding: 0 var(--lqg-sp-5);
  border-top: 1px solid var(--lqg-line);
}

.sps__row:first-child {
  border-top: none;
}

.sps__v {
  flex: 1;
  min-width: 0;
  font-size: var(--lqg-fs-title);
  color: var(--lqg-ink);
  word-break: break-all;
}

.sps__row--on .sps__v {
  color: var(--lqg-primary);
  font-weight: var(--lqg-fw-semibold);
}

.sps__check {
  flex: none;
  color: var(--lqg-primary);
}

.sps__row--create .sps__v,
.sps__lead {
  color: var(--lqg-primary);
}

.sps__lead {
  flex: none;
}

.sps__hint {
  display: block;
  margin-top: var(--lqg-sp-4);
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}
</style>
