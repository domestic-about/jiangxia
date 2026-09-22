<script setup lang="ts">
import { ref } from 'vue'
import { docKindLabel } from '@/pages/doc/group'

// 预览页**占位**（DOC-MP-001 §2：外部样本详情第三段点条目先进这里）。
//
// 真正的预览与下载（页面图、格式弹层 PDF / Word、打开 / 发送到微信、
// 小程序不能直接存进手机文件夹的那行小字）在 **DOC-MP-002**。
// 本张只保证「列表 / 详情 → 点条目 → 能到这里、参数带得对」这条链路是通的。
// ★ 不要在这里自己拼下载链接：页面图与下载链接都是 10 分钟签名链接，
//   进预览页要重新调一次对应的 pages / download（AUTH-EXT-003 §7.3）。
definePage({
  style: {
    navigationBarTitleText: '文档预览',
  },
})

const sampleId = ref('')
const docKind = ref('')

onLoad((options) => {
  sampleId.value = String(options?.sampleId ?? '')
  docKind.value = String(options?.docKind ?? '')
})

function notYet() {
  uni.showToast({ title: '即将开放', icon: 'none' })
}
</script>

<template>
  <view class="pv">
    <view class="lqg-gl">文档预览</view>
    <view class="lqg-card">
      <view class="pv__row">
        <text class="pv__k">文档</text>
        <text class="pv__v">{{ docKindLabel(docKind) || '—' }}</text>
      </view>
      <view class="pv__row">
        <text class="pv__k">样本</text>
        <text class="pv__v lqg-mono">{{ sampleId || '—' }}</text>
      </view>
    </view>
    <view class="lqg-note pv__note">
      预览与下载在 DOC-MP-002 接上；本页先保证列表点得进来。
    </view>
    <view class="lqg-bar">
      <button class="pv__btn" @click="notYet">
        下载
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.pv {
  padding: var(--lqg-sp-5) 0 calc(var(--lqg-sp-7) + env(safe-area-inset-bottom));
}

.pv__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lqg-sp-5);
  min-height: var(--lqg-cell-h);
  padding: var(--lqg-sp-5) var(--lqg-sp-6);
}

.pv__k {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}

.pv__v {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink);
}

.pv__note {
  margin-top: var(--lqg-sp-5);
}

.pv__btn {
  width: 100%;
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

.pv__btn::after {
  border: none;
}
</style>
