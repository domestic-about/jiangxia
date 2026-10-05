<script setup lang="ts">
import { computed, ref } from 'vue'
import type { CryoBatchRow } from '@/api/cryo'
import { cryoLedgerCell, cryoPlaceText, cryoQtyText, fetchIntCryoDetail, overdueLabel } from '@/api/cryo'
import CryoFlowForm from '@/components/lqg/CryoFlowForm.vue'
import CryoLn2Form from '@/components/lqg/CryoLn2Form.vue'
import ErrorState from '@/components/lqg/ErrorState.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import type { CryoFlowRecord, MpFlowKind } from '@/pages/cryo/flow'
import {
  canChangeFlow,
  canRegisterLn2,
  deleteCryoFlow,
  editedText,
  fetchCryoFlows,
  flowBalanceText,
  flowDeltaText,
  flowFromText,
  flowKindText,
  flowOperatorText,
  flowPurposeText,
  flowTimeText,
  isEdited,
  mpFlowKindOf,
  removeConfirmText,
} from '@/pages/cryo/flow'
import { ledgerColumns } from '@/pages/ledger/columns'
import { goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
// ★ 一律显式 import wd-* 的 .vue（SAMPLE-MP-001 坑 6：只靠 easycom 会让该模块的 .js 产物消失，
//   而页面在真机上就是空白 —— 表格页那边因此也必须用 .vue 路径 import 本组件，
//   不能走桶口 / easycom 标签，见 accept 第 4 条的那段 grep）。
import WdPopup from 'wot-design-uni/components/wd-popup/wd-popup.vue'

// 冻存批次详情弹层（UI:mp.cryo.flow）· CRYO-MP-001；2026-09-24 起可登记（G 批 B2）。
//
// ★ 甲方看设计稿 v3 后要求「小程序和工作台界面都能操作」冻存取用登记，并说清了 -80 与液氮的关系
//   （-80 是暂存、液氮是长期存放，一般从液氮取走）。所以弹层分三块：
//   1. 上部：这一批放在哪（「-80℃ 暂存 · 冻存 N 天」/「液氮 · 位置 xxx · 转入 yyyy-mm-dd」）、
//      剩几支、已取空 / 已超 N 天标记，再按甲方模板的先后列出全部字段（列名与顺序只从
//      `pages/ledger/columns.ts` 的 cryo 那一段来，与表格、导出同一份）；
//   2. 操作：「取走」「补入」，还在 -80 没转过的多一个「转液氮」—— 点了在弹层里就地换成表单
//      （`CryoFlowForm` / `CryoLn2Form`），调的是与工作台**同一份**写口，规则一字不差；
//   3. 取用登记：时间倒序，每行带操作后剩余、改过的标「已改」；取走 / 补入那两种每行可「改」「删」
//      （第三种登记只在工作台做，这里照样列出来但不给改删）。
// ★ 右上角「修改」仍然只做一件事：关掉弹层、跳 `pages/cryo/form?id=&mode=edit`（改这条冻存记录本身）。
// ★ 超期、剩余、位置、取空**照实显示**后端给的 `overdue` / `overdueDays` / `remainingQty` /
//   `location` / `emptied` / `frozenDays`，前端不按天数自己算；每次登记成功后重新取这一批与它的登记，
//   并通知表格页刷新（剩余、超期、页签数字当场变）。
const props = defineProps<{
  /** 预留：弹层自身不含身份判断（入口由表格页把住，接口 403 兜底） */
  disabled?: boolean
}>()

const emit = defineEmits<{
  /** 登记 / 改 / 删 / 转液氮成功了：表格页据此重新取数（剩余、超期、页签数字） */
  (e: 'changed'): void
}>()

const store = useUserStore()

const show = ref(false)
const row = ref<CryoBatchRow | null>(null)
const flows = ref<CryoFlowRecord[]>([])
const loading = ref(false)
const failed = ref(false)
/** 弹层当前显示哪一块：批次详情 / 取走补入表单 / 转液氮表单 */
const view = ref<'detail' | 'flow' | 'ln2'>('detail')
const formKind = ref<MpFlowKind>('take')
/** 改一笔时是那一笔；新登记为 null */
const editingFlow = ref<CryoFlowRecord | null>(null)
const removing = ref(false)

function str(value: unknown): string {
  return value === null || value === undefined ? '' : String(value)
}

/** 上部抬头：冻存样品名称（编号类，等宽） */
const name = computed(() => str(row.value?.cryoName).trim() || '—')

/** 超期徽标文案（后端给的 `overdueDays`；没有就不渲染） */
const overdueText = computed(() => {
  if (row.value?.overdue !== true) {
    return ''
  }
  return overdueLabel(row.value.overdueDays)
})

/** 已取空（后端行上的 `emptied`，与表格「已取空」页签同一判据） */
const emptied = computed(() => row.value?.emptied === true)

/** 放在哪：-80 暂存几天 / 液氮哪个位置、哪天转入 */
const placeText = computed(() => (row.value ? cryoPlaceText(row.value) : ''))

/** 剩 N / 初始 M 支 */
const qtyLine = computed(() => (row.value ? cryoQtyText(row.value, false) : ''))

/** 全部字段，按甲方模板的先后（列名 / 顺序只从 columns.ts 来；冻存样品已在抬头） */
const fields = computed(() => {
  const current = row.value
  const cols = ledgerColumns('cryo')
  if (!current || !cols) {
    return []
  }
  return cols.columns.map(col => ({ key: col.key, label: col.label, value: cryoLedgerCell(current, col.key) }))
})

/** 还在 -80、没转过液氮的才有「转液氮」 */
const showLn2 = computed(() => canRegisterLn2(row.value))

/** 取空了就不能再取（按钮置灰，点了给一句提示） */
const canTake = computed(() => (row.value?.remainingQty ?? 0) > 0)

/** 这一批的取用登记（拉不到就显示失败态 + 重试） */
async function loadFlows() {
  const current = row.value
  if (!current || current.id === null || current.id === undefined) {
    flows.value = []
    return
  }
  loading.value = true
  failed.value = false
  try {
    flows.value = await fetchCryoFlows(current.id as string | number)
  }
  catch {
    failed.value = true
    flows.value = []
  }
  finally {
    loading.value = false
  }
}

/** 重新取这一批（剩余、位置、超期、取空都是读时算的，登记之后要重取才对得上） */
async function refreshBatch() {
  const current = row.value
  if (!current || current.id === null || current.id === undefined) {
    return
  }
  try {
    row.value = await fetchIntCryoDetail(current.id as string | number)
  }
  catch {
    // 取不到就先留着旧的；表格页那边会整体刷新
  }
}

/**
 * 表格页点一行时调它（持实例调 open —— 弹层开关不靠 prop，wot 1.14 的 popup 没有 `visible`）。
 */
function open(next: CryoBatchRow) {
  if (props.disabled) {
    return
  }
  row.value = next ?? null
  flows.value = []
  view.value = 'detail'
  editingFlow.value = null
  show.value = true
  loadFlows()
}

/** 「取走」「补入」：就地换成登记表单 */
function startFlow(kind: MpFlowKind) {
  if (kind === 'take' && !canTake.value) {
    uni.showToast({ title: '这一批已经取空了', icon: 'none' })
    return
  }
  formKind.value = kind
  editingFlow.value = null
  view.value = 'flow'
}

/** 一笔登记的「改」：同一张表单，类型是那一笔原来的（改不了） */
function editFlow(item: CryoFlowRecord) {
  const kind = mpFlowKindOf(item)
  if (!kind) {
    return
  }
  formKind.value = kind
  editingFlow.value = item
  view.value = 'flow'
}

function startLn2() {
  view.value = 'ln2'
}

function backToDetail() {
  view.value = 'detail'
  editingFlow.value = null
}

/** 登记 / 改 / 删 / 转液氮成功：回到详情，重取这一批与它的登记，并让表格页刷新 */
async function afterChange() {
  backToDetail()
  await Promise.all([refreshBatch(), loadFlows()])
  emit('changed')
}

function confirmRemove(item: CryoFlowRecord): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title: '删掉这一笔',
      content: removeConfirmText(item),
      confirmText: '删除',
      cancelText: '不删',
      success: res => resolve(!!res.confirm),
      fail: () => resolve(false),
    })
  })
}

/** 一笔登记的「删」：二次确认；被拒（删了会让后面某一步为负）时把后端原话弹出来 */
async function removeFlow(item: CryoFlowRecord) {
  const current = row.value
  if (!current || removing.value || !canChangeFlow(item) || !(await confirmRemove(item))) {
    return
  }
  removing.value = true
  try {
    await deleteCryoFlow(current.id as string | number, item.id)
    uni.showToast({ title: '已删除', icon: 'none' })
    await afterChange()
  }
  catch (e) {
    uni.showModal({
      title: '没删成',
      content: e instanceof Error && e.message ? e.message : '请稍后再试',
      showCancel: false,
      confirmText: '知道了',
    })
    loadFlows()
  }
  finally {
    removing.value = false
  }
}

/** 右上角「修改」（CR-20260918-07）：关掉弹层，跳本条记录的填写页修改模式 */
function toEdit() {
  const current = row.value
  if (!current || current.id === null || current.id === undefined) {
    return
  }
  show.value = false
  goPage(`/pages/cryo/form?id=${current.id}&mode=edit`)
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
    <view class="lqg-sheet cbs">
      <CryoFlowForm
        v-if="view === 'flow' && row"
        :batch="row"
        :kind="formKind"
        :flow="editingFlow"
        :operator-name="store.name"
        @saved="afterChange"
        @cancel="backToDetail"
      />

      <CryoLn2Form v-else-if="view === 'ln2' && row" :batch="row" @saved="afterChange" @cancel="backToDetail" />

      <template v-else>
        <view class="lqg-sheet__head">
          <text class="cbs__name lqg-mono">{{ name }}</text>
          <text class="cbs__edit" @click="toEdit">修改</text>
        </view>

        <view class="cbs__where">
          <text v-if="emptied" class="lqg-tag cbs__emptied">已取空</text>
          <text v-if="overdueText" class="lqg-tag lqg-tag--overdue">{{ overdueText }}</text>
          <text class="cbs__place">{{ placeText }}</text>
        </view>
        <text class="cbs__qty lqg-num">{{ qtyLine }}</text>

        <!-- 全部字段：甲方模板的先后（冻存时间 … 备注），再是代数、当前剩余 -->
        <view class="cbs__fields">
          <view v-for="item in fields" :key="item.key" class="cbs__field">
            <text class="cbs__label">{{ item.label }}</text>
            <text class="cbs__value">{{ item.value }}</text>
          </view>
        </view>

        <view class="cbs__acts">
          <text class="cbs__act" :class="{ 'cbs__act--off': !canTake }" @click="startFlow('take')">取走</text>
          <text class="cbs__act" @click="startFlow('add')">补入</text>
          <text v-if="showLn2" class="cbs__act" @click="startLn2">转液氮</text>
        </view>

        <view class="lqg-gl">取用登记</view>

        <LoadingState v-if="loading" />

        <ErrorState v-else-if="failed" text="没能加载取用登记" @retry="loadFlows" />

        <view v-else-if="flows.length === 0" class="cbs__empty">
          <text class="cbs__empty-t">还没有取用登记</text>
        </view>

        <view v-else class="cbs__list">
          <view v-for="item in flows" :key="String(item.id)" class="cbs__item">
            <view class="cbs__item-l">
              <text class="cbs__when">{{ flowTimeText(item) }}</text>
              <text class="cbs__kind">{{ flowKindText(item) }}</text>
              <text class="cbs__delta lqg-num">{{ flowDeltaText(item) }}</text>
              <text v-if="flowFromText(item)" class="cbs__from">从{{ flowFromText(item) }}</text>
              <text class="cbs__purpose">{{ flowPurposeText(item) }}</text>
              <text class="cbs__op">{{ flowOperatorText(item) }}</text>
              <text v-if="isEdited(item)" class="lqg-tag lqg-tag--ocr cbs__edited">{{ editedText(item) }}</text>
            </view>
            <view class="cbs__item-r">
              <text class="cbs__bal lqg-num">{{ flowBalanceText(item) }}</text>
              <view v-if="canChangeFlow(item)" class="cbs__row-acts">
                <text class="cbs__row-act" @click="editFlow(item)">改</text>
                <text class="cbs__row-act cbs__row-act--danger" @click="removeFlow(item)">删</text>
              </view>
            </view>
          </view>
        </view>

        <text class="cbs__note">每一笔都写明操作后还剩几支，改过的标「已改」；登记错了点「改」或「删」。</text>
      </template>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
/* 内容多（字段 + 登记）时弹层自己滚，不顶出屏幕 */
.cbs {
  max-height: 85vh;
  overflow-y: auto;
  box-sizing: border-box;
}

.cbs__name {
  min-width: 0;
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
  word-break: break-all;
}

.cbs__edit {
  flex: none;
  /* 点击区扩到约 44 高（UX 测试 MP-09：原来只有字那么大，30×21）；负外边距抵掉，版式不动 */
  padding: var(--lqg-sp-5) var(--lqg-sp-4);
  margin: calc(-1 * var(--lqg-sp-5)) calc(-1 * var(--lqg-sp-4));
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-primary);
}

.cbs__where {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--lqg-sp-2) var(--lqg-sp-3);
  margin-top: var(--lqg-sp-3);
}

/* 「已取空」：琥珀色醒目标记（与超期的红区分开：超期是「该处理」，取空是「没了」） */
.cbs__emptied {
  font-weight: var(--lqg-fw-semibold);
  background: var(--lqg-warn-soft);
  color: var(--lqg-warn);
}

.cbs__place {
  font-size: var(--lqg-fs-body);
  font-weight: var(--lqg-fw-medium);
  color: var(--lqg-ink);
}

.cbs__qty {
  display: block;
  margin-top: var(--lqg-sp-2);
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-2);
}

.cbs__fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--lqg-sp-4) var(--lqg-sp-5);
  margin-top: var(--lqg-sp-5);
  padding: var(--lqg-sp-5);
  border-radius: var(--lqg-radius-ctl);
  background: var(--lqg-inset);
}

.cbs__field {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.cbs__label {
  font-size: var(--lqg-fs-xs);
  color: var(--lqg-ink-3);
}

.cbs__value {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink);
  word-break: break-all;
}

.cbs__acts {
  display: flex;
  gap: var(--lqg-sp-4);
  margin-top: var(--lqg-sp-5);
}

.cbs__act {
  flex: 1;
  height: 40px;
  line-height: 40px;
  text-align: center;
  font-size: var(--lqg-fs-base);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-primary);
  background: var(--lqg-primary-soft);
  border-radius: var(--lqg-radius-ctl);
}

.cbs__act--off {
  color: var(--lqg-ink-3);
  background: var(--lqg-inset);
}

.cbs__list {
  overflow: hidden;
  border-radius: var(--lqg-radius-ctl);
  background: var(--lqg-card);
}

.cbs__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lqg-sp-3);
  padding: var(--lqg-sp-4) 0;
  border-bottom: 1px solid var(--lqg-line);
}

.cbs__item-l {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-2) var(--lqg-sp-3);
  flex-wrap: wrap;
  min-width: 0;
}

.cbs__item-r {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--lqg-sp-2);
  flex: none;
}

.cbs__when {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.cbs__kind {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink);
}

.cbs__delta {
  font-size: var(--lqg-fs-body);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.cbs__from,
.cbs__purpose,
.cbs__op {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-2);
}

.cbs__edited {
  font-size: var(--lqg-fs-xs);
}

.cbs__bal {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.cbs__row-acts {
  display: flex;
  /* 「改」「删」之间拉开，删除不会误点成修改（UX 测试 MP-09）；= 两边各扩出的点击区之和，两块点击区相接不重叠 */
  gap: calc(2 * var(--lqg-sp-6));
}

.cbs__row-act {
  /* 点击区扩到约 44×44（原来 12×17）；负外边距抵掉，行高不变 */
  padding: var(--lqg-sp-5) var(--lqg-sp-6);
  margin: calc(-1 * var(--lqg-sp-5)) calc(-1 * var(--lqg-sp-6));
  font-size: var(--lqg-fs-sm);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-primary);
}

.cbs__row-act--danger {
  color: var(--lqg-danger);
}

.cbs__empty {
  padding: var(--lqg-sp-7) 0;
  text-align: center;
}

.cbs__empty-t {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}

.cbs__note {
  display: block;
  margin-top: var(--lqg-sp-5);
  font-size: var(--lqg-fs-xs);
  color: var(--lqg-ink-3);
}
</style>
