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
// ★ 2026-09-30（Kevin 真机：「所有表格填写还是没有下划线」）—— 真正的根因在这里，不是 09-29 以为的 var()：
//   小程序里每个自定义组件外面都包着一层**宿主节点**（<field-row>），于是 `.fr` 永远是宿主里唯一的子节点，
//   `.fr:last-child { border-bottom-width: 0 }` 对**每一行**都成立 → 所有行的下划线都被去掉了。
//   H5 没有宿主节点，`.fr` 之间是真兄弟，所以 H5 上一直是好的。
//   virtualHost：不生成宿主节点，`.fr` 直接成为卡片的子节点 —— `:last-child` 这才只命中卡片最后一行。
//   （父级没有往 FieldRow 上传 class / style，关掉宿主节点不丢东西。）
defineOptions({
  options: {
    virtualHost: true,
  },
})

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
 * 长标签：**仍和值同一行**，标签字号收一档（飞书 2026-10-01 小程序行24）。
 *
 * 演变：09-30 第 13 行「琼脂糖包埋样本送样时间」左右排时最后一个字掉到第二行 → 当时改成「上下排」（标签独占一行、
 *   值在下一行）；10-01 第 24 行甲方又指出上下排也算「标题和右侧文字换行了」，要求长标题也不换行。
 * 现在：宽度按「汉字 = 1、ASCII = 0.55」估，
 *   ≤ 10 个字宽：15px（正常）；≤ 12.5：14px（如「琼脂糖包埋样本送样时间」11）；更长：13px（「-80度超低温冰箱转移至液氮时间」≈14.7）。
 *   标签区按「字宽 × 字号」给足、单行不折；值区拿剩下的，长标签那一行的值允许在「日期 / 时间」之间折成两行（只在空格处折），
 *   保证整段时间都看得见。
 * 只有多行文本（textarea）遇到长标签还上下排：wd-textarea 的标签不认自定义 class、没法缩字号（目前也没有这种字段）。
 */
const LONG_LABEL_WIDTH = 10
const BASE_FS = 15
function labelWidth(text: string): number {
  let width = 0
  for (const ch of text) {
    width += ch.charCodeAt(0) < 128 ? 0.55 : 1
  }
  return width
}
const labelW = computed(() => labelWidth(props.label))
const labelFs = computed(() => (labelW.value <= LONG_LABEL_WIDTH ? BASE_FS : labelW.value <= 12.5 ? 14 : 13))
const isLong = computed(() => labelFs.value < BASE_FS)
/** 传给 wot 组件的标题 class（全局样式见 App.vue：lqg-fr-nowrap / lqg-fr-sm / lqg-fr-xs） */
const titleClass = computed(() => `lqg-fr-nowrap${labelFs.value === 14 ? ' lqg-fr-sm' : labelFs.value === 13 ? ' lqg-fr-xs' : ''}`)
const stacked = computed(() => props.control === 'textarea' && isLong.value)

/**
 * 左右排时标签区的宽度：**按标签自身宽度给足**（字宽 + 必填星号 + 余量）× 字号，标签一律单行。
 *
 * ★ 2026-09-30 飞书「小程序」第 21 行「所有表单项的标题都不要换行」：wd-input 的标签区默认只占 33%
 *   （约 6 个半汉字），「类器官来源类型」（7 个字）第 7 个字掉到第二行；wd-cell 默认左右各一半也会折。
 *   现在所有左右排的行（输入、选择、按钮组、备注）都按这个宽度给标签区，值区拿剩下的。
 *   余量：正常标签 1 个字；长标签只留 0.6 个字（把地方让给右边的值）。
 */
const titleWidth = computed(() => {
  if (stacked.value) {
    return ''
  }
  const em = labelW.value + (props.required ? 1 : 0) + (isLong.value ? 0.6 : 1)
  return `${Math.ceil(em * labelFs.value)}px`
})

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
    custom-class="lqg-fr-cell"
    :title="label"
    :title-width="titleWidth"
    :custom-title-class="titleClass"
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
    custom-class="lqg-fr-cell"
    :title="label"
    :title-width="titleWidth"
    :custom-title-class="titleClass"
    :required="required" marker-side="after"
    clickable
    value-align="right"
    @click="onPick"
  >
    <view class="fr__val">
      <text v-if="ocrMark" class="lqg-tag lqg-tag--ocr fr__mark">识别 · 请核对</text>
      <text v-if="display" class="fr__text" :class="{ 'lqg-mono': mono, 'fr__text--one': !isLong, 'fr__text--wrap': isLong }">{{ display }}</text>
      <text v-else class="fr__ph" :class="{ 'fr__ph--sm': isLong }">{{ placeholderText }}</text>
    </view>
    <template #right-icon>
      <view class="fr__arrow">
        <text>›</text>
      </view>
    </template>
  </wd-cell>

  <!-- 多行（备注 / 情况描述…）：和其它项一样**标签在左、文字在右并靠右**，高度随内容长（最少约两行）。
       ★ 2026-09-30 飞书「小程序」第 19、20 行：原来是上下排 + 固定高的大文本框，一格占掉半屏、文字靠左，
         和别的表单项不统一。wd-textarea 自带 label（左右排），auto-height 让它跟着内容长。
       标签超长（上下排）时仍然标签一行、文本框在下一行。
       （wd-textarea 不认 custom-label-class，标签单行全靠 label-width 按字数给足。） -->
  <wd-textarea
    v-else-if="control === 'textarea' && !stacked"
    :label="label"
    :label-width="titleWidth"
    custom-textarea-class="lqg-fr-ta"
    disable-default-padding
    :required="required" marker-side="after"
    :model-value="modelValue"
    :placeholder="placeholderText"
    :maxlength="maxlength > 0 ? maxlength : 500"
    auto-height
    no-border
    @update:model-value="(v: string) => emit('update:modelValue', v)"
  />
  <wd-cell v-else-if="control === 'textarea'" :title="label" :required="required" marker-side="after" vertical>
    <wd-textarea
      :model-value="modelValue"
      :placeholder="placeholderText"
      :maxlength="maxlength > 0 ? maxlength : 500"
      custom-textarea-class="lqg-fr-ta lqg-fr-ta--left"
      disable-default-padding
      auto-height
      no-border
      @update:model-value="(v: string) => emit('update:modelValue', v)"
    />
  </wd-cell>

  <!-- 文本 / 数字：原地输入 -->
  <wd-input
    v-else
    :label="label"
    :label-width="titleWidth"
    :custom-label-class="titleClass"
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
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 只读的编号类长串（住院号等）没有空格可折：允许任意处断行，不顶出卡片（UX 测试 MP-11） */
.fr__text.lqg-mono {
  word-break: break-all;
}

/* 长标签那一行的值：只在空格处折（「2026-09-29 / 14:22」），字号与收小后的标签一致 */
.fr__text--wrap {
  font-size: 14px;
  text-align: right;
  word-break: keep-all;
}

.fr__ph {
  min-width: 0;
  overflow: hidden;
  font-size: var(--lqg-fs-title);
  color: var(--lqg-ink-3);
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 长标签那一行的占位与值同字号（UX 测试 MP-03：占位 15px 在长标签旁折行、掉单字） */
.fr__ph--sm {
  font-size: 14px;
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
