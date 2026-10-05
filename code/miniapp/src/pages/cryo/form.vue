<script setup lang="ts">
import { computed, ref } from 'vue'
import type { CryoBatchRow, CryoFormValue } from '@/api/cryo'
import {
  createIntCryo,
  cryoFormProblem,
  cryoPatch,
  cryoPayload,
  emptyCryoForm,
  fetchIntCryoDetail,
  toCryoFormValue,
  updateIntCryo,
} from '@/api/cryo'
import type { SampleRow } from '@/api/sample'
import { fetchIntSampleDetail } from '@/api/sample'
import ErrorState from '@/components/lqg/ErrorState.vue'
import FieldRow from '@/components/lqg/FieldRow.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import NoteBar from '@/components/lqg/NoteBar.vue'
import SamplePicker from '@/components/lqg/SamplePicker.vue'
import SegButtons from '@/components/lqg/SegButtons.vue'
import { finishTo } from '@/router/config'
import { useUserStore } from '@/store/user'
// ★ 一律显式 import wd-* 的 .vue（SAMPLE-MP-001 坑 6：只靠 easycom 会静默丢掉该模块的 .js 产物）
import WdCell from 'wot-design-uni/components/wd-cell/wd-cell.vue'
import WdDatetimePicker from 'wot-design-uni/components/wd-datetime-picker/wd-datetime-picker.vue'
import { useLeaveGuard } from '@/utils/leaveGuard'

// -80 冻存记录 · 填写页（UI:mp.cryo.form）· CRYO-MP-001。
//
// 三种模式**由入口决定**：
//   首页点「-80 冻存记录」/ 样本页「加冻存」 → mode=new
//   「我的 → 历史编辑记录」点一行               → mode=edit
//   内部管理「-80 冻存」批次详情右上角「修改」   → mode=edit（CR-20260918-07 的第二条路）
//   （mode=view 只有深链才会到：改仍从这一页右上角的「修改」走，与别的表同形）
//
// 六件最容易做反的事：
//   1. **修改模式全部可改，含冻存数量/支（= 初始支数）**；改小到让某一步剩余为负时**后端**拒绝，
//      前端把提示原样显示，**不自己算**（`cryoFormProblem` 只管「填没填、格式对不对」）。
//   2. **内部人员改谁录的都行，不限本人录的**（CR-20260918-07）—— 不按 `mine` 收窄。
//   3. 「选择样本」只列**已核验有效**的样本（`SamplePicker` 打 `/mp/int/sample/list?verifyStatus=valid`）。
//   4. 选了样本后用**「内部编号-」预填**冻存样品名称，用户可以手改（系统不解析名称）。
//   5. 代数是**单独一栏**：固定 `P` + 数字键盘（页面里就拼成 `P3` 再提交）。
//   6. 日期控件的值是**毫秒时间戳**、后端要 `yyyy-MM-dd`（两侧都要转；1.14 的
//      `wd-datetime-picker` **没有 `visible` prop**，持实例调 `open()`）。
definePage({
  style: {
    navigationBarTitleText: '-80 冻存记录',
  },
})

type CryoMode = 'new' | 'edit' | 'view'

const store = useUserStore()

const cryoId = ref('')
/** 从样本填写页「加冻存」带过来的样本 id（`?mode=new&sampleId=`） */
const incomingSampleId = ref('')
const mode = ref<CryoMode>('view')
const loading = ref(true)
const failed = ref(false)
const saving = ref(false)
const detail = ref<Partial<CryoBatchRow> | null>(null)
const form = ref<CryoFormValue>(emptyCryoForm())
const pickerValue = ref<number>(Date.now())
/** 日期面板这次给哪一格选：冻存时间 / 转移至液氮时间（两格共用一个面板） */
const pickerField = ref<'freezeTime' | 'toLn2Time'>('freezeTime')
const pickerRef = ref<{ open: () => void } | null>(null)
const samplePickerRef = ref<{ open: () => void } | null>(null)

/** 入口模式归一化：只认三个字面量，其余（含 undefined / ''）一律只读 */
function normalizeMode(raw: unknown): CryoMode {
  return raw === 'new' || raw === 'edit' || raw === 'view' ? raw : 'view'
}

onLoad((options) => {
  cryoId.value = String(options?.id ?? '')
  incomingSampleId.value = String(options?.sampleId ?? '')
  mode.value = normalizeMode(options?.mode)
  load()
})

const isInternal = computed(() => store.identity === 'internal')
/** 可写 = 新增 / 修改两种模式（只读模式一律不可写，改的话先点右上角「修改」） */
const editable = computed(() => isInternal.value && mode.value !== 'view')
// 有没保存的改动时按返回先问一句（UX 测试 MP-04）：加载完、可写时记基线
const leave = useLeaveGuard(() => form.value, () => !loading.value && !failed.value && editable.value)
/** 只读页右上角「修改」：内部人员对冻存记录都能改，不限本人录的（CR-20260918-07） */
const showEditEntry = computed(() => isInternal.value && mode.value === 'view')

/** 「暂存 -80 度超低温冰箱」两个按钮（是 / 否；UI:mp.cryo.form） */
const flagOptions = [
  { value: 'Y', label: '是' },
  { value: 'N', label: '否' },
]

/** 选「否」（直接进液氮）或已登记转液氮时，液氮储存位置必填（与后端同一条） */
const needLn2Location = computed(() => form.value.inMinus80 === 'N' || !!form.value.toLn2Time)

/**
 * 代数那一栏显示的**数字部分**：页面固定一个 `P` 前缀，输入框只吃数字（数字键盘）。
 * 提交时拼回 `P3` —— `passage` 在库里 / 接口上就是这个形状（`^P\d{1,3}$`）。
 */
const passageDigits = computed(() => form.value.passage.replace(/^P/i, ''))

/** 修改模式顶部小字：判据是 `updateTime` 非空（= 这一行被改过），不是 `updateByName` 非空 */
const lastModified = computed(() => {
  if (mode.value !== 'edit' || !detail.value) {
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
    // ★ 身份的唯一来源是 `/mp/me`：本页可能是冷启动直接进来的（H5 深链 / 小程序分享）
    if (!store.me) {
      await store.loadMe()
    }
    if (!isInternal.value) {
      return
    }
    if (mode.value === 'new') {
      form.value = emptyCryoForm()
      // 冻存人默认带当前登录人（可改）
      form.value.frozenBy = store.name || ''
      if (incomingSampleId.value) {
        await prefillSample(incomingSampleId.value)
      }
      return
    }
    if (!cryoId.value) {
      failed.value = true
      return
    }
    const data = await fetchIntCryoDetail(cryoId.value)
    detail.value = data
    form.value = toCryoFormValue(data)
  }
  catch {
    failed.value = true
  }
  finally {
    loading.value = false
  }
}

/**
 * 从样本填写页带进来的样本（`?sampleId=`）：读它的详情，把样本与冻存样品名称填好。
 * 拉不到不算错（样本可能刚好被改判无效 / 删了）—— 用户还能自己点「选择样本」重选。
 */
async function prefillSample(sampleId: string) {
  try {
    const sample = await fetchIntSampleDetail(sampleId)
    onSamplePicked(sample)
  }
  catch {
    // 忽略：下面「选择样本」那一格仍是空的，用户可以自己选
  }
}

/**
 * 选了样本（只列已核验有效的样本，筛选在后端）：记样本，并用**「内部编号-」预填**冻存样品名称。
 * 用户已经手填过名称就不覆盖。
 */
function onSamplePicked(sample: SampleRow) {
  form.value.sampleId = String(sample.id)
  form.value.sampleLabel = [String(sample.internalNo || ''), String(sample.submitNo || '')]
    .filter(Boolean)
    .join(' · ')
  if (!form.value.cryoName.trim()) {
    const internalNo = String(sample.internalNo || '').trim()
    if (internalNo) {
      form.value.cryoName = `${internalNo}-`
    }
  }
}

function openSamplePicker() {
  if (!editable.value) {
    return
  }
  samplePickerRef.value?.open()
}

/** 日期控件的输入值是毫秒时间戳 */
function toMs(value: string): number {
  if (!value) {
    return Date.now()
  }
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const ms = new Date(normalized).getTime()
  return Number.isNaN(ms) ? Date.now() : ms
}

function openDatePicker(field: 'freezeTime' | 'toLn2Time') {
  if (!editable.value) {
    return
  }
  pickerField.value = field
  // 转液氮时间还没填时，面板从冻存时间那天起（不早于冻存时间），冻存时间也没填就从今天起
  pickerValue.value = toMs(form.value[field] || (field === 'toLn2Time' ? form.value.freezeTime : ''))
  pickerRef.value?.open()
}

/** 毫秒 → `yyyy-MM-dd`（后端 `freezeTime` 是 LocalDate） */
function formatMs(ms: number): string {
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function onPicked(event: { value: number | string }) {
  const ms = Number(event.value)
  if (Number.isNaN(ms)) {
    return
  }
  form.value[pickerField.value] = formatMs(ms)
}

/** 代数：输入框只收 1~3 位数字，拼回 `P3` */
function onPassageInput(e: unknown) {
  const raw = String((e as { detail?: { value?: string } })?.detail?.value ?? '')
  const digits = raw.replace(/\D/g, '').slice(0, 3)
  form.value.passage = digits ? `P${digits}` : ''
}

/** 冻存数量 / 支：输入框只收数字（正整数由 `cryoFormProblem` 与后端一起把） */
function onInitQtyInput(value: string) {
  form.value.initQty = String(value || '').replace(/\D/g, '')
}

/** 只读页右上角的「修改」：把 mode 切成 edit */
function toEdit() {
  if (!showEditEntry.value) {
    return
  }
  mode.value = 'edit'
}

async function submit() {
  if (!editable.value || saving.value) {
    return
  }
  const problem = cryoFormProblem(form.value)
  if (problem) {
    uni.showToast({ title: problem, icon: 'none' })
    return
  }
  saving.value = true
  // 成功后按钮一直禁用到离开本页（UX 测试 MP-01：原来 finally 先复位，600ms 空窗里连点会重复建一条）
  let done = false
  try {
    if (mode.value === 'new') {
      await createIntCryo(cryoPayload(form.value))
      uni.showToast({ title: '已提交', icon: 'none' })
    }
    else {
      // ★ 保存 = PUT（patch），**不再 POST 一次** —— 那会新增一条批次
      await updateIntCryo(cryoPatch(cryoId.value, form.value))
      uni.showToast({ title: '已保存', icon: 'none' })
    }
    // ★ 被拒时（例如冻存数量改小到某一步剩余为负）后端回 400，request 层已经把后端原话
    //   toast 出来了，这里不再自己拼一句判据 —— 前端不写第二份「剩余不为负」。
    done = true
    leave.release()
    setTimeout(() => finishTo('/pages/history/index?tab=cryo'), 600)
  }
  catch (e) {
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
  <view class="cryo">
    <LoadingState v-if="loading" />

    <ErrorState v-else-if="failed" text="没能加载这条冻存记录" @retry="load" />

    <!-- 外部身份是确认了的，只是这张表不对外（UX 测试 MP-16：原来也说「没能确认你的身份」，把人引去反复登录） -->
    <view v-else-if="store.identity === 'external'" class="lqg-state">
      <text class="lqg-state__text">-80 冻存记录只给中心内部人员开放</text>
    </view>

    <view v-else-if="!isInternal" class="lqg-state">
      <text class="lqg-state__text">没能确认你的身份，请重新登录后再试</text>
    </view>

    <template v-else>
      <view v-if="mode === 'view'" class="lqg-sec cryo__sec">
        <text class="lqg-sec__t">冻存记录</text>
        <text v-if="showEditEntry" class="cryo__edit" @click="toEdit">修改</text>
      </view>

      <text v-if="lastModified" class="cryo__meta">{{ lastModified }}</text>

      <NoteBar v-if="mode !== 'new'" text="取走、补入：在「内部管理 → -80 冻存」里点这一批登记" />

      <!-- 字段先后照甲方 -80 冻存模板（2026-09-24「请参照我发你的模板，理解先后顺序」）：
           冻存时间、冻存样品、冻存数量/支、冻存密度、暂存-80、冻存人、转移至液氮时间、液氮储存位置、备注；
           模板没有的「代数」放在最后。「选择样本」是挂样本用的，放在最前（选了才能预填样品名称）。 -->
      <view class="cryo__group">
        <FieldRow
          label="选择样本"
          control="select"
          required
          mono
          :readonly="!editable"
          :model-value="form.sampleLabel"
          placeholder="选有效样本"
          @pick="openSamplePicker"
        />

        <FieldRow
          label="冻存时间"
          control="date"
          required
          :readonly="!editable"
          :model-value="form.freezeTime"
          placeholder="请选择日期"
          @pick="openDatePicker('freezeTime')"
        />

        <FieldRow
          label="冻存样品名称"
          required
          mono
          :maxlength="100"
          :readonly="!editable"
          :model-value="form.cryoName"
          placeholder="选样本后自动填"
          @update:model-value="(v: string) => form.cryoName = v"
        />

        <FieldRow
          v-if="editable"
          label="冻存数量/支"
          control="digit"
          required
          :model-value="form.initQty"
          placeholder="正整数"
          @update:model-value="onInitQtyInput"
        />
        <wd-cell v-else title="冻存数量/支" value-align="right">
          <text class="cryo__ro">{{ form.initQty || '—' }}</text>
        </wd-cell>

        <FieldRow
          label="冻存密度"
          :maxlength="50"
          :readonly="!editable"
          :model-value="form.density"
          placeholder="例如 2e5"
          @update:model-value="(v: string) => form.density = v"
        />

        <FieldRow label="暂存-80度超低温冰箱" control="seg" :readonly="!editable">
          <SegButtons
            :options="flagOptions"
            :model-value="form.inMinus80"
            :disabled="!editable"
            @update:model-value="(v: string) => form.inMinus80 = v"
          />
        </FieldRow>

        <FieldRow
          label="冻存人"
          :maxlength="50"
          :readonly="!editable"
          :model-value="form.frozenBy"
          @update:model-value="(v: string) => form.frozenBy = v"
        />

        <!-- 飞书 2026-10-03 小程序行12：新增 / 修改时可直接选（与工作台抽屉一致）；没转液氮就留空。
             批次详情弹层的「转液氮」仍可登记，写的是同一列。 -->
        <FieldRow
          label="-80度超低温冰箱转移至液氮时间"
          control="date"
          :readonly="!editable"
          :model-value="form.toLn2Time"
          placeholder="选填"
          @pick="openDatePicker('toLn2Time')"
        />

        <FieldRow
          label="液氮储存位置"
          :required="needLn2Location" marker-side="after"
          :maxlength="100"
          :readonly="!editable"
          :model-value="form.ln2Location"
          :placeholder="needLn2Location ? '必填，如 1号罐-1架' : '转液氮时填'"
          @update:model-value="(v: string) => form.ln2Location = v"
        />

        <FieldRow
          label="备注"
          control="textarea"
          :readonly="!editable"
          :model-value="form.remark"
          placeholder="选填"
          @update:model-value="(v: string) => form.remark = v"
        />

        <wd-cell v-if="editable" title="代数" required marker-side="after">
          <view class="cryo__passage">
            <text class="cryo__passage-p">P</text>
            <input
              class="cryo__passage-in"
              type="number"
              :value="passageDigits"
              placeholder="1~3 位数字"
              @input="onPassageInput"
            >
          </view>
        </wd-cell>
        <wd-cell v-else title="代数" value-align="right">
          <text class="cryo__ro">{{ form.passage || '—' }}</text>
        </wd-cell>
      </view>

      <view class="lqg-bar-spacer" />
    </template>

    <view v-if="!loading && !failed && isInternal" class="lqg-bar cryo__bar">
      <button v-if="editable" class="cryo__btn" :disabled="saving" @click="submit">
        {{ mode === 'new' ? '提交' : '保存' }}
      </button>
    </view>

    <SamplePicker ref="samplePickerRef" :disabled="!editable" @pick="onSamplePicked" />

    <wd-datetime-picker
      ref="pickerRef"
      v-model="pickerValue"
      type="date"
      title="选择日期"
      @confirm="onPicked"
    >
      <!-- ★ 给默认插槽放一个空节点（G12）：没有默认插槽时 wd-datetime-picker 会自己渲染一行
           「值 ›」的 cell，页面底部就多出一行没有标签的「今天日期 ›」。面板开关只靠 open()。 -->
      <view />
    </wd-datetime-picker>
  </view>
</template>

<style lang="scss" scoped>
.cryo {
  padding: var(--lqg-sp-5) 0 0;
}

.cryo__sec {
  padding-top: 0;
}

.cryo__edit {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-primary);
}

.cryo__meta {
  display: block;
  margin: var(--lqg-sp-5) var(--lqg-gutter) 0;
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.cryo__group {
  margin: var(--lqg-sp-5) var(--lqg-gutter) 0;
  overflow: hidden;
  border-radius: var(--lqg-radius-card);
  background: var(--lqg-card);
  box-shadow: var(--lqg-shadow-sm);
}

.cryo__passage {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--lqg-sp-2);
}

.cryo__passage-p {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-2);
}

.cryo__passage-in {
  width: 96px;
  height: 32px;
  /* 数字紧跟在「P」后面（UX 测试 MP-13：原来右对齐，「P」和数字之间空出一大段，像两个控件） */
  text-align: left;
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink);
}

.cryo__ro {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink);
}

.cryo__bar {
  display: flex;
  align-items: center;
}

.cryo__btn {
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

.cryo__btn::after {
  border: none;
}
</style>
