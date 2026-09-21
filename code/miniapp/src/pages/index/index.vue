<script setup lang="ts">
import { computed, ref } from 'vue'
import EntryGrid from '@/components/biz/EntryGrid.vue'
import IdentityBar from '@/components/ui/IdentityBar.vue'
import LoadState from '@/components/ui/LoadState.vue'
import type { EntryKey } from '@/pages/index/entries'
import { entryTarget, entriesFor } from '@/pages/index/entries'
import { LOGIN_PAGE, goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
import { normalizeIdentity } from '@/types/identity'
import { UNBOUND_HINT, normalizeBindStatus, unitGroupWithStatus } from '@/utils/ext-profile'

// 首页（UI:mp.home / UI:mp.home.entries / FLOW:F-MP-01.step1）。
//
// 口径（CR-20260917-05）：
// - 内部与外部同一个样子：问候行 + 身份徽标 → 「填写」宫格
// - **没有数字摘要、没有最近记录、入口上不挂小红点**
// - 内部 2×2 四格，外部三格（没有 -80 冻存记录），第三格横向占满
// - 点哪格都是进该表的填写页新增一条（内部外部一样）
// - 身份只认 /mp/me 的 identity：缺失 / 空 / 不认识 → 一个入口都不渲染，**绝不默认当内部**
definePage({
  style: {
    navigationBarTitleText: '类器官送检',
  },
})

const store = useUserStore()
const failed = ref(false)

const identity = computed(() => normalizeIdentity(store.identity))
const entries = computed<EntryKey[]>(() => entriesFor(store.identity))

/** 外部那一行：单位 · 组别 · 核验状态 */
const externalLine = computed(() => unitGroupWithStatus(store.ext))
const showUnboundHint = computed(() =>
  identity.value === 'external' && normalizeBindStatus(store.ext?.bindStatus) === 'unbound',
)

/** 拉一次 /mp/me；失败时给重试，但不猜身份 */
async function refresh() {
  failed.value = false
  try {
    await store.loadMe()
  }
  catch {
    failed.value = true
  }
}

async function ensureLoaded() {
  if (!store.me) {
    await refresh()
  }
}

// tab 页每次显示都刷新一次，保证切换单位 / 核验后回来是最新的
onShow(() => {
  ensureLoaded()
})

function onPick(key: EntryKey) {
  const target = entryTarget(store.identity, key)
  if (!target) {
    // 身份未知时点不到这里（宫格整体不渲染）；兜底给一句人话，别静默
    uni.showToast({ title: '登录状态已失效，请重新登录', icon: 'none' })
    return
  }
  goPage(target)
}

function goLogin() {
  uni.reLaunch({ url: LOGIN_PAGE })
}

function retry() {
  refresh()
}
</script>

<template>
  <view class="home">
    <IdentityBar
      :name="store.name"
      :identity="identity"
      :secondary="identity === 'external' ? externalLine : ''"
    />

    <!-- 外部档案未绑定：提示去补单位与组别（AUTH-GROUP-001） -->
    <view v-if="showUnboundHint" class="lqg-note lqg-note--warn" @click="goPage('/pages/me/unit-group')">
      <text>{{ UNBOUND_HINT }}</text>
    </view>

    <LoadState v-if="store.loading && !store.me" state="loading" />

    <template v-else-if="identity">
      <EntryGrid :entries="entries" @pick="onPick" />
      <view class="lqg-note">
        <text>填过的记录在「我的 · 历史编辑记录」里找回和修改</text>
      </view>
    </template>

    <!-- 身份缺失 / 为空 / 不认识：一个入口都不渲染（绝不默认当内部） -->
    <view v-else class="lqg-state">
      <text class="lqg-state__text">没能确认你的身份，请重新登录后再试</text>
      <button class="home__btn" @click="failed ? retry() : goLogin()">
        {{ failed ? '重新加载' : '去登录' }}
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.home {
  padding-bottom: var(--lqg-sp-8);
}

.home__btn {
  height: 40px;
  line-height: 40px;
  padding: 0 var(--lqg-sp-8);
  font-size: var(--lqg-fs-base);
  color: var(--lqg-primary);
  background: var(--lqg-card);
  border: 1px solid var(--lqg-primary);
  border-radius: var(--lqg-radius-seg);
}

.home__btn::after {
  border: none;
}
</style>
