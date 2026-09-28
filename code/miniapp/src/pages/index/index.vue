<script setup lang="ts">
import { computed, ref } from 'vue'
import type { HomeTodo } from '@/api/verify'
import { fetchHomeTodo } from '@/api/verify'
import EntryGrid from '@/components/biz/EntryGrid.vue'
import TodoCard from '@/components/biz/TodoCard.vue'
import IdentityBar from '@/components/ui/IdentityBar.vue'
import LineIcon from '@/components/ui/LineIcon.vue'
import LoadState from '@/components/ui/LoadState.vue'
import type { EntryKey } from '@/pages/index/entries'
import { entryTarget, entriesFor } from '@/pages/index/entries'
import type { TodoItem } from '@/pages/index/todo'
import { showTodo, todoAllClear, todoItems } from '@/pages/index/todo'
import { LOGIN_PAGE, goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
import { normalizeIdentity } from '@/types/identity'
import { UNBOUND_HINT, normalizeBindStatus, unitGroupWithStatus } from '@/utils/ext-profile'

// 首页（UI:mp.home / UI:mp.home.entries / FLOW:F-MP-01.step1）。
//
// 口径（CR-20260917-05，内部首页的「待处理」按甲方 2026-09-24 第 17 行改）：
// - 外部：问候行 + 身份徽标 → 「填写」宫格，没有任何数字（不变）
// - 内部：问候行 → **「待处理」一块**（待核验样本 / 待核验石蜡包埋送样 / -80 超期未转液氮，
//   数来自 `GET /lqg/home/todo`，与工作台首页同一次现算）→ 「填写」宫格。
//   每次页面显示都重取；加载中、失败（点一下重试）各有样子，失败不影响下面的填写入口。
//   显示规则、点击去向全在 `todo.ts`（fixture 驱动），本页不判断数字。
// - 最近记录仍然没有；入口宫格上不挂小红点
// - 内部 2×2 四格，外部三格（没有 -80 冻存记录），第三格横向占满
// - 宫格图标是线性图标（Kevin 2026-09-24 本机验收：单字方块换成与「我的」同一套图标）
// - 点哪格都是进该表的填写页新增一条（内部外部一样）
// - 身份只认 /mp/me 的 identity：缺失 / 空 / 不认识 → 一个入口都不渲染，**绝不默认当内部**
definePage({
  style: {
    navigationBarTitleText: '类器官送检',
  },
})

const store = useUserStore()
const failed = ref(false)

/** 「待处理」：接口原样结果（null = 还没取到过）与本次取数状态 */
const todo = ref<HomeTodo | null>(null)
const todoFailed = ref(false)

const identity = computed(() => normalizeIdentity(store.identity))
const entries = computed<EntryKey[]>(() => entriesFor(store.identity))

/** 这一块只给内部（外部首页一个字都不变） */
const todoVisible = computed(() => showTodo(store.identity))
const todoList = computed<TodoItem[]>(() => todoItems(todo.value))
const todoClear = computed(() => todoAllClear(todo.value))
/** 取到过数就一直显示数（再次显示页面时后台刷新，不闪骨架）；没取到过才是加载中 / 失败 */
const todoState = computed<'loading' | 'error' | 'ready'>(() => {
  if (todo.value) {
    return 'ready'
  }
  return todoFailed.value ? 'error' : 'loading'
})

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

/** 取「待处理」三项的数（只给内部；失败只影响这一块） */
async function loadTodo() {
  if (!showTodo(store.identity)) {
    return
  }
  todoFailed.value = false
  try {
    todo.value = await fetchHomeTodo()
  }
  catch {
    todoFailed.value = true
    // 刷新失败时丢掉旧数：宁可显示「没能加载」，也不挂着一个可能已经过时的数
    todo.value = null
  }
}

// tab 页每次显示都刷新一次，保证切换单位 / 核验后回来是最新的（「待处理」的数也跟着重取）
onShow(async () => {
  await ensureLoaded()
  loadTodo()
})

function onTodoPick(item: TodoItem) {
  goPage(item.target)
}

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
      <!-- 内部：待处理（外部不渲染）；失败只影响这一块 -->
      <TodoCard
        v-if="todoVisible"
        :state="todoState"
        :items="todoList"
        :all-clear="todoClear"
        @pick="onTodoPick"
        @retry="loadTodo"
      />
      <EntryGrid :entries="entries" @pick="onPick" />
      <!-- 底部说明前置时钟图标（落地规范 §5.2），与宫格同一套线性图标 -->
      <view class="lqg-note">
        <LineIcon name="clock" :size="16" />
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
