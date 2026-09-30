<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import WdDatetimePicker from 'wot-design-uni/components/wd-datetime-picker/wd-datetime-picker.vue'
import type { DictItem, QcBundle, QcDocType } from '@/api/qc'
import { fetchDict, fetchQcBundle, publishQcDoc, renderQcDoc, saveQcDoc, unpublishQcDoc, uploadToOss } from '@/api/qc'
import EmptyState from '@/components/lqg/EmptyState.vue'
import ErrorState from '@/components/lqg/ErrorState.vue'
import FieldRow from '@/components/lqg/FieldRow.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import QcAttachmentList from '@/components/lqg/QcAttachmentList.vue'
import QcImageSlot from '@/components/lqg/QcImageSlot.vue'
import SegButtons from '@/components/lqg/SegButtons.vue'
import { goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
import { normalizeIdentity } from '@/types/identity'
import { pickErrorText, pickFile } from '@/utils/pickFiles'
import { dateOrNull, QC_TABS, SCORE_ROWS, scoreSummary, statusTag, statusText, toScore } from './status'

// 小程序里编辑一个样本的三份质控文档（内部人员专用；甲方 2026-09-30 要求，能力对齐工作台质控文档编辑页）。
//
// 能力与工作台一致：三个页签（样本质控表 / 类器官质控表 / 类器官质量评分表）的全部字段、四个图片位、
// 细胞活率测定附件、通用附件、评分档位与即时合计、保存、预览（内部版逐页图）、完成并同步 / 撤回。
// 后端完全复用工作台那一套接口（api/qc.ts 头注释），规则也一样：
//   · 保存是整份表单一起发（补丁语义）；已完成的文档改了再保存会自动回到草稿（后端规则）；
//   · 图片、附件立即生效，不跟保存走；
//   · 有没保存的改动时不许「完成并同步」「预览」（同步 / 预览的是库里的内容，不是屏幕上的）。
// ★ 只给内部人员：外部深链进来显示一句话、一个请求都不发（接口本身也按权限串 403）。
definePage({
  style: {
    navigationBarTitleText: '编辑质控文档',
  },
})

const store = useUserStore()
const isInternal = computed(() => normalizeIdentity(store.identity) === 'internal')

const sampleId = ref('')
const bundle = ref<QcBundle | null>(null)
const loading = ref(true)
const failed = ref(false)
const failText = ref('没能加载这个样本的质控文档')
const saving = ref(false)
const publishing = ref(false)

const active = ref<QcDocType>('sample-qc')
const activeTab = computed(() => QC_TABS.find(t => t.type === active.value)!)

// ── 三份表单（屏幕上的值）+ 上次从库里取回的快照（判断有没有没保存的改动）────────────
const sampleForm = reactive({
  patientNo: '',
  samplingSite: '',
  samplingMethod: '',
  clinicalDiagnosis: '',
  receiveDesc: '',
  viabilityOssId: '' as string | number,
  viabilityFileName: '',
  origDesc: '',
  observeDesc: '',
  pretreatDesc: '',
})
const organoidForm = reactive({
  formedTime: '',
  growthState: '',
  growthDesc: '',
  plannedDrugScreen: '',
  feedbackTime: '',
})
const scoreForm = reactive({
  preCultureLevel: '',
  cultureDaysLevel: '',
  organoidCountLevel: '',
  diameterLevel: '',
})
const FORMS: Record<QcDocType, Record<string, unknown>> = {
  'sample-qc': sampleForm,
  'organoid-qc': organoidForm,
  'score': scoreForm,
}
const snapshots = reactive<Record<QcDocType, string>>({ 'sample-qc': '', 'organoid-qc': '', 'score': '' })
const snap = (type: QcDocType) => JSON.stringify(FORMS[type])
const dirtyOf = (type: QcDocType) => snapshots[type] !== '' && snapshots[type] !== snap(type)
const currentDirty = computed(() => dirtyOf(active.value))
const anyDirty = computed(() => QC_TABS.some(t => dirtyOf(t.type)))

/** 从库里取回的值灌进表单；**有没保存改动的那份不覆盖**（传完图片重取时，别把正在写的字冲掉） */
function fill(type: QcDocType, values: Record<string, unknown>, force = false) {
  if (!force && dirtyOf(type)) {
    return
  }
  const form = FORMS[type]
  Object.keys(form).forEach((key) => {
    const value = values[key]
    ;(form as Record<string, unknown>)[key] = value === null || value === undefined ? '' : value
  })
  snapshots[type] = snap(type)
}

function statusOf(type: QcDocType): string | undefined {
  const b = bundle.value
  if (!b) {
    return undefined
  }
  return type === 'sample-qc' ? b.sampleQc?.docStatus : type === 'organoid-qc' ? b.organoidQc?.docStatus : b.score?.docStatus
}
const currentPublished = computed(() => statusOf(active.value) === 'published')

// ── 取数 ─────────────────────────────────────────────────────────────────
async function load(force = false) {
  if (!store.me) {
    try {
      await store.loadMe()
    }
    catch {
      failed.value = true
      loading.value = false
      return
    }
  }
  if (!isInternal.value || !sampleId.value) {
    loading.value = false
    return
  }
  failed.value = false
  try {
    const data = await fetchQcBundle(sampleId.value)
    bundle.value = data
    fill('sample-qc', data.sampleQc as unknown as Record<string, unknown>, force)
    fill('organoid-qc', data.organoidQc as unknown as Record<string, unknown>, force)
    fill('score', data.score as unknown as Record<string, unknown>, force)
  }
  catch (err) {
    failText.value = (err as Error)?.message || '没能加载这个样本的质控文档'
    failed.value = true
  }
  finally {
    loading.value = false
  }
}

// ── 评分表：四个变量的档位与分值来自字典（与工作台同一组字典）────────────────
const dicts = ref<Record<string, DictItem[]>>({})
async function loadDicts() {
  const entries = await Promise.all(SCORE_ROWS.map(async row => [row.dictType, await fetchDict(row.dictType).catch(() => [])] as const))
  dicts.value = Object.fromEntries(entries)
}
type ScoreKey = typeof SCORE_ROWS[number]['key']
function levelOf(key: ScoreKey): string {
  return String((scoreForm as Record<string, string>)[`${key}Level`] || '')
}
function setLevel(key: ScoreKey, value: string) {
  ;(scoreForm as Record<string, string>)[`${key}Level`] = value
}
const scoreItems = computed(() => SCORE_ROWS.map((row) => {
  const level = levelOf(row.key)
  const hit = (dicts.value[row.dictType] || []).find(d => d.dictValue === level)
  return level ? toScore(hit?.remark) : null
}))
const scoreTotal = computed(() => scoreSummary(scoreItems.value))
function scoreOptions(dictType: string) {
  return (dicts.value[dictType] || []).map(d => ({ value: d.dictValue, label: d.dictLabel }))
}

// ── 细胞活率测定（样本质控表上单独一栏；选好文件后「保存」才生效，与工作台一致）──────
const viabilityBusy = ref(false)
async function pickViability() {
  if (viabilityBusy.value) {
    return
  }
  let file
  try {
    file = await pickFile()
  }
  catch (err) {
    uni.showToast({ title: pickErrorText(err), icon: 'none', duration: 3500 })
    return
  }
  if (!file) {
    return
  }
  viabilityBusy.value = true
  uni.showLoading({ title: '上传中', mask: true })
  try {
    const uploaded = await uploadToOss(file.path, file.name)
    sampleForm.viabilityOssId = uploaded.ossId
    sampleForm.viabilityFileName = file.name || uploaded.fileName
    uni.hideLoading()
    uni.showToast({ title: '已选好，点「保存」后生效', icon: 'none' })
  }
  catch (err) {
    uni.hideLoading()
    uni.showToast({ title: (err as Error)?.message || '上传失败', icon: 'none', duration: 3500 })
  }
  finally {
    viabilityBusy.value = false
  }
}
function removeViability() {
  // 摘掉的约定是 viabilityOssId = 0（null 在补丁语义里是「不动」）
  sampleForm.viabilityOssId = 0
  sampleForm.viabilityFileName = ''
}
const hasViability = computed(() => !!sampleForm.viabilityOssId && String(sampleForm.viabilityOssId) !== '0')

// ── 形成类器官时间 / 反馈时间：可以手写文字，也可以点「选日期」────────────────────
const pickerRef = ref<{ open: () => void } | null>(null)
const pickerValue = ref<number>(Date.now())
let pickerTarget: 'formedTime' | 'feedbackTime' = 'formedTime'
function openDate(target: 'formedTime' | 'feedbackTime') {
  pickerTarget = target
  const current = dateOrNull(organoidForm[target])
  pickerValue.value = current ? new Date(`${current}T00:00:00`).getTime() : Date.now()
  pickerRef.value?.open()
}
function onDatePicked(event: { value: number | string }) {
  const ms = Number(event.value)
  if (Number.isNaN(ms)) {
    return
  }
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  organoidForm[pickerTarget] = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

// ── 保存 / 预览 / 完成并同步 ─────────────────────────────────────────────────
function bodyOf(type: QcDocType): Record<string, unknown> {
  if (type === 'sample-qc') {
    return {
      ...sampleForm,
      // 没挂过活率报告时不带这两个键（null = 不动）
      viabilityOssId: sampleForm.viabilityOssId === '' ? null : sampleForm.viabilityOssId,
      viabilityFileName: sampleForm.viabilityOssId === '' ? null : sampleForm.viabilityFileName,
    }
  }
  if (type === 'organoid-qc') {
    return { ...organoidForm }
  }
  // 评分表只收四个档位，分值后端按字典回填；没选的传 null
  return Object.fromEntries(Object.entries(scoreForm).map(([k, v]) => [k, v || null]))
}

async function save() {
  if (saving.value || !sampleId.value) {
    return
  }
  const tab = activeTab.value
  saving.value = true
  try {
    await saveQcDoc(sampleId.value, tab.type, bodyOf(tab.type))
    snapshots[tab.type] = snap(tab.type)
    await load()
    // 与工作台一致：保存即重新出一版预览（异步，不等它）
    renderQcDoc(sampleId.value, tab.kind).catch(() => {})
    uni.showToast({
      title: statusOf(tab.type) === 'published' ? '已保存' : '已保存草稿',
      icon: 'none',
    })
  }
  catch {
    // request 已弹过后端给的原因
  }
  finally {
    saving.value = false
  }
}

function preview() {
  if (currentDirty.value) {
    uni.showToast({ title: '先点「保存」，预览的是保存后的内容', icon: 'none' })
    return
  }
  const tab = activeTab.value
  const no = encodeURIComponent(bundle.value?.sample?.internalNo || '')
  goPage(`/pages/qc/preview?sampleId=${sampleId.value}&docKind=${tab.kind}&no=${no}`)
}

function togglePublish() {
  if (publishing.value || !sampleId.value) {
    return
  }
  if (currentDirty.value) {
    uni.showToast({ title: '先把这一页的改动保存，再完成并同步', icon: 'none' })
    return
  }
  const tab = activeTab.value
  const published = currentPublished.value
  uni.showModal({
    title: published ? '撤回' : '完成并同步',
    content: published
      ? '确定撤回吗？撤回后送检方立刻看不到这份文档。'
      : '确定「完成并同步」这份文档吗？完成后送检方就能看到它。',
    // ★ 微信 showModal 的按钮文字最多 4 个字（超了直接报错不弹）
    confirmText: published ? '撤回' : '确定同步',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      publishing.value = true
      try {
        if (published) {
          await unpublishQcDoc(sampleId.value, tab.type)
        }
        else {
          await publishQcDoc(sampleId.value, tab.type)
        }
        await load()
        uni.showToast({ title: published ? '已撤回，送检方看不到了' : '已同步给送检方', icon: 'none' })
      }
      catch {
        // request 已弹过原因
      }
      finally {
        publishing.value = false
      }
    },
  })
}

// ── 离开页面前的提醒（有没保存的改动）——小程序原生返回键也拦得住 ──────────────────
watch(anyDirty, (dirty) => {
  // #ifdef MP-WEIXIN
  const api = uni as unknown as { enableAlertBeforeUnload?: (o: object) => void, disableAlertBeforeUnload?: (o?: object) => void }
  if (dirty) {
    api.enableAlertBeforeUnload?.({ message: '还有没保存的改动，离开就丢掉了。确定离开吗？' })
  }
  else {
    api.disableAlertBeforeUnload?.()
  }
  // #endif
})

const summary = computed(() => {
  const s = bundle.value?.sample
  if (!s) {
    return ''
  }
  const kind = s.sampleKind === 'organoid' ? '类器官送样记录' : '样本记录信息表'
  return [s.sourceUnitName, kind, s.receiveDate ? `收样 ${s.receiveDate}` : '', s.operatorName ? `操作人 ${s.operatorName}` : '']
    .filter(Boolean)
    .join(' · ')
})

function pickTab(type: QcDocType) {
  active.value = type
}

onLoad((query) => {
  sampleId.value = String((query as Record<string, string> | undefined)?.sampleId ?? '')
  if (query && (query as Record<string, string>).tab) {
    const tab = QC_TABS.find(t => t.type === (query as Record<string, string>).tab)
    if (tab) {
      active.value = tab.type
    }
  }
  load(true)
  loadDicts()
})
</script>

<template>
  <view class="qce">
    <EmptyState v-if="!loading && !isInternal" state="empty" text="编辑质控文档只给中心内部人员开放" />

    <LoadingState v-else-if="loading" />

    <ErrorState v-else-if="failed || !bundle" :text="failText" @retry="load(true)" />

    <template v-else>
      <!-- 页头：样本摘要（从样本主档带出，只读） -->
      <view class="lqg-card qce__head">
        <text class="qce__no lqg-mono">{{ bundle.sample?.internalNo || '—' }}</text>
        <text class="qce__sum">{{ summary }}</text>
      </view>

      <!-- 三个页签，各带状态 -->
      <view class="lqg-sheets qce__tabs">
        <view
          v-for="tab in QC_TABS"
          :key="tab.type"
          class="lqg-sheets__item qce__tab"
          :class="{ 'lqg-sheets__item--on': tab.type === active }"
          @click="pickTab(tab.type)"
        >
          <text class="qce__tab-t">{{ tab.short }}</text>
          <text class="qce__tab-s" :class="`qce__tab-s--${statusTag(statusOf(tab.type))}`">
            {{ dirtyOf(tab.type) ? '未保存' : statusText(statusOf(tab.type)) }}
          </text>
        </view>
      </view>

      <!-- ═══ 样本质控表 ═══（三份表各挂一次、v-show 切换：换页签再回来，没保存的字还在） -->
      <view v-show="active === 'sample-qc'">
        <view class="qce__group">
          <FieldRow label="患者编号" placeholder="如 P-0231" :model-value="sampleForm.patientNo" @update:model-value="(v: string) => sampleForm.patientNo = v" />
          <FieldRow label="取样部位" :model-value="sampleForm.samplingSite" @update:model-value="(v: string) => sampleForm.samplingSite = v" />
          <FieldRow label="取样方式" :model-value="sampleForm.samplingMethod" @update:model-value="(v: string) => sampleForm.samplingMethod = v" />
          <view class="qce__viab">
            <text class="qce__viab-l">细胞活率测定</text>
            <view class="qce__viab-r">
              <text v-if="hasViability" class="qce__viab-name">{{ sampleForm.viabilityFileName || '已上传的活率报告' }}</text>
              <text class="qce__link" @click="pickViability">{{ viabilityBusy ? '上传中…' : hasViability ? '替换' : '上传活率报告' }}</text>
              <text v-if="hasViability" class="qce__link qce__link--danger" @click="removeViability">移除</text>
            </view>
          </view>
        </view>

        <view class="qce__group">
          <FieldRow label="临床诊断 / 既往治疗" control="textarea" :model-value="sampleForm.clinicalDiagnosis" @update:model-value="(v: string) => sampleForm.clinicalDiagnosis = v" />
          <FieldRow label="收样描述" control="textarea" :model-value="sampleForm.receiveDesc" @update:model-value="(v: string) => sampleForm.receiveDesc = v" />
        </view>

        <view class="lqg-gl">收样原始情况 · 图片</view>
        <view class="qce__group qce__slot">
          <QcImageSlot :sample-id="sampleId" doc-type="sample-qc" slot-key="orig" :images="bundle.sampleQc?.images?.orig || []" @changed="load()" />
          <FieldRow label="情况描述" control="textarea" :model-value="sampleForm.origDesc" @update:model-value="(v: string) => sampleForm.origDesc = v" />
        </view>

        <view class="lqg-gl">样本观察情况 · 图片</view>
        <view class="qce__group qce__slot">
          <QcImageSlot :sample-id="sampleId" doc-type="sample-qc" slot-key="observe" :images="bundle.sampleQc?.images?.observe || []" @changed="load()" />
          <FieldRow label="情况描述" control="textarea" :model-value="sampleForm.observeDesc" @update:model-value="(v: string) => sampleForm.observeDesc = v" />
        </view>

        <view class="lqg-gl">样本预处理情况 · 图片</view>
        <view class="qce__group qce__slot">
          <QcImageSlot :sample-id="sampleId" doc-type="sample-qc" slot-key="pretreat" :images="bundle.sampleQc?.images?.pretreat || []" @changed="load()" />
          <FieldRow label="情况描述" control="textarea" :model-value="sampleForm.pretreatDesc" @update:model-value="(v: string) => sampleForm.pretreatDesc = v" />
        </view>

        <view class="lqg-gl">附件</view>
        <view class="qce__group qce__slot">
          <QcAttachmentList :sample-id="sampleId" doc-type="sample-qc" :attachments="bundle.sampleQc?.attachments || []" @changed="load()" />
        </view>
      </view>

      <!-- ═══ 类器官质控表 ═══ -->
      <view v-show="active === 'organoid-qc'">
        <view class="lqg-gl">样本观察情况 · 图片</view>
        <view class="qce__group qce__slot">
          <QcImageSlot :sample-id="sampleId" doc-type="organoid-qc" slot-key="organoid_observe" :images="bundle.organoidQc?.images?.organoid_observe || []" @changed="load()" />
        </view>

        <view class="qce__group">
          <FieldRow label="形成类器官时间" placeholder="选日期或直接写" :model-value="organoidForm.formedTime" @update:model-value="(v: string) => organoidForm.formedTime = v" />
          <view class="qce__datepick"><text class="qce__link" @click="openDate('formedTime')">选日期 ›</text></view>
          <FieldRow label="生长状态" :model-value="organoidForm.growthState" @update:model-value="(v: string) => organoidForm.growthState = v" />
          <FieldRow label="类器官生长情况" control="textarea" :model-value="organoidForm.growthDesc" @update:model-value="(v: string) => organoidForm.growthDesc = v" />
          <FieldRow label="预计筛药" control="textarea" :model-value="organoidForm.plannedDrugScreen" @update:model-value="(v: string) => organoidForm.plannedDrugScreen = v" />
          <FieldRow label="反馈时间" placeholder="选日期或直接写" :model-value="organoidForm.feedbackTime" @update:model-value="(v: string) => organoidForm.feedbackTime = v" />
          <view class="qce__datepick"><text class="qce__link" @click="openDate('feedbackTime')">选日期 ›</text></view>
        </view>
        <text class="qce__hint">时间可以选日期（填 yyyy-MM-dd），也可以直接写文字，如「约第 5 天」</text>

        <view class="lqg-gl">附件</view>
        <view class="qce__group qce__slot">
          <QcAttachmentList :sample-id="sampleId" doc-type="organoid-qc" :attachments="bundle.organoidQc?.attachments || []" @changed="load()" />
        </view>
      </view>

      <!-- ═══ 类器官质量评分表 ═══ -->
      <view v-show="active === 'score'">
        <view class="lqg-gl">评分（选完四项出合计）</view>
        <view class="qce__group qce__slot">
          <view v-for="(row, index) in SCORE_ROWS" :key="row.key" class="qce__score">
            <view class="qce__score-head">
              <text class="qce__score-l">{{ row.label }}</text>
              <text class="qce__score-v">{{ scoreItems[index] === null ? '—' : `${scoreItems[index]} 分` }}</text>
            </view>
            <SegButtons
              :options="scoreOptions(row.dictType)"
              :model-value="levelOf(row.key)"
              @update:model-value="(v: string) => setLevel(row.key, v)"
            />
          </view>
          <view class="qce__total">
            <text class="qce__total-l">合计</text>
            <text class="qce__total-v">{{ scoreTotal === null ? '—' : scoreTotal }}</text>
          </view>
          <text v-if="scoreTotal === null" class="qce__hint qce__hint--in">四项都选了才出合计</text>
        </view>

        <view class="lqg-gl">附件</view>
        <view class="qce__group qce__slot">
          <QcAttachmentList :sample-id="sampleId" doc-type="score" :attachments="bundle.score?.attachments || []" @changed="load()" />
        </view>
      </view>

      <view class="lqg-bar-spacer qce__spacer" />
    </template>

    <view v-if="!loading && !failed && bundle && isInternal" class="lqg-bar qce__bar">
      <text class="qce__bar-hint">
        {{ currentDirty ? '有改动还没保存' : currentPublished ? '已同步给送检方 · 修改后需重新同步' : '草稿：「完成并同步」之后送检方才看得到' }}
      </text>
      <view class="qce__btns">
        <button class="qce__btn qce__btn--s" :disabled="publishing" @click="preview">预览</button>
        <button class="qce__btn qce__btn--s" :disabled="publishing || currentDirty" @click="togglePublish">
          {{ currentPublished ? '撤回' : '完成并同步' }}
        </button>
        <button class="qce__btn qce__btn--p" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
      </view>
    </view>

    <wd-datetime-picker ref="pickerRef" v-model="pickerValue" type="date" title="选择日期" @confirm="onDatePicked">
      <!-- 默认插槽放空节点：否则 wd-datetime-picker 会自己渲染一行「值 ›」（cryo/form.vue 同一坑 G12） -->
      <view />
    </wd-datetime-picker>
  </view>
</template>

<style lang="scss" scoped>
.qce {
  padding: var(--lqg-sp-5) 0 0;
}

.qce__head {
  margin: 0 var(--lqg-gutter);
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-2);
}

.qce__no {
  font-size: var(--lqg-fs-lg);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.qce__sum {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.qce__tabs {
  display: flex;
}

.qce__tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.qce__tab-t {
  white-space: nowrap;
}

.qce__tab-s {
  font-size: var(--lqg-fs-xs);
  color: var(--lqg-ink-3);
}

.qce__tab-s--draft {
  color: var(--lqg-warn);
}

.qce__tab-s--published {
  color: var(--lqg-ok);
}

/* 选中的页签是深底：状态字统一用反白，否则绿 / 橙字压在主色上看不清 */
.lqg-sheets__item--on .qce__tab-s {
  color: var(--lqg-on-primary);
  opacity: 0.85;
}

.qce__group {
  margin: var(--lqg-sp-5) var(--lqg-gutter) 0;
  overflow: hidden;
  border-radius: var(--lqg-radius-card);
  background: var(--lqg-card);
  box-shadow: var(--lqg-shadow-sm);
}

.lqg-gl + .qce__group {
  margin-top: 0;
}

.qce__slot {
  padding: var(--lqg-sp-5);
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-4);
}

/* 活率报告那一行：与 FieldRow 同一个左右排版与下划线 */
.qce__viab {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lqg-sp-3);
  padding: 14px var(--lqg-sp-6);
  border-bottom-width: 1px;
  border-bottom-style: solid;
  border-bottom-color: #dfe6e7;
}

.qce__viab:last-child {
  border-bottom-width: 0;
}

.qce__viab-l {
  flex: none;
  font-size: var(--lqg-fs-title);
  color: var(--lqg-ink);
}

.qce__viab-r {
  min-width: 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--lqg-sp-3);
}

.qce__viab-name {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-2);
  word-break: break-all;
}

.qce__link {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-primary);
  white-space: nowrap;
}

.qce__link--danger {
  color: var(--lqg-danger);
}

/* 「选日期」挂在上一行时间字段下面，算那一行的附属操作（不再画线） */
.qce__datepick {
  display: flex;
  justify-content: flex-end;
  padding: var(--lqg-sp-2) var(--lqg-sp-6) var(--lqg-sp-3);
}

.qce__hint {
  display: block;
  margin: var(--lqg-sp-3) var(--lqg-gutter) 0;
  font-size: var(--lqg-fs-xs);
  color: var(--lqg-ink-3);
}

.qce__hint--in {
  margin: 0;
}

.qce__score {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-3);
  padding-bottom: var(--lqg-sp-4);
  border-bottom-width: 1px;
  border-bottom-style: solid;
  border-bottom-color: #dfe6e7;
}

.qce__score-head {
  display: flex;
  justify-content: space-between;
  gap: var(--lqg-sp-3);
}

.qce__score-l {
  font-size: var(--lqg-fs-title);
  color: var(--lqg-ink);
}

.qce__score-v {
  flex: none;
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-primary);
}

.qce__total {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.qce__total-l {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
}

.qce__total-v {
  font-size: var(--lqg-fs-lg);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-primary);
}

.qce__spacer {
  height: calc(var(--lqg-btn-h) + 48px + env(safe-area-inset-bottom));
}

.qce__bar {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-3);
}

.qce__bar-hint {
  font-size: var(--lqg-fs-xs);
  color: var(--lqg-ink-3);
}

.qce__btns {
  display: flex;
  gap: var(--lqg-sp-3);
}

.qce__btn {
  flex: 1;
  height: var(--lqg-btn-h);
  line-height: var(--lqg-btn-h);
  margin: 0;
  padding: 0;
  font-size: var(--lqg-fs-body);
  font-weight: var(--lqg-fw-semibold);
  border-radius: var(--lqg-radius-ctl);
}

.qce__btn::after {
  border: none;
}

.qce__btn--s {
  color: var(--lqg-primary);
  background: var(--lqg-primary-soft);
}

.qce__btn--p {
  flex: 1.3;
  color: var(--lqg-on-primary);
  background: var(--lqg-primary);
}
</style>
