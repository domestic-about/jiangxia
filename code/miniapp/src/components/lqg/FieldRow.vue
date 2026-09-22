<script setup lang="ts">
import { computed } from 'vue'
// ★ 直接 .vue 路径导入（SYS-MP-001 的纪律），**同时**绕开 easycom 在这一页的产物问题：
// 实测「页面 / 组件里只靠 easycom 引用 wd-*」时，该模块的 .js 产物会消失
//（只剩 .json/.wxml，页面加载不到逻辑）——见完工报告「坑与解法」。
import WdCell from 'wot-design-uni/components/wd-cell/wd-cell.vue'
import WdInput from 'wot-design-uni/components/wd-input/wd-input.vue'
import WdTextarea from 'wot-design-uni/components/wd-textarea/wd-textarea.vue'

// FieldRow（落地规范 §5.4 Cell 表单 + §5.5 按钮组）：
// 标签在左、值右对齐，行高至少 52，可点项右侧 `›`（颜色走 `--lqg-ink-3`）。
//
// 三种呈现方式（`control`）：
//   - 文本 / 数字：`wd-input align-right`（原地输入）
//   - 日期 / 时间 / 选择（`select`）：不可输入，点一下由父级弹面板（§5.4：日期与选择走底部弹框）
//   - 按钮组：值插槽里放 `SegButtons`（二态 / 三态），本组件让出右侧空间
//   - 多行：`wd-textarea`（备注）
// `readonly` 时不渲染任何输入控件，只显示纯文本 —— 只读页与「不可改的字段」共用这一支。
// ★ `const props =` 不能省（D2 r1 L2 S0-2）：只写 `withDefaults(defineProps…)` 时编译器
//   不生成运行时 `props` 变量，下面 `display` 引用 `props.modelValue` 会抛 ReferenceError
//   → readonly 分支一个字段值都渲染不出来。
const props = withDefaults(defineProps<{
  label: string
  /** v-model 的值（按钮组 / 日期也走同一份字符串值） */
  modelValue?: string
  /** 控件类型 */
  control?: 'text' | 'digit' | 'date' | 'datetime' | 'seg' | 'textarea' | 'select'
  /** 只读：不渲染输入控件，值以纯文本呈现 */
  readonly?: boolean
  /** 占位提示 */
  placeholder?: string
  /** 必填小星号 */
  required?: boolean
  /**
   * 「识别 · 请核对」小标（OCR-MP-001）：这一项是被这次识别**预填**的。
   * 用户改动该项后由页面把它从 `marks` 里摘掉（§5.7：在值左侧、改动后消失）。
   */
  ocrMark?: boolean
}>(), {
  modelValue: '',
  control: 'text',
  readonly: false,
  placeholder: '请填写',
  required: false,
  ocrMark: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  /** 日期 / 时间 / 选择类控件被点了，由父级决定弹哪个面板 */
  (e: 'pick'): void
}>()

const display = computed(() => (props.modelValue === '' ? '' : props.modelValue))
</script>

<template>
  <!-- 只读：一个普通 cell，值在右 -->
  <wd-cell
    v-if="readonly || control === 'seg'"
    :title="label"
    :required="required"
    :clickable="!readonly && control !== 'seg'"
    value-align="right"
    @click="!readonly && control !== 'seg' && emit('pick')"
  >
    <template v-if="control === 'seg'">
      <view class="fr__val">
        <slot />
        <text v-if="ocrMark" class="lqg-tag lqg-tag--ocr fr__mark">识别 · 请核对</text>
      </view>
    </template>
    <template v-else>
      <view class="fr__val">
        <text class="fr__text">{{ display || '—' }}</text>
        <text v-if="ocrMark" class="lqg-tag lqg-tag--ocr fr__mark">识别 · 请核对</text>
      </view>
      <text v-if="!readonly && control !== 'text'" class="fr__arrow">›</text>
    </template>
  </wd-cell>

  <!-- 多行备注 -->
  <wd-cell v-else-if="control === 'textarea'" :title="label" :required="required" vertical>
    <wd-textarea
      :model-value="modelValue"
      :placeholder="placeholder"
      :maxlength="200"
      no-border
      @update:model-value="(v: string) => emit('update:modelValue', v)"
    />
  </wd-cell>

  <!-- 文本 / 数字：原地输入 -->
  <wd-input
    v-else
    :label="label"
    :type="control === 'digit' ? 'digit' : 'text'"
    align-right
    :required="required"
    :placeholder="placeholder"
    :model-value="modelValue"
    @update:model-value="(v: string) => emit('update:modelValue', v)"
    @click="control === 'date' || control === 'datetime' || control === 'select' ? emit('pick') : undefined"
  >
    <!-- 「识别 · 请核对」小标：wd-input 的 suffix 槽正好在值右边（§5.7） -->
    <template v-if="ocrMark" #suffix>
      <text class="lqg-tag lqg-tag--ocr fr__mark">识别 · 请核对</text>
    </template>
  </wd-input>
</template>

<style lang="scss" scoped>
.fr__val {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--lqg-sp-3);
  min-width: 0;
}

.fr__text {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink);
}

.fr__mark {
  flex: none;
}

.fr__arrow {
  margin-left: var(--lqg-sp-3);
  color: var(--lqg-ink-3);
}
</style>
