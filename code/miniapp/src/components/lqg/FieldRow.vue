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

/**
 * 标签太长时「上下排」：标签独占一行，值（和 ›）在下一行靠右。
 *
 * ★ 2026-09-30 飞书「小程序」第 13 行：「琼脂糖包埋样本送样时间」左右排时最后一个「间」字单独掉到第二行，
 *   「-80度超低温冰箱转移至液氮时间」也被折成两行。左右排时标签区只有约 10 个汉字宽，
 *   而值一旦选好（`2026-09-29 14:22`）也要占住右边，两边都不能挤 —— 所以超过这个宽度就换成上下排。
 * 宽度按「汉字 = 1、ASCII = 0.55」估（`-80` 这类比汉字窄），阈值 10：
 *   琼脂糖包埋样本时间（9）照旧左右排；琼脂糖包埋样本送样时间（11）、-80…液氮时间（≈14.7）上下排。
 * 按钮组（seg）不参与：它右边的按钮本身就窄，「暂存-80度超低温冰箱」左右排放得下。
 */
const LONG_LABEL_WIDTH = 10
function labelWidth(text: string): number {
  let width = 0
  for (const ch of text) {
    width += ch.charCodeAt(0) < 128 ? 0.55 : 1
  }
  return width
}
const stacked = computed(() => props.control !== 'seg' && labelWidth(props.label) > LONG_LABEL_WIDTH)

function onPick() {
  if (props.readonly) {
    return
  }
  emit('pick')
}
</script>

<template>
  <!-- ★ 2026-09-28（Kevin 要求）：每一项一条**很浅的下划线**。
       为什么要包一层：下面四支各是 wot 组件自己的根元素（wd-cell / wd-input），
       父级的 scoped 样式够不到组件内部，所以线画在这层 .fr 上；线色用设计 token 里那条
       「cell 分隔线」--lqg-line(#e2e9ea)，与表格行线同一档，不抢视觉。 -->
  <view class="fr">
  <!-- 只读 / 按钮组：一个普通 cell，值在右 -->
  <wd-cell
    v-if="readonly || control === 'seg'"
    :title="label"
    :required="required" marker-side="after"
    :vertical="stacked"
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
    :vertical="stacked"
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
  </view>
</template>

<style lang="scss" scoped>
/* 每项一条浅下划线；最后一行不画（否则与卡片底边叠成双线）
 *
 * ★ 2026-09-29：Kevin 报「真机上完全看不出来」。产物本身是对的
 *   （`<view class="fr">` + `.fr[data-v]{border-bottom:1px solid var(--lqg-line)}` 都在），
 *   所以问题出在 mp 端两个已知坑，这里一并规避：
 *     ① **不把 var() 放进简写属性**：`border-bottom: 1px solid var(--lqg-line)` 在部分小程序基础库上
 *        整条不生效（拆成 width/style/color 三个长写法最稳）；
 *     ② **不依赖 CSS 变量穿透组件边界**：本组件是自定义组件（styleIsolation 默认 isolated），
 *        `--lqg-line` 定义在 page 上，能否继承进来跟基础库有关 —— 直接用字面色最稳
 *        （值 = design-authority §B 的 `--lqg-line` #e2e9ea，这里**略深一档** #dfe6e7，
 *         因为原值在真机白底上确实几乎看不见）。 */
.fr {
  border-bottom-width: 1px;
  border-bottom-style: solid;
  border-bottom-color: #dfe6e7;
}

.fr:last-child {
  border-bottom-width: 0;
}

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
