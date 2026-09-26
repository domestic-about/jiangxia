<script setup lang="ts">
import { computed, ref } from 'vue'
import InternalAdminBlock from '@/components/biz/InternalAdminBlock.vue'
import MeHeader from '@/components/biz/MeHeader.vue'
import MeRow from '@/components/biz/MeRow.vue'
import MeSectionTitle from '@/components/biz/MeSectionTitle.vue'
import LineIcon from '@/components/ui/LineIcon.vue'
import LoadState from '@/components/ui/LoadState.vue'
import { LEGAL_OPERATOR } from '@/config/legal'
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
// - 外部：「单位与组别」一行（当前值只认 /mp/me，显示在页头；这一行进修改页 AUTH-GROUP-001，右侧是核验状态）
// - 内部：「内部管理」板块，四个入口进表格页；外部**不渲染**（不是置灰）
// 身份缺失 / 空 / 不认识 → 一个板块都不出，也**不默认当内部**。
//
// 「内部管理」板块底部小字见 entries.ts 的 INTERNAL_ADMIN_NOTE（CR-20260918-07：这行不写「修改」；
// 甲方 2026-09-24 第 20 行：核验、冻存登记小程序里也能做了，不再把人支到网页工作台）：
// 核验、冻存登记在小程序和网页工作台都能做
//
// Kevin 2026-09-24 本机验收（「我的页面还是需要添加一些设计感，加一些图标设计」）：
// - 页头卡片分层：浅身份色渐变、实心圆头像、身份徽标、手机号，再一行所属单位
//  （内部 = 中心名称，取协议里的运营方 `LEGAL_OPERATOR`；合作单位 = 「单位 · 组别」）；
// - 行首一律是线性图标（与首页宫格同一套），行高、右箭头、分隔线统一由 MeRow 给；
//   每组卡片上面都有一个分组标题（协议两行也归到「协议与隐私」下）；
// - 合作单位那一行改成「修改单位与组别」+ 核验状态：当前的单位 · 组别已经在页头里，不在两处重复。
// ★ 页面根节点的 class **不能叫 `me`**：UnoCSS 把 `me` 当成工具类生成 `.me { margin-inline-end: 1rem }`，
//   整页右边多出约 16px，卡片右侧留白比左侧大（Kevin 截图里那一处）。改名 `mine`。
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
/** 页头「所属单位」那一行：内部是中心名称，合作单位是「单位 · 组别」 */
const org = computed(() => {
  if (identity.value === 'internal') {
    return LEGAL_OPERATOR
  }
  return identity.value === 'external' ? unitGroup.value : ''
})
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
  <view class="mine">
    <view class="lqg-card lqg-card--flush mine__head">
      <MeHeader
        :name="store.name"
        :phone-masked="store.phoneMasked"
        :identity="identity"
        :org="org"
      />
    </view>

    <LoadState v-if="store.loading && !store.me" state="loading" />

    <template v-else-if="identity">
      <!-- 所有人：历史编辑记录 -->
      <template v-if="hasSection('history')">
        <MeSectionTitle title="我的记录" />
        <view class="lqg-card lqg-card--flush mine__card">
          <MeRow
            icon="history"
            title="历史编辑记录"
            desc="找回自己填过的记录，点进去修改"
            @click="goPage(ME_TARGET.history)"
          />
        </view>
      </template>

      <!-- 外部：单位与组别（当前值在页头；这一行进修改页，右侧是核验状态） -->
      <template v-if="hasSection('unitGroup')">
        <MeSectionTitle title="单位与组别" />
        <view class="lqg-card lqg-card--flush mine__card">
          <MeRow
            icon="unit"
            title="修改单位与组别"
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

      <!-- 协议与隐私（正文在 SYS-RELEASE-001） -->
      <MeSectionTitle title="协议与隐私" />
      <view class="lqg-card lqg-card--flush mine__card">
        <MeRow icon="agreement" title="用户协议" @click="goPage(ME_TARGET.agreement)" />
        <MeRow icon="privacy" title="隐私政策" line @click="goPage(ME_TARGET.privacy)" />
      </view>

      <view class="lqg-card mine__logout" @click="logout">
        <LineIcon name="logout" :size="18" />
        <text class="mine__logout-t">退出登录</text>
      </view>
    </template>

    <!-- 身份缺失 / 空 / 不认识：一个板块都不出 -->
    <view v-else class="lqg-state">
      <text class="lqg-state__text">没能确认你的身份，请重新登录后再试</text>
      <button class="mine__btn" @click="failed ? refresh() : goLogin()">
        {{ failed ? '重新加载' : '去登录' }}
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 根节点别叫 `me`（UnoCSS 的 margin-inline-end 工具类，见文件头）；左右留白全靠各卡片自己的屏边距 16 */
.mine {
  padding: var(--lqg-gutter) 0 var(--lqg-sp-8);
}

.mine__head {
  margin: 0 var(--lqg-gutter);
}

/* 分组卡左右留屏边距（落地规范 §7：屏边距 16；以前这两张贴着屏幕边缘，G14） */
.mine__card {
  margin: 0 var(--lqg-gutter);
}

/* 退出登录单独一张卡，红字居中（落地规范 §5.3），前面一个同色的线性图标 */
.mine__logout {
  margin: var(--lqg-sp-8) var(--lqg-gutter) 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--lqg-sp-2);
  color: var(--lqg-danger);
}

.mine__logout-t {
  font-size: var(--lqg-fs-title);
  color: var(--lqg-danger);
}

.mine__btn {
  height: 40px;
  line-height: 40px;
  padding: 0 var(--lqg-sp-8);
  font-size: var(--lqg-fs-base);
  color: var(--lqg-primary);
  background: var(--lqg-card);
  border: 1px solid var(--lqg-primary);
  border-radius: var(--lqg-radius-seg);
}

.mine__btn::after {
  border: none;
}
</style>
