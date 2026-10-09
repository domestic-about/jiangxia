<script setup lang="ts">
import { computed, ref } from 'vue'
import type { SampleDetail } from '@/api/sample'
import {
  createIntSample,
  fetchExtSampleDetail,
  fetchIntSampleDetail,
  sourceUnitIdFor,
  updateIntSample,
} from '@/api/sample'
import type { SelectorUnit } from '@/api/unit-group'
import { boundUnitOf, fetchUnits, unitOptionsFor } from '@/api/unit-group'
import ErrorState from '@/components/lqg/ErrorState.vue'
import FieldRow from '@/components/lqg/FieldRow.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import NoteBar from '@/components/lqg/NoteBar.vue'
import SegButtons from '@/components/lqg/SegButtons.vue'
import SourceUnitSheet from '@/components/lqg/SourceUnitSheet.vue'
import SpeciesSheet from '@/components/lqg/SpeciesSheet.vue'
import StatusChip from '@/components/lqg/StatusChip.vue'
import { finishTo, goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
import { unitDisplay } from '@/utils/ext-profile'
import { DEFAULT_SPECIES, fetchSpeciesOptions, speciesProblem } from '@/utils/species'
import WdDatetimePicker from 'wot-design-uni/components/wd-datetime-picker/wd-datetime-picker.vue'
import type { OrganoidFormValue } from './api'
import {
  createExtOrganoid,
  emptyOrganoidForm,
  externalOrganoidPayload,
  fetchOrganoidHints,
  internalOrganoidPatch,
  internalOrganoidPayload,
  toOrganoidFormValue,
  updateExtOrganoid,
} from './api'
import type { OrganoidFieldKey, OrganoidFieldSpec, OrganoidMode } from './layout'
import { useLeaveGuard } from '@/utils/leaveGuard'
import {
  RECEIVE_FIELDS,
  fieldMaxlength,
  fieldSpecs,
  hasReceiveGroup,
  normalizeMode,
  organoidLayout,
  passageProblem,
} from './layout'

// 类器官收样记录 · 填写页（UI:mp.organoid.form）· SAMPLE-MP-002。
//
// 三种模式**由入口决定**（同样本记录信息表）：
//   首页点表进来            → mode=new（内部八项 / 外部四项；「代数」是 2026-09-24 甲方要加的，紧跟类器官类型）
//   「历史编辑记录」点一条  → mode=edit（外部改自己的待核验 / 无效；内部改有效样本）
//   「内部管理」点一行      → mode=view（**一律只读**）
//
// 布局只认 `organoidLayout(identity, verifyStatus, mine, mode)` 这个纯函数（fixture 驱动）：
//   - 外部**只渲染四项**（来源单位、类器官类型、代数、备注）、收样段连标题都不出现（不是置灰）；
//   - 内部看外部送来的待核验 = 只读，且**只读页也没有「修改」**；
//   - 只读页右上角的「修改」= 把 mode 换成 `edit` 重算同一个纯函数，算出来可改才显示
//     —— 「按钮显不显示」与「能不能改」同源（CR-20260918-07）；
//   - 身份缺失什么都不渲染。
definePage({
  style: {
    navigationBarTitleText: '类器官送样记录',
  },
})

const store = useUserStore()

const organoidId = ref('')
const mode = ref<OrganoidMode>('view')
const loading = ref(true)
const failed = ref(false)
const saving = ref(false)
const detail = ref<Partial<SampleDetail> | null>(null)
const form = ref<OrganoidFormValue>(emptyOrganoidForm())
const hints = ref<string[]>([])
const units = ref<SelectorUnit[]>([])
/** 来源单位的选择面板（底部弹层，`SourceUnitSheet`）与「手填」开关 */
const unitSheetRef = ref<{ open: () => void } | null>(null)
const manualUnit = ref(false)
/** 种属（CR-20261009-18）：字典常用值 + 底部选择面板（`SpeciesSheet`，可手填） */
const speciesOptions = ref<string[]>([...DEFAULT_SPECIES])
const speciesSheetRef = ref<{ open: () => void } | null>(null)
/** 日期 / 时间控件的目标字段、回填毫秒值与组件实例 */
const pickerField = ref<OrganoidFieldKey>('receiveDate')
const pickerValue = ref<number>(Date.now())
// ★ wot-design-uni 1.14 的 `wd-datetime-picker` **没有 `visible` 这个 prop**（D2 r1 L2 S0-3）：
//   面板开关是组件内部的 `popupShow`，对外只暴露 `open()` / `close()`。持实例、点字段时调 `open()`。
const pickerRef = ref<{ open: () => void } | null>(null)

onLoad((options) => {
  organoidId.value = String(options?.id ?? '')
  mode.value = normalizeMode(options?.mode)
  load()
})

const identity = computed(() => store.identity)
const isInternal = computed(() => identity.value === 'internal')
/** 新增时这条记录就是「我」要建的；有详情时按后端行上的 `mine` */
const mine = computed(() => (mode.value === 'new' ? true : detail.value?.mine === true))
const verifyStatus = computed(() => detail.value?.verifyStatus ?? null)

/** 布局（纯函数）：渲染哪些字段、能不能改 */
const layout = computed(() => organoidLayout(identity.value, verifyStatus.value, mine.value, mode.value))
const editable = computed(() => layout.value.editable)
// 有没保存的改动时按返回先问一句（UX 测试 MP-04）：加载完、可写时记基线
const leave = useLeaveGuard(() => form.value, () => !loading.value && !failed.value && editable.value)
const specs = computed<OrganoidFieldSpec[]>(() => fieldSpecs(layout.value, editable.value))
const sendSpecs = computed(() => specs.value.filter(s => !(RECEIVE_FIELDS as readonly string[]).includes(s.key)))
const receiveSpecs = computed(() => specs.value.filter(s => (RECEIVE_FIELDS as readonly string[]).includes(s.key)))
const showReceive = computed(() => hasReceiveGroup(layout.value))

// ★ 只读页右上角的「修改」：把 mode 换成 edit **重算同一个纯函数**。
//   外部送来还没核验的样本算出来是 false → 这一页连「修改」都不出现
//   （核验走核验页，绕不过去）。
const canEditFromView = computed(() => mode.value === 'view'
  && organoidLayout(identity.value, verifyStatus.value, mine.value, 'edit').editable)

const topNote = computed(() => {
  if (mode.value !== 'edit' || editable.value) {
    return ''
  }
  if (isInternal.value) {
    // 待核验 / 无效的外部记录在这一页只读；核验走核验页（甲方 2026-09-24 第 20 行），改判仍在工作台
    return '核验请从首页「待处理」进入，改判请到网页工作台'
  }
  return '这条记录现在不能修改'
})

// 内部修改模式顶部小字：最后修改：某某 · 时间。
// 判据是 `updateTime` 非空（= 这一行被改过），不是 `updateByName` 非空
// —— 从没改过的行也可能有 `updateByName`（SAMPLE-MP-001 坑 1）。
const lastModified = computed(() => {
  if (mode.value !== 'edit' || !isInternal.value || !detail.value) {
    return ''
  }
  const when = detail.value.updateTime
  if (!when) {
    return ''
  }
  return `最后修改：${detail.value.updateByName || '—'} · ${when}`
})

async function load() {
  loading.value = true
  failed.value = false
  try {
    // ★ 身份的唯一来源是 `/mp/me`：本页可能是冷启动直接进来的（H5 深链 / 小程序分享），
    //   这时 store 里还没有 me —— 不先拉一次，布局就是空布局，内外部分支根本没机会跑。
    if (!store.me) {
      await store.loadMe()
    }
    const [, species] = await Promise.all([loadUnits(), fetchSpeciesOptions()])
    speciesOptions.value = species
    if (mode.value === 'new') {
      form.value = emptyOrganoidForm()
      if (isInternal.value) {
        // 内部新增：操作人默认带当前登录人（可改）
        form.value.operatorName = store.name || ''
      }
      else {
        // 外部新增：来源单位默认带档案里**绑定**的单位（待核验 / 已核验才算；UI:mp.organoid.form）；
        // 只有自填单位名的按手填（不带 id —— 后端只收本人绑定单位的 id）
        const bound = boundUnitOf(store.ext)
        form.value.sourceUnitId = bound ? bound.unitId : null
        form.value.sourceUnitName = bound ? bound.unitName : unitDisplay(store.ext)
        manualUnit.value = !bound && !!form.value.sourceUnitName
      }
      await loadHints()
      return
    }
    if (!organoidId.value) {
      failed.value = true
      return
    }
    const data = isInternal.value
      ? await fetchIntSampleDetail(organoidId.value)
      : await fetchExtSampleDetail(organoidId.value)
    detail.value = data
    form.value = toOrganoidFormValue(data)
    // ★ V01 同口径：外部详情的 VO 不带 sourceUnitId → 这里按名字对回 id，**只在面板能选的单位里对**
    //   （外部 = 本人绑定的那一个；内部 = 全部启用单位）。否则外部改后重提会把库里原有的单位 id 洗成空。
    //   名字对不上任何单位才算「手动填写」。
    if (form.value.sourceUnitId === null) {
      form.value.sourceUnitId = sourceUnitIdFor(
        form.value.sourceUnitName,
        unitOptions.value.map(unit => ({ id: unit.unitId, name: unit.unitName })),
      )
    }
    manualUnit.value = form.value.sourceUnitId === null && !!form.value.sourceUnitName.trim()
    await loadHints()
  }
  catch {
    failed.value = true
  }
  finally {
    loading.value = false
  }
}

/** 类器官类型联想词（字典接口；拉不到不挡填写） */
async function loadHints() {
  try {
    hints.value = await fetchOrganoidHints()
  }
  catch {
    hints.value = []
  }
}

/** 来源单位选择器的候选（内外部都能拉：`/mp/ext/units` 是共用选择器，AUTH-GROUP-001） */
async function loadUnits() {
  try {
    units.value = await fetchUnits()
  }
  catch {
    units.value = []
  }
}

const hasOptions = [
  { value: 'Y', label: '有' },
  { value: 'N', label: '无' },
]

/** 输入框占位：类器官类型给字典里的第一个联想词；代数给格式示例（选填） */
function placeholderOf(key: OrganoidFieldKey): string | undefined {
  if (key === 'organoidType' && hints.value.length) {
    return `${hints.value[0]} 等`
  }
  if (key === 'passage') {
    return '选填，如 P3'
  }
  return undefined
}

function fieldValue(key: OrganoidFieldKey): string {
  return (form.value as unknown as Record<string, string>)[key] ?? ''
}

function setField(key: OrganoidFieldKey, value: string) {
  ;(form.value as unknown as Record<string, string>)[key] = value
}

/** 日期 / 时间控件要的是毫秒时间戳（输入输出两侧都要转） */
function toMs(value: string): number {
  if (!value) {
    return Date.now()
  }
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const ms = new Date(normalized).getTime()
  return Number.isNaN(ms) ? Date.now() : ms
}

function onPick(spec: OrganoidFieldSpec) {
  if (!editable.value) {
    return
  }
  if (spec.key === 'species') {
    speciesSheetRef.value?.open()
    return
  }
  if (spec.control === 'select') {
    unitSheetRef.value?.open()
    return
  }
  pickerField.value = spec.key
  pickerValue.value = toMs(fieldValue(spec.key))
  pickerRef.value?.open()
}

function onPicked(event: { value: number | string }) {
  const ms = Number(event.value)
  if (Number.isNaN(ms)) {
    return
  }
  const key = pickerField.value
  setField(key, formatMs(ms, key === 'processTime' ? 'datetime' : 'date'))
}

/** 毫秒 → `yyyy-MM-dd`（日期）/ `yyyy-MM-dd HH:mm:ss`（时间），与后端 `@JsonFormat` 同形 */
function formatMs(ms: number, type: 'date' | 'datetime'): string {
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  const date = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  if (type === 'date') {
    return date
  }
  return `${date} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/**
 * 来源单位面板里列哪些单位（与后端同口径，V01）：内部 = 全部启用单位；
 * 外部 = **只有本人绑定的那一个**（后端只收本人绑定单位的 id，列全部单位会让人选到别的单位被 400）。
 */
const unitOptions = computed<SelectorUnit[]>(() => unitOptionsFor(store.identity, units.value, store.ext))

/** 外部档案里还没有绑定单位时，面板里给的一句话 */
const unitEmptyHint = computed(() => (isInternal.value ? '' : '档案里还没有绑定单位：可以手动填写，也可以先到「我的 → 单位与组别」补充'))

// 选了列表里的单位：**id 与名字一起带上**（名字是快照，导出那一列读的就是它）
function pickUnit(unit: SelectorUnit) {
  form.value.sourceUnitId = unit.unitId
  form.value.sourceUnitName = unit.unitName
  manualUnit.value = false
}

// 「列表里没有，手动填写」：清掉 id，只留名字（后端按名字落快照）
function pickManualUnit() {
  form.value.sourceUnitId = null
  manualUnit.value = true
}

/** 外部还没有来源单位（新用户、档案里没绑定单位）：可以手填，或去「我的 → 单位与组别」补充 */
function guideToUnitGroup() {
  uni.showModal({
    title: '还没有来源单位',
    content: '可以在「来源单位」里手动填写；也可以先到「我的 → 单位与组别」补充单位与组别，之后提交会自动带上。',
    confirmText: '去补充',
    cancelText: '知道了',
    success: (res) => {
      if (res.confirm) {
        goPage('/pages/me/unit-group')
      }
    },
  })
}

/** 只读页右上角的「修改」：切成修改模式，同一个纯函数重算可写性 */
function toEdit() {
  if (!canEditFromView.value) {
    return
  }
  mode.value = 'edit'
}

async function submit() {
  if (!editable.value || saving.value) {
    return
  }
  // 种属（CR-20261009-18）：两类、内外部都必填
  const speciesError = speciesProblem(form.value.species)
  if (speciesError) {
    uni.showToast({ title: speciesError, icon: 'none' })
    return
  }
  if (!form.value.organoidType.trim()) {
    uni.showToast({ title: '请填类器官类型', icon: 'none' })
    return
  }
  // 代数选填；填了就要形如 P3（后端同一规则兜底，这里先给人话提示，免得提交后才看到 400）
  const passageHint = passageProblem(form.value.passage)
  if (passageHint) {
    uni.showToast({ title: passageHint, icon: 'none' })
    return
  }
  if (!form.value.sourceUnitName.trim()) {
    if (isInternal.value) {
      uni.showToast({ title: '请选择来源单位', icon: 'none' })
    }
    else {
      guideToUnitGroup()
    }
    return
  }
  if (isInternal.value) {
    if (!form.value.receiveDate) {
      uni.showToast({ title: '请填收样日期', icon: 'none' })
      return
    }
    if (!form.value.internalNo.trim()) {
      uni.showToast({ title: '内部编号必填', icon: 'none' })
      return
    }
  }
  saving.value = true
  // 成功后按钮一直禁用到离开本页（UX 测试 MP-01：原来 finally 先复位，600ms 空窗里连点会重复建一条）
  let done = false
  try {
    if (mode.value === 'new') {
      if (isInternal.value) {
        await createIntSample(internalOrganoidPayload(form.value))
      }
      else {
        await createExtOrganoid(externalOrganoidPayload(form.value))
      }
      uni.showToast({ title: '已提交', icon: 'none' })
    }
    else if (isInternal.value) {
      // ★ 修改模式走 PUT（不是再 POST 一次 —— 会撞内部编号唯一性）。
      //   且**不带 sampleKind**：类别是这条记录的类目身份，不是可改字段（issue #105）——
      //   带上它会让「点错行进来保存」把一条组织样本静默改判成类器官（后端同口径 400 兜底）。
      await updateIntSample({ id: organoidId.value, ...internalOrganoidPatch(form.value) })
      uni.showToast({ title: '已保存', icon: 'none' })
    }
    else {
      await updateExtOrganoid(organoidId.value, externalOrganoidPayload(form.value))
      uni.showToast({ title: '已保存', icon: 'none' })
    }
    done = true
    leave.release()
    setTimeout(() => finishTo('/pages/history/index?tab=organoid'), 600)
  }
  catch (e) {
    // 外部新用户撞上后端「来源单位不能为空」：换成去「我的 → 单位与组别」的引导
    if (!isInternal.value && e instanceof Error && /来源单位.*(不能为空|为空|必填)/.test(e.message)) {
      guideToUnitGroup()
      return
    }
    if (e instanceof Error && e.message) {
      uni.showToast({ title: e.message, icon: 'none' })
    }
  }
  finally {
    if (!done) {
      saving.value = false
    }
  }
}
</script>

<template>
  <view class="org">
    <LoadingState v-if="loading" />

    <ErrorState v-else-if="failed" text="没能加载这条类器官送样记录" @retry="load" />

    <view v-else-if="specs.length === 0" class="lqg-state">
      <text class="lqg-state__text">没能确认你的身份，请重新登录后再试</text>
    </view>

    <template v-else>
      <view v-if="mode === 'view'" class="lqg-sec org__sec">
        <StatusChip :value="verifyStatus" />
        <text v-if="canEditFromView" class="org__edit" @click="toEdit">修改</text>
      </view>

      <text v-if="lastModified" class="org__meta">{{ lastModified }}</text>
      <NoteBar v-if="topNote" tone="warn" :text="topNote" />

      <view class="lqg-gl">送检信息</view>
      <view class="org__group">
        <FieldRow
          v-for="spec in sendSpecs"
          :key="spec.key"
          :label="spec.label"
          :control="spec.control"
          :readonly="!spec.editable"
          :required="spec.key === 'organoidType' || spec.key === 'sourceUnitName' || spec.key === 'species'" marker-side="after"
          :maxlength="fieldMaxlength(spec.key)"
          :model-value="fieldValue(spec.key)"
          :placeholder="placeholderOf(spec.key)"
          @update:model-value="(v: string) => setField(spec.key, v)"
          @pick="onPick(spec)"
        >
          <SegButtons
            v-if="spec.control === 'seg'"
            :options="hasOptions"
            :model-value="fieldValue(spec.key)"
            :disabled="!spec.editable"
            @update:model-value="(v: string) => setField(spec.key, v)"
          />
        </FieldRow>
      </view>

      <template v-if="showReceive">
        <view class="lqg-gl">收样信息</view>
        <view class="org__group">
          <FieldRow
            v-for="spec in receiveSpecs"
            :key="spec.key"
            :label="spec.label"
            :control="spec.control"
            :readonly="!spec.editable"
            :required="spec.key === 'receiveDate' || spec.key === 'internalNo'" marker-side="after"
            :mono="spec.key === 'internalNo'"
            :maxlength="fieldMaxlength(spec.key)"
            :model-value="fieldValue(spec.key)"
            @update:model-value="(v: string) => setField(spec.key, v)"
            @pick="onPick(spec)"
          >
            <SegButtons
              v-if="spec.control === 'seg'"
              :options="hasOptions"
              :model-value="fieldValue(spec.key)"
              :disabled="!spec.editable"
              @update:model-value="(v: string) => setField(spec.key, v)"
            />
          </FieldRow>
        </view>
      </template>

      <view class="lqg-bar-spacer" />
    </template>

    <view v-if="!loading && !failed && specs.length > 0" class="lqg-bar org__bar">
      <button v-if="editable" class="org__btn" :disabled="saving" @click="submit">
        {{ mode === 'new' ? '提交' : '保存' }}
      </button>
    </view>

    <!-- 种属：底部弹框（CR-20261009-18）—— 一个输入框既搜常用值、又能直接「使用」列表里没有的 -->
    <SpeciesSheet
      ref="speciesSheetRef"
      :model-value="form.species"
      :options="speciesOptions"
      :disabled="!editable"
      @update:model-value="(v: string) => setField('species', v)"
    />

    <!-- 来源单位：底部弹框（内部 = 全部启用单位；外部 = 本人绑定的单位；都能手填） -->
    <SourceUnitSheet
      ref="unitSheetRef"
      :units="unitOptions"
      :unit-id="form.sourceUnitId"
      :unit-name="form.sourceUnitName"
      :manual="manualUnit"
      :disabled="!editable"
      :empty-hint="unitEmptyHint"
      @pick="pickUnit"
      @manual="pickManualUnit"
      @update:unit-name="(v: string) => form.sourceUnitName = v"
    />

    <wd-datetime-picker
      ref="pickerRef"
      v-model="pickerValue"
      :type="pickerField === 'processTime' ? 'datetime' : 'date'"
      title="选择时间"
      @confirm="onPicked"
    >
      <!-- ★ 给默认插槽放一个空节点（G12）：没有默认插槽时 wd-datetime-picker 会自己渲染一行
           「值 ›」的 cell，页面底部就多出一行没有标签的「今天日期 ›」。面板开关只靠 open()。 -->
      <view />
    </wd-datetime-picker>
  </view>
</template>

<style lang="scss" scoped>
.org {
  padding: var(--lqg-sp-5) 0 0;
}

.org__sec {
  padding-top: 0;
}

.org__edit {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-primary);
}

.org__group {
  margin: 0 var(--lqg-gutter);
  overflow: hidden;
  border-radius: var(--lqg-radius-card);
  background: var(--lqg-card);
  box-shadow: var(--lqg-shadow-sm);
}

.org__meta {
  display: block;
  margin: var(--lqg-sp-5) var(--lqg-gutter) 0;
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.org__bar {
  display: flex;
  align-items: center;
}

.org__btn {
  flex: 1;
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

.org__btn::after {
  border: none;
}

</style>
