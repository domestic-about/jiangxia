<script setup lang="ts">
// 文档预览页（UI:mp.doc.preview / FLOW:F-DOC-02.step2 · 方案 A「应用内预览」）· DOC-MP-002。
//
// 结构（自上而下，方向 A 落地规范 §5.1 / §5.8 / §5.10 / §5.11）：
//   ① 顶部 `DocTabs` 切换条：该样本**已完成**的几份 + ≥2 份时的「合并」（顺序由 groupDocs 定）
//   ② 上部 `PageImageViewer`：逐页页面图，点任一页全屏 + 双指缩放（第一层放大）
//   ③ 中部 `ThumbStrip`：「文档中的图片」缩略图，点开看**原图**（第二层放大，显微照片细节）
//   ④ 下部 `AttachmentList`：附件，点开用微信内置查看器
//   ⑤ 底部固定 `DownloadBar`：格式切换（Word / PDF）+「打开」+「发送到微信」
//
// 口径（票面 §0 最容易做反的四条，逐条落点）：
//   1. **两层放大**：`PageImageViewer` 点页 → `uni.previewImage`（全屏缩放）；
//      `ThumbStrip` 缩略用 `previewUrl`、点开用 `url`（**原图**）——accept 1 的 counterfeit 第 1 条。
//   2. **两处入口一套实现**：本页底部的 `DownloadBar` 与列表上的 `DownloadSheet` 里是同一个组件。
//   3. 顶部切换条的顺序与「要不要合并」**复用** `groupDocs`（DOC-MP-001 的纯函数），
//      本页一行排序都不写（口径复述 4）。
//   4. 身份只决定**打哪个接口**（内部 `/mp/int/doc/**`、外部 `/mp/ext/doc/**`），
//      不决定「给哪几份」：外部拿到的也是清单里那几份（口径复述 4 的后半句）。
//
// ★★ **失败态不泄露内部错误**：页面只说一句人话（`stateText('failed')`），
//    内部那条接口里的失败原因字段一个字节都不读（accept 1 的禁字 grep 钉着这件事）。
// ★★ **合并件「份数够但还没渲染好」**（DOC-MP-001 §7.4 / issue #259）：合并件是异步渲染的，
//    清单里还没有 `merged` 行时，本页进「文档生成中」态并轮询（`waitForMerged`，最多 60s），
//    超时给「重试」；渲染好之后 DownloadBar 会 `ready`，本页重新取 pages。
// ★ 页面图 / 原图 / 下载链接都是 10 分钟签名链接：进页与每次切换都重新取，不缓存。
// ★ 视觉只用 `.lqg-*` 范式类与 token，零色值字面量。
import type { DocAttachmentRow, DocImageRow, DocListRow, DocPageItem } from '@/api/doc'
import { fetchDocPages, fetchSampleDocs } from '@/api/doc'
import AttachmentList from '@/components/lqg/AttachmentList.vue'
import DocTabs from '@/components/lqg/DocTabs.vue'
import DownloadBar from '@/components/lqg/DownloadBar.vue'
import ErrorState from '@/components/lqg/ErrorState.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import PageImageViewer from '@/components/lqg/PageImageViewer.vue'
import ThumbStrip from '@/components/lqg/ThumbStrip.vue'
import type { DocLoadState } from '@/pages/doc/download'
import { hasMergedRow, stateText, waitForMerged } from '@/pages/doc/download'
import type { DocGroup } from '@/pages/doc/group'
import { docKindLabel, groupDocs } from '@/pages/doc/group'
import { useUserStore } from '@/store/user'
import { normalizeIdentity } from '@/types/identity'
import { computed, ref } from 'vue'

definePage({
  style: {
    navigationBarTitleText: '文档预览',
  },
})

const store = useUserStore()

/** 路由参数 */
const sampleId = ref('')
/** 进来时指定的那一份（列表点哪一份就传哪一份；没传就取组内第一份） */
const initialKind = ref('')

/** 当前展示的那一份（sample_qc / organoid_qc / organoid_score / merged） */
const current = ref('')
/** 顶部切换条（顺序 = groupDocs 给的组内固定顺序 + 末尾「合并」） */
const tabs = ref<Array<{ docKind: string, label: string }>>([])
/** 合并件此刻在不在（清单里有没有 merged 行） */
const mergedState = ref<'ready' | 'generating' | 'absent'>('absent')
/** 编号（内部编号 / 送检单号）——服务端 fileName 缺失时的兜底文件名要用 */
const docNo = ref('')

const state = ref<DocLoadState>('loading')
const pages = ref<DocPageItem[]>([])
const images = ref<DocImageRow[]>([])
const attachments = ref<DocAttachmentRow[]>([])
/** 正在轮询合并件（防止重复起轮询） */
const pollingMerged = ref(false)

/** 身份的**唯一**来源是 /mp/me（SYS-MP-001）；认不出来就按外部那条最窄的打 */
const identity = computed(() => normalizeIdentity(store.identity))

/** 当前样本那一组（groupDocs 的结果里找） */
function groupOf(rows: DocListRow[]): DocGroup | undefined {
  return groupDocs(rows).find(group => group.sampleId === sampleId.value)
}

/**
 * 用清单行刷新「顶部切换条的份数 / 顺序」「合并入口」「编号」。
 * ★ 顺序与「要不要合并」**只由 groupDocs 决定**（口径复述 4）。
 */
function applyRows(rows: DocListRow[]) {
  const group = groupOf(rows)
  const list: Array<{ docKind: string, label: string }> = (group?.docKinds ?? [])
    .map(kind => ({ docKind: kind, label: docKindLabel(kind) }))
  // 「合并」在 ≥2 份时才出现（groupDocs 的 showMerge；蓝图 FLOW:F-DOC-02.step1「每组最多三份」）
  if (group?.showMerge) {
    list.push({ docKind: 'merged', label: '合并' })
  }
  tabs.value = list
  docNo.value = group?.title ?? ''
  mergedState.value = hasMergedRow(rows) ? 'ready' : (group?.showMerge ? 'generating' : 'absent')
}

/** 这一份取页面图 / 图片位 / 附件（每次切换都重取：签名链接 10 分钟过期） */
async function fetchPages(kind: string) {
  current.value = kind
  if (!kind) {
    state.value = 'failed'
    return
  }
  // 合并件还没渲染好：先给「生成中」，别去撞那个 404
  if (kind === 'merged' && mergedState.value !== 'ready') {
    state.value = 'generating'
    pollMerged()
    return
  }
  state.value = 'loading'
  try {
    const data = await fetchDocPages(sampleId.value, kind, identity.value)
    pages.value = data.pages ?? []
    // ★ 外部那条只给页面图（#254）：这两个键拿不到就是空数组 —— 「文档中的图片」「附件」两段不渲染
    images.value = data.images ?? []
    attachments.value = data.attachments ?? []
    state.value = 'ready'
  }
  catch {
    if (kind === 'merged') {
      // 合并件 404 与「没这份文档」不可区分 → 统一按「还在生成」处理并轮询
      state.value = 'generating'
      pollMerged()
      return
    }
    state.value = 'failed'
  }
}

/**
 * ★ 合并件轮询（最多 60 秒）：清单里出现 `merged` 行 = 这一版渲染好了 → 重新取 pages。
 * 超时就停在「文档生成中」+ 重试（不死等、不假装成功）。
 */
async function pollMerged() {
  if (pollingMerged.value) {
    return
  }
  pollingMerged.value = true
  try {
    const ok = await waitForMerged(async () => {
      const rows = await fetchSampleDocs(sampleId.value, identity.value)
      applyRows(rows)
      return hasMergedRow(rows)
    })
    if (ok) {
      await fetchPages('merged')
    }
  }
  catch {
    // 轮询期间的网络抖动：停在这一屏，用户可以点「重试」
  }
  finally {
    pollingMerged.value = false
  }
}

/** 进页 / 重试：先取清单（定切换条与合并件在不在），再取当前这一份的页面图 */
async function load() {
  state.value = 'loading'
  try {
    const rows = await fetchSampleDocs(sampleId.value, identity.value)
    applyRows(rows)
    const wanted = initialKind.value || current.value
    const exists = tabs.value.some(tab => tab.docKind === wanted)
    // 指定的那一份不在清单里（比如改内容回了草稿）→ 落到组内第一份，不显示空白页
    await fetchPages(exists ? wanted : (tabs.value[0]?.docKind ?? ''))
  }
  catch {
    state.value = 'failed'
  }
}

/** 切一份 */
async function switchTo(kind: string) {
  if (kind === current.value && state.value === 'ready') {
    return
  }
  await fetchPages(kind)
}

/** 失败 / 生成中之后的重试 */
function retry() {
  if (current.value === 'merged') {
    load()
    return
  }
  fetchPages(current.value)
}

/** DownloadBar 报「合并件渲染好了」→ 重新取 pages（它只下载，不替页面取数） */
async function onDownloadReady() {
  if (current.value === 'merged') {
    const rows = await fetchSampleDocs(sampleId.value, identity.value)
    applyRows(rows)
    await fetchPages('merged')
  }
}

onLoad((options) => {
  sampleId.value = String(options?.sampleId ?? '')
  initialKind.value = String(options?.docKind ?? '')
})

let started = false
onShow(async () => {
  uni.setNavigationBarTitle({ title: '文档预览' })
  if (!store.me) {
    try {
      await store.loadMe()
    }
    catch {
      // /mp/me 失败也把页面试一遍：认不出身份时按外部那条最窄的打
    }
  }
  if (!started) {
    started = true
    await load()
  }
})
</script>

<template>
  <view class="pv">
    <!-- ① 顶部切换条：该样本已完成的几份 + ≥2 份时的「合并」 -->
    <DocTabs
      :tabs="tabs"
      :current="current"
      :merged-state="mergedState"
      @change="switchTo"
    />

    <LoadingState v-if="state === 'loading'" />

    <template v-else-if="state === 'ready'">
      <!-- ② 逐页页面图（点开全屏 + 双指缩放） -->
      <PageImageViewer :pages="pages" />
      <!-- ③ 文档中的图片（缩略用预览图，点开看原图） -->
      <ThumbStrip :images="images" />
      <!-- ④ 附件（微信内置查看器打开） -->
      <AttachmentList :attachments="attachments" />
    </template>

    <!-- ★ 合并件还在异步渲染：给「生成中」+ 可重试（不显示内部错误） -->
    <view v-else-if="state === 'generating'" class="lqg-state">
      <text class="lqg-state__text">{{ stateText('generating') }}</text>
      <text class="lqg-state__text">合并件要等几份都渲染好，通常几秒到一分钟。</text>
      <button class="pv__retry" @click="retry">
        重试
      </button>
    </view>

    <!-- ⑤ 失败：一句人话 + 重试（内部错误不往这里放） -->
    <ErrorState v-else :text="stateText('failed')" @retry="retry" />

    <view class="lqg-bar-spacer" />

    <!-- 底部固定操作栏：格式切换 + 打开 + 发送到微信（与列表弹层同一个组件） -->
    <view v-if="current" class="lqg-bar">
      <DownloadBar
        :sample-id="sampleId"
        :doc-kind="current"
        :identity="identity"
        :no="docNo"
        @ready="onDownloadReady"
        @generating="state = 'generating'"
      />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.pv {
  padding: var(--lqg-sp-5) 0 var(--lqg-sp-5);
}

.pv__retry {
  height: 36px;
  line-height: 36px;
  padding: 0 var(--lqg-sp-8);
  font-size: var(--lqg-fs-body);
  color: var(--lqg-primary);
  background: var(--lqg-card);
  border: 1px solid var(--lqg-primary);
  border-radius: var(--lqg-radius-seg);
}

.pv__retry::after {
  border: none;
}
</style>
