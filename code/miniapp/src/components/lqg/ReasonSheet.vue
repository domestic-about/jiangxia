<script setup lang="ts">
import { ref } from 'vue'
// ★ 显式 .vue 路径导入（SAMPLE-MP-001 坑 6：只靠 easycom 会让该模块的 .js 产物消失）
import WdPopup from 'wot-design-uni/components/wd-popup/wd-popup.vue'
import WdTextarea from 'wot-design-uni/components/wd-textarea/wd-textarea.vue'

// 「判为无效」的原因面板（小程序核验页：样本、石蜡包埋两页共用；底部弹层，落地规范 §5.10）。
//
// ★ 原因合作单位看得到（与工作台同一句提示）；字数上限与后端 `invalid_reason` 列长一致（200）。
// ★ 本组件不判必填、不发请求：点「确认判为无效」把原因抛给页面，页面走同一套自检（`rules.ts`）再提交，
//   被拒时面板**不关**，原因还在，改了再点。
// ★ `notes`：页面想在确认前明说的话（例如石蜡包埋「判为无效不会保存你补填的：切片时间、染色」），不静默丢。
withDefaults(defineProps<{
  /** 面板里额外的提醒（每条一行，琥珀色） */
  notes?: string[]
  /** 提交中：按钮置灰 */
  busy?: boolean
  maxlength?: number
}>(), {
  notes: () => [],
  busy: false,
  maxlength: 200,
})

const emit = defineEmits<{
  (e: 'confirm', reason: string): void
}>()

const show = ref(false)
const reason = ref('')

/** 打开面板（上一次没提交成功的原因保留，方便改了再点） */
function open() {
  show.value = true
}

function close() {
  show.value = false
}

/** 提交成功后由页面调用：清空并关掉 */
function reset() {
  reason.value = ''
  show.value = false
}

defineExpose({ open, close, reset })
</script>

<template>
  <wd-popup
    v-model="show"
    position="bottom"
    custom-style="border-radius: var(--lqg-radius-sheet) var(--lqg-radius-sheet) 0 0"
    @close="close"
  >
    <view class="lqg-sheet">
      <view class="lqg-sheet__head">
        <text class="rs__t">判为无效的原因</text>
        <text class="rs__x" @click="close">取消</text>
      </view>
      <text class="rs__tip">合作单位看得到这句话，写清哪里不对</text>
      <view v-for="note in notes" :key="note" class="lqg-note lqg-note--warn rs__note">
        <text>{{ note }}</text>
      </view>
      <view class="rs__box">
        <wd-textarea
          :model-value="reason"
          placeholder="例如：信息不全，缺住院号"
          :maxlength="maxlength"
          show-word-limit
          no-border
          @update:model-value="(v: string) => reason = v"
        />
      </view>
      <button class="rs__btn" :disabled="busy" @click="emit('confirm', reason)">
        {{ busy ? '正在提交…' : '确认判为无效' }}
      </button>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.rs__t {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.rs__x {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}

.rs__tip {
  display: block;
  margin-top: var(--lqg-sp-3);
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.rs__note {
  margin: var(--lqg-sp-5) 0 0;
}

.rs__box {
  margin-top: var(--lqg-sp-5);
  padding: var(--lqg-sp-3);
  border-radius: var(--lqg-radius-field);
  background: var(--lqg-inset);
}

.rs__btn {
  margin-top: var(--lqg-sp-7);
  height: var(--lqg-btn-h);
  line-height: var(--lqg-btn-h);
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-on-primary);
  background: var(--lqg-danger);
  border: none;
  border-radius: var(--lqg-radius-ctl);
}

.rs__btn::after {
  border: none;
}
</style>
