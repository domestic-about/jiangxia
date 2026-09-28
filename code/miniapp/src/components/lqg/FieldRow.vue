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
// 呈现方式（`control`）：
//   - 文本 / 数字：`wd-input align-right`（原地输入）
//   - 日期 / 时间 / 选择（`select`）：**只读格子**，值在右、右侧 `›`，点一下由父级弹底部面板
//     （§5.4：日期、选择、选样本一律底部弹框）。这一支**不渲染任何输入控件**、也**不发
//     `update:modelValue`** —— 值只能从面板回填（V25：以前是可打字的 wd-input，
//     石蜡包埋页在「选择样本」那格打字会把输入的文字当成 sampleId 写进表单）。
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
  /** 占位提示；不传时按控件给：可输入的「请填写」、弹面板的「请选择」 */
  placeholder?: string
  /** 必填小星号 */
  required?: boolean
  /** 值是编号类（内部编号、送检单号、石蜡块编号、住院号……）：等宽字体（落地规范 §3） */
  mono?: boolean
  /**
   * 最多能输入几个字（与后端 / 库里的列长同一口径，由页面按字段传）；不传：单行不限、多行 500。
   * ★ 小程序原生 input 不设时默认只收 140 个字，而且超长的值后端直接 400 —— 所以按列长给。
   */
  maxlength?: number
  /**
   * 「识别 · 请核对」小标（OCR-MP-001）：这一项是被这次识别**预填**的。
   * 用户改动该项后由页面把它从 `marks` 里摘掉（§5.7：在值左侧、改动后消失）。
   */
  ocrMark?: boolean
}>(), {
  modelValue: '',
  control: 'text',
  readonly: false,
  placeholder: '',
  required: false,
  mono: false,
  maxlength: 0,
  ocrMark: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  /** 日期 / 时间 / 选择类控件被点了，由父级决定弹哪个面板 */
  (e: 'pick'): void
}>()

// 时间（`yyyy-MM-dd HH:mm:ss`）只显示到分钟：面板本来就只选到分钟，秒恒为 00；
// 值本身不动（提交的仍是原串），只是格子里少占一截、不在窄屏上折成两行。
const display = computed(() => {
  const value = props.modelValue ?? ''
  if (props.control === 'datetime') {
    const hit = /^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})(:\d{2})?$/.exec(value)
    if (hit) {
      return `${hit[1]} ${hit[2]}`
    }
  }
  return value
})

/** 日期 / 时间 / 选择：点开弹底部面板的那一类（只读格子 + `›`） */
const isPicker = computed(() => props.control === 'date' || props.control === 'datetime' || props.control === 'select')

const placeholderText = computed(() => props.placeholder || (isPicker.value ? '请选择' : '请填写'))

function onPick() {
  if (props.readonly) {
    return
  }
  emit('pick')
}
</script>

<template>
  <!-- 只读 / 按钮组：一个普通 cell，值在右 -->
  <wd-cell
    v-if="readonly || control === 'seg'"
    :title="label"
    :required="required" marker-side="after"
    value-align="right"
  >
    <view class="fr__val">
      <slot v-if="control === 'seg'" />
      <text v-else class="fr__text" :class="{ 'lqg-mono': mono && !!display }">{{ display || '—' }}</text>
      <text v-if="ocrMark" class="lqg-tag lqg-tag--ocr fr__mark">识别 · 请核对</text>
    </view>
  </wd-cell>

  <!-- 日期 / 时间 / 选择：只读格子，右侧 ›，点一下弹面板（不可打字） -->
  <wd-cell
    v-else-if="isPicker"
    :title="label"
    :required="required" marker-side="after"
    clickable
    value-align="right"
    @click="onPick"
  >
    <view class="fr__val">
      <text v-if="ocrMark" class="lqg-tag lqg-tag--ocr fr__mark">识别 · 请核对</text>
      <text v-if="display" class="fr__text fr__text--one" :class="{ 'lqg-mono': mono }">{{ display }}</text>
      <text v-else class="fr__ph">{{ placeholderText }}</text>
    </view>
    <template #right-icon>
      <view class="fr__arrow">
        <text>›</text>
      </view>
    </template>
  </wd-cell>

  <!-- 多行备注 -->
  <wd-cell v-else-if="control === 'textarea'" :title="label" :required="required" marker-side="after" vertical>
    <wd-textarea
      :model-value="modelValue"
      :placeholder="placeholderText"
      :maxlength="maxlength > 0 ? maxlength : 500"
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
    :required="required" marker-side="after"
    :placeholder="placeholderText"
    :maxlength="maxlength > 0 ? maxlength : -1"
    :custom-input-class="mono ? 'lqg-mono' : ''"
    :model-value="modelValue"
    @update:model-value="(v: string) => emit('update:modelValue', v)"
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

/* cell 的值与标签同为 15（落地规范 §2「cell 标签与值 15」，= wd-input 里输入的字号） */
.fr__text {
  font-size: var(--lqg-fs-title);
  color: var(--lqg-ink);
}

.fr__text--one {
  white-space: nowrap;
}

.fr__ph {
  font-size: var(--lqg-fs-title);
  color: var(--lqg-ink-3);
}

.fr__mark {
  flex: none;
}

.fr__arrow {
  display: flex;
  align-items: center;
  flex: none;
  margin-left: var(--lqg-sp-3);
  font-size: var(--lqg-fs-lg);
  color: var(--lqg-ink-3);
}
</style>
