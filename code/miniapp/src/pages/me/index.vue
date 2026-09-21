<script setup lang="ts">
import { computed, ref } from 'vue'
import InternalAdminBlock from '@/components/biz/InternalAdminBlock.vue'
import MeHeader from '@/components/biz/MeHeader.vue'
import MeRow from '@/components/biz/MeRow.vue'
import MeSectionTitle from '@/components/biz/MeSectionTitle.vue'
import LoadState from '@/components/ui/LoadState.vue'
import type { EntryKey } from '@/pages/index/entries'
import {
  ENTRY_KEYS,
  INTERNAL_ADMIN_NOTE,
  ME_TARGET,
  ledgerTarget,
  meSections,
} from '@/pages/index/entries'
import { LOGIN_PAGE, goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
import { normalizeIdentity } from '@/types/identity'
import { bindStatusText, bindStatusTone, unitGroupDisplay } from '@/utils/ext-profile'

// 我的（UI:mp.me）。
//
// 板块按身份**只认 /mp/me 的 identity**，判定全在 meSections()：
// - 所有人：「历史编辑记录」一行（UI:mp.history）
// - 外部：「单位与组别」一行（本张只展示 /mp/me 的值，修改页在 AUTH-GROUP-001）
// - 内部：「内部管理」板块，四个入口进表格页；外部**不渲染**（不是置灰）
// 身份缺失 / 空 / 不认识 → 一个板块都不出，也**不默认当内部**。
//
// 「内部管理」板块底部小字逐字照 UI:mp.me（CR-20260918-07：表格页已有修改入口，
// 这行不再写「修改」，见 entries.ts 的 INTERNAL_ADMIN_NOTE）：
// 核验、冻存取用请到网页工作台
definePage({
  style: {
    navigationBarTitleText: '我的',
  },
})

const store = useUserStore()
const failed = ref(false)

const identity = computed(() => normalizeIdentity(store.identity))
const sections = computed(() => meSections(store.identity))
const hasSection = (key: string) => sections.value.includes(key as any)

/** 内部管理板块的四个入口：固定模板顺序，与首页宫格同一批 */
const adminEntries = computed<EntryKey[]>(() => [...ENTRY_KEYS])

const unitGroup = computed(() => unitGroupDisplay(store.ext))
const unitStatusText = computed(() => bindStatusText(store.ext?.bindStatus))
const unitStatusTone = computed(() => bindStatusTone(store.ext?.bindStatus))

async function refresh() {
  failed.value = false
  try {
    await store.loadMe()
  }
  catch {
    failed.value = true
  }
}

onShow(() => {
  if (!store.me) {
    refresh()
  }
})

function goLogin() {
  uni.reLaunch({ url: LOGIN_PAGE })
}

function onAdminPick(key: EntryKey) {
  goPage(ledgerTarget(key))
}

function logout() {
  uni.showModal({
    title: '退出登录',
    content: '退出后需要重新用微信手机号登录',
    success: async (res) => {
      if (res.confirm) {
        await store.logout()
        uni.reLaunch({ url: LOGIN_PAGE })
      }
    },
  })
}
</script>

<template>
  <view class="me">
    <view class="lqg-card me__head">
      <MeHeader
        :name="store.name"
        :phone-masked="store.phoneMasked"
        :identity="identity"
      />
    </view>

    <LoadState v-if="store.loading && !store.me" state="loading" />

    <template v-else-if="identity">
      <!-- 所有人：历史编辑记录 -->
      <template v-if="hasSection('history')">
        <MeSectionTitle title="我的记录" />
        <view class="lqg-card lqg-card--flush">
          <MeRow
            mark="史"
            title="历史编辑记录"
            desc="找回自己填过的记录，点进去修改"
            @click="goPage(ME_TARGET.history)"
          />
        </view>
      </template>

      <!-- 外部：单位与组别（本张只展示；修改页在 AUTH-GROUP-001） -->
      <template v-if="hasSection('unitGroup')">
        <MeSectionTitle title="单位与组别" />
        <view class="lqg-card lqg-card--flush">
          <MeRow
            mark="单"
            :title="unitGroup"
            desc="改了需要中心重新核验"
            @click="goPage(ME_TARGET.unitGroup)"
          >
            <template #end>
              <text class="lqg-tag" :class="`lqg-tag--${unitStatusTone}`">{{ unitStatusText }}</text>
            </template>
          </MeRow>
        </view>
      </template>

      <!-- 内部：内部管理板块（外部整块不渲染） -->
      <template v-if="hasSection('internalAdmin')">
        <MeSectionTitle title="内部管理" hint="查看 · 筛选 · 导出" />
        <InternalAdminBlock
          :entries="adminEntries"
          :note="INTERNAL_ADMIN_NOTE"
          @pick="onAdminPick"
        />
      </template>

      <!-- 协议与隐私（正文在 SYS-RELEASE-001，本张只放占位页） -->
      <view class="lqg-card lqg-card--flush me__legal">
        <MeRow mark="协" title="用户协议" @click="goPage(ME_TARGET.agreement)" />
        <MeRow
          mark="隐"
          title="隐私政策"
          class="me__line"
          @click="goPage(ME_TARGET.privacy)"
        />
      </view>

      <view class="lqg-card me__logout" @click="logout">
        <text class="me__logout-t">退出登录</text>
      </view>
    </template>

    <!-- 身份缺失 / 空 / 不认识：一个板块都不出 -->
    <view v-else class="lqg-state">
      <text class="lqg-state__text">没能确认你的身份，请重新登录后再试</text>
      <button class="me__btn" @click="failed ? refresh() : goLogin()">
        {{ failed ? '重新加载' : '去登录' }}
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.me {
  padding: var(--lqg-gutter) 0 var(--lqg-sp-8);
}

.me__head {
  margin: 0 var(--lqg-gutter);
}

.me__legal {
  margin: var(--lqg-gap) var(--lqg-gutter) 0;
}

.me__line {
  border-top: 1px solid var(--lqg-line);
}

.me__logout {
  margin: var(--lqg-gap) var(--lqg-gutter) 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.me__logout-t {
  font-size: var(--lqg-fs-title);
  color: var(--lqg-danger);
}

.me__btn {
  height: 40px;
  line-height: 40px;
  padding: 0 var(--lqg-sp-8);
  font-size: var(--lqg-fs-base);
  color: var(--lqg-primary);
  background: var(--lqg-card);
  border: 1px solid var(--lqg-primary);
  border-radius: var(--lqg-radius-seg);
}

.me__btn::after {
  border: none;
}
</style>
