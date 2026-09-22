<script setup lang="ts">
import type { SampleFormValue } from '@/api/sample'
import type { OcrRecognizeData, OcrUploadTask } from '@/api/ocr'
import { startOcrUpload } from '@/api/ocr'
import { OCR_FAIL_TEXT, mergeOcrPrefill, readOcrResponse, recognizedCount } from '@/pages/sample/ocr/prefill'
import { compressToMaxEdge, isCancelled, pickImage } from '@/pages/sample/ocr/image'

// 拍照识别条（OCR-MP-001）。
//
// 权威：`UI:mp.sample.form.ocr`（表单顶部一条：左「拍照识别」右「从相册选」，下面一行小字
// 「识别结果仅作预填，请核对后提交」；识别中显示进度；成功后被预填的项右侧出现
// 「识别 · 请核对」小标记，用户改动后标记消失；条下方可展开「查看识别到的原文」；
// 识别失败 / 超时：提示「没识别出来，请手动填写」，不阻塞填表；仅新增时显示）·
// `FLOW:F-OCR-01.step1`（wx.chooseMedia 单张 → 压到长边 ≤ 2000px → 上传）·
// `FLOW:F-OCR-01.step4`（只填空项、加标记、可看原文）。
// 视觉：方向 A 落地规范 §5.7（`.lqg-ocr` 一张卡、左实底主按钮右 soft 按钮、高 44 圆角 12、
// 下方 12 弱字说明、末尾青绿文字链；识别中两个按钮置灰并出细进度条；失败只换说明文字、
// 不弹窗、不挡表单）。**本组件不写任何色值**，样式全走 `--lqg-*` token。
//
// 四个状态（`UI:mp.sample.form.ocr` 明写的四段）：
//   idle        空闲：两个按钮 + 一句说明
//   recognizing 识别中：按钮置灰不可点 + 细进度条
//   done        成功：下面出「已识别 N 项 … 查看识别到的原文」
//   failed      失败：说明文字换成 `没识别出来，请手动填写`，**按钮仍在**（还能再试）
//
// 「仅新增时显示」不在这里判 —— 由页面按 `layout.showOcr` 决定挂不挂这个组件
// （SAMPLE-MP-001 的 fixture 已规定编辑态 showOcr=false）。
//
// 隐私（ticket §2）：首次用相机 / 相册前走 `uni.requirePrivacyAuthorize`；它在小程序基础库
// 2.32.3+ 才有，H5 没有 → 特性检测，**拿不到这个 API 就直接继续**（不因为平台差异挡填表）。
// 用途说明写进隐私保护指引（SYS-RELEASE-001 汇总，本票不碰那份文档）。

const props = withDefaults(defineProps<{
  /** 表单当前值（合并是**原地**做的，本组件只读它、不替页面持一份表单） */
  form: SampleFormValue
  /**
   * 测试桩用例（`X-Ocr-Stub-Case`）：只给 H5 端侧取证用（`?stubCase=01` 带进页面），
   * 生产上恒空 —— `StubOcrProvider` 也只在 dev / test 存在。
   */
  stubCase?: string
}>(), {
  stubCase: '',
})

const emit = defineEmits<{
  /** 识别开始（页面据此给表单拍快照，用来判断「某格的值是不是识别写的」） */
  (e: 'recognizing'): void
  (e: 'prefilled', form: SampleFormValue, marks: string[]): void
}>()

type OcrState = 'idle' | 'recognizing' | 'done' | 'failed'

const state = ref<OcrState>('idle')
const progress = ref(0)
const rawLines = ref<string[]>([])
const shownCount = ref(0)
const showRaw = ref(false)
/** 正在进行的上传（用户重选 / 连点两下时 abort 掉上一次，不留悬挂请求） */
let upload: OcrUploadTask | null = null

const busy = computed(() => state.value === 'recognizing')
const tip = computed(() => {
  if (state.value === 'failed') {
    return OCR_FAIL_TEXT
  }
  if (state.value === 'done') {
    return '识别结果已填入下面的空项，请逐项核对后再提交'
  }
  return '识别结果仅作预填，请核对后提交'
})

/** 首次使用相机 / 相册前的隐私授权；**当前环境没有这个 API 就直接放行** */
function ensurePrivacy(): Promise<void> {
  const api = uni as unknown as { requirePrivacyAuthorize?: (options: Record<string, unknown>) => void }
  if (typeof api.requirePrivacyAuthorize !== 'function') {
    return Promise.resolve()
  }
  return new Promise<void>((resolve, reject) => {
    api.requirePrivacyAuthorize!({
      success: () => resolve(),
      // 用户拒了隐私授权：这不是「识别失败」，但也确实拿不到图 —— 同样落失败态，不挡手填
      fail: () => reject(new Error('需要授权相机 / 相册才能识别')),
    })
  })
}

async function start(sourceType: 'camera' | 'album') {
  if (busy.value) {
    return
  }
  // 上一次还在上传就掐掉（用户连点两下 / 换一张图）：不留悬挂请求，也不让旧结果后到覆盖新的
  upload?.abort()
  upload = null
  // 页面在这一刻给表单拍快照：识别回来后要区分「这格的值是识别写的」还是「本来就有值」
  emit('recognizing')
  state.value = 'recognizing'
  progress.value = 0
  // 重来一次时先把上一次的原文收起来：失败态下不该还挂着上一次的原文
  rawLines.value = []
  showRaw.value = false
  try {
    await ensurePrivacy()
    const picked = await pickImage(sourceType)
    const ready = await compressToMaxEdge(picked)
    const started = startOcrUpload(ready.path, {
      stubCase: props.stubCase || undefined,
      onProgress: (p) => {
        progress.value = Math.max(0, Math.min(100, Number(p?.progress) || 0))
      },
    })
    upload = started.task
    const data = await started.done
    upload = null
    apply(data)
  }
  catch (e) {
    upload = null
    // 用户自己取消选图：不是「识别失败」（别弹「没识别出来」吓人），回空闲态
    if (isCancelled(e)) {
      state.value = 'idle'
      return
    }
    state.value = 'failed'
  }
}

/**
 * 把接口结果合并进表单。
 *
 * ★ 这里**没有**「至少识别出一项才算成功」这道闸：识别出一项是成功，一项都没认出来也
 *   是「识别失败」而不是「成功 0 项」—— 后者会让填写人以为系统认识这张图、只是没值，
 *   从而不去手动填写（`UI:mp.sample.form.ocr` 的失败态只有一个：没识别出来）。
 */
function apply(data: OcrRecognizeData) {
  const { ocrFields, rawLines: lines } = readOcrResponse(data as unknown)
  const result = mergeOcrPrefill(props.form, ocrFields)
  const count = recognizedCount(result.marks)
  if (count === 0) {
    state.value = 'failed'
    rawLines.value = lines
    // 一项都没填出来时，原文反而更有用（用户能看出识别服务到底读到了什么）→ 直接展开
    showRaw.value = lines.length > 0
    shownCount.value = 0
    return
  }
  state.value = 'done'
  shownCount.value = count
  rawLines.value = lines
  emit('prefilled', result.form, result.marks)
}

function onCamera() {
  void start('camera')
}

function onAlbum() {
  void start('album')
}

onBeforeUnmount(() => {
  upload?.abort()
  upload = null
})
</script>

<template>
  <view class="lqg-ocr ocr">
    <view class="lqg-ocr__row">
      <button
        class="lqg-ocr__btn lqg-ocr__btn--p"
        :class="{ 'ocr__btn--off': busy }"
        :disabled="busy"
        @click="onCamera"
      >
        拍照识别
      </button>
      <button
        class="lqg-ocr__btn lqg-ocr__btn--s"
        :class="{ 'ocr__btn--off': busy }"
        :disabled="busy"
        @click="onAlbum"
      >
        从相册选
      </button>
    </view>

    <!-- 识别中的细进度条（§5.7）；成功 / 空闲时不出这条 -->
    <view v-if="busy" class="ocr__bar">
      <view class="ocr__bar-fill" :style="{ width: `${progress}%` }" />
    </view>

    <view class="ocr__foot">
      <text class="lqg-ocr__tip ocr__tip" :class="{ 'ocr__tip--fail': state === 'failed' }">{{ tip }}</text>
      <text v-if="state === 'done'" class="ocr__count">已识别 {{ shownCount }} 项</text>
    </view>

    <!-- 原文折叠区：成功与失败都能看（失败时是唯一能自证的东西） -->
    <view v-if="rawLines.length" class="ocr__raw">
      <text class="ocr__raw-link" @click="showRaw = !showRaw">
        {{ showRaw ? '收起识别到的原文' : '查看识别到的原文' }}
      </text>
      <view v-if="showRaw" class="ocr__raw-box">
        <text v-for="(line, i) in rawLines" :key="i" class="ocr__raw-line">{{ line }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 只补落地规范 §5.7 里`.lqg-ocr` 没覆盖的两件：置灰、进度条、原文区、右侧计数 */
.ocr__btn--off {
  opacity: 0.5;
}

.ocr__bar {
  margin-top: var(--lqg-sp-4);
  height: 3px;
  border-radius: var(--lqg-radius-badge);
  background: var(--lqg-inset);
  overflow: hidden;
}

.ocr__bar-fill {
  height: 3px;
  background: var(--lqg-primary);
}

.ocr__foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--lqg-sp-3);
  margin-top: var(--lqg-sp-4);
}

.ocr__tip {
  margin-top: 0;
  flex: 1;
}

/* 失败只换文字颜色，不弹窗、不加红块（§5.7） */
.ocr__tip--fail {
  color: var(--lqg-warn);
}

.ocr__count {
  flex: none;
  font-size: var(--lqg-fs-sm);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-primary);
}

.ocr__raw {
  margin-top: var(--lqg-sp-3);
}

.ocr__raw-link {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-primary);
}

.ocr__raw-box {
  margin-top: var(--lqg-sp-3);
  padding: var(--lqg-sp-4) var(--lqg-sp-5);
  border-radius: var(--lqg-radius-ctl);
  background: var(--lqg-inset);
}

.ocr__raw-line {
  display: block;
  font-family: var(--lqg-font-mono);
  font-size: var(--lqg-fs-xs);
  color: var(--lqg-ink-2);
  line-height: 1.7;
}
</style>
