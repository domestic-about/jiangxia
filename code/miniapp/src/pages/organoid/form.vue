<script setup lang="ts">
import { computed, ref } from 'vue'
import type { SampleDetail } from '@/api/sample'
import {
  createIntSample,
  fetchExtSampleDetail,
  fetchIntSampleDetail,
  updateIntSample,
} from '@/api/sample'
import type { SelectorUnit } from '@/api/unit-group'
import { fetchUnits } from '@/api/unit-group'
import ErrorState from '@/components/lqg/ErrorState.vue'
import FieldRow from '@/components/lqg/FieldRow.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import NoteBar from '@/components/lqg/NoteBar.vue'
import SegButtons from '@/components/lqg/SegButtons.vue'
import StatusChip from '@/components/lqg/StatusChip.vue'
import { goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
import { unitDisplay } from '@/utils/ext-profile'
import WdDatetimePicker from 'wot-design-uni/components/wd-datetime-picker/wd-datetime-picker.vue'
import WdPopup from 'wot-design-uni/components/wd-popup/wd-popup.vue'
import type { OrganoidFormValue } from './api'
import {
  createExtOrganoid,
  emptyOrganoidForm,
  externalOrganoidPayload,
  fetchOrganoidHints,
  internalOrganoidPayload,
  toOrganoidFormValue,
  updateExtOrganoid,
} from './api'
import type { OrganoidFieldKey, OrganoidFieldSpec, OrganoidMode } from './layout'
import {
  RECEIVE_FIELDS,
  fieldSpecs,
  hasReceiveGroup,
  normalizeMode,
  organoidLayout,
} from './layout'

// 类器官收样记录 · 填写页（UI:mp.organoid.form）· SAMPLE-MP-002。
//
// 三种模式**由入口决定**（同样本记录信息表）：
//   首页点表进来            → mode=new（内部七项 / 外部三项）
//   「历史编辑记录」点一条  → mode=edit（外部改自己的待核验 / 无效；内部改有效样本）
//   「内部管理」点一行      → mode=view（**一律只读**）
//
// 布局只认 `organoidLayout(identity, verifyStatus, mine, mode)` 这个纯函数（fixture 驱动）：
//   - 外部**只渲染三项**、收样段连标题都不出现（不是置灰）；
//   - 内部看外部送来的待核验 = 只读，且**只读页也没有「修改」**；
//   - 只读页右上角的「修改」= 把 mode 换成 `edit` 重算同一个纯函数，算出来可改才显示
//     —— 「按钮显不显示」与「能不能改」同源（CR-20260918-07）；
//   - 身份缺失什么都不渲染。
definePage({
  style: {
    navigationBarTitleText: '类器官收样记录',
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
/** 来源单位的选择面板（底部弹层）与「手填」开关 */
const unitSheet = ref(false)
const manualUnit = ref(false)
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
const specs = computed<OrganoidFieldSpec[]>(() => fieldSpecs(layout.value, editable.value))
const sendSpecs = computed(() => specs.value.filter(s => !(RECEIVE_FIELDS as readonly string[]).includes(s.key)))
const receiveSpecs = computed(() => specs.value.filter(s => (RECEIVE_FIELDS as readonly string[]).includes(s.key)))
const showReceive = computed(() => hasReceiveGroup(layout.value))

// ★ 只读页右上角的「修改」：把 mode 换成 edit **重算同一个纯函数**。
//   外部送来还没核验的样本算出来是 false → 这一页连「修改」都不出现
//   （核验在工作台，绕不过去）。
const canEditFromView = computed(() => mode.value === 'view'
  && organoidLayout(identity.value, verifyStatus.value, mine.value, 'edit').editable)

const topNote = computed(() => {
  if (mode.value !== 'edit' || editable.value) {
    return ''
  }
  if (isInternal.value) {
    return '核验与改判请到网页工作台'
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
    await loadUnits()
    if (mode.value === 'new') {
      form.value = emptyOrganoidForm()
      if (isInternal.value) {
        // 内部新增：操作人默认带当前登录人（可改）
        form.value.operatorName = store.name || ''
      }
      else {
        // 外部新增：来源单位默认带档案里的单位（UI:mp.organoid.form）
        form.value.sourceUnitId = store.ext?.unitId ?? null
        form.value.sourceUnitName = unitDisplay(store.ext)
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
    manualUnit.value = form.value.sourceUnitId === null
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
  if (spec.control === 'select') {
    manualUnit.value = form.value.sourceUnitId === null
    unitSheet.value = true
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

// 选了列表里的单位：**id 与名字一起带上**（名字是快照，导出那一列读的就是它）
function pickUnit(unit: SelectorUnit) {
  form.value.sourceUnitId = unit.unitId
  form.value.sourceUnitName = unit.unitName
  manualUnit.value = false
  unitSheet.value = false
}

// 「列表里没有，手动填写」：清掉 id，只留名字（后端按名字落快照）
function pickManualUnit() {
  form.value.sourceUnitId = null
  manualUnit.value = true
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
  if (!form.value.organoidType.trim()) {
    uni.showToast({ title: '请填类器官类型', icon: 'none' })
    return
  }
  if (!form.value.sourceUnitName.trim()) {
    uni.showToast({ title: '请选择来源单位', icon: 'none' })
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
      // ★ 修改模式走 PUT（不是再 POST 一次 —— 会撞内部编号唯一性）
      await updateIntSample({ id: organoidId.value, ...internalOrganoidPayload(form.value) })
      uni.showToast({ title: '已保存', icon: 'none' })
    }
    else {
      await updateExtOrganoid(organoidId.value, externalOrganoidPayload(form.value))
      uni.showToast({ title: '已保存', icon: 'none' })
    }
    setTimeout(() => goPage('/pages/history/index'), 600)
  }
  catch (e) {
    if (e instanceof Error && e.message) {
      uni.showToast({ title: e.message, icon: 'none' })
    }
  }
  finally {
    saving.value = false
  }
}
</script>

<template>
  <view class="org">
    <LoadingState v-if="loading" />

    <ErrorState v-else-if="failed" text="没能加载这条类器官收样记录" @retry="load" />

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
          :required="spec.key === 'organoidType'"
          :model-value="fieldValue(spec.key)"
          :placeholder="spec.key === 'organoidType' && hints.length ? `${hints[0]} 等` : '请填写'"
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
            :required="spec.key === 'receiveDate' || spec.key === 'internalNo'"
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

    <wd-popup
      v-model="unitSheet"
      position="bottom"
      custom-style="border-radius: var(--lqg-radius-sheet) var(--lqg-radius-sheet) 0 0"
      @close="unitSheet = false"
    >
      <view class="lqg-sheet">
        <view class="lqg-sheet__head">
          <text class="org__sheet-t">来源单位</text>
          <text class="org__sheet-x" @click="unitSheet = false">关闭</text>
        </view>
        <view class="org__units">
          <text
            v-for="unit in units"
            :key="unit.unitId"
            class="lqg-filter__chip org__unit"
            :class="{ 'org__unit--on': String(form.sourceUnitId) === String(unit.unitId) }"
            @click="pickUnit(unit)"
          >{{ unit.unitName }}</text>
          <text
            class="lqg-filter__chip org__unit"
            :class="{ 'org__unit--on': manualUnit }"
            @click="pickManualUnit"
          >列表里没有，手动填写</text>
        </view>
        <FieldRow
          v-if="manualUnit"
          label="单位名称"
          :readonly="!editable"
          :model-value="form.sourceUnitName"
          placeholder="请填写单位名称"
          @update:model-value="(v: string) => form.sourceUnitName = v"
        />
      </view>
    </wd-popup>

    <wd-datetime-picker
      ref="pickerRef"
      v-model="pickerValue"
      :type="pickerField === 'processTime' ? 'datetime' : 'date'"
      title="选择时间"
      @confirm="onPicked"
    />
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

.org__sheet-t {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.org__sheet-x {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}

.org__units {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lqg-sp-3);
  margin: var(--lqg-sp-5) 0;
}

.org__unit {
  font-size: var(--lqg-fs-body);
}

.org__unit--on {
  background: var(--lqg-primary-soft);
  color: var(--lqg-primary);
  font-weight: var(--lqg-fw-semibold);
  box-shadow: inset 0 0 0 1px var(--lqg-primary);
}
</style>
