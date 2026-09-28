<script setup lang="ts">
import { computed } from 'vue'
import type { LegalSection } from '@/config/legal'
import { LEGAL_CONTACT, LEGAL_OPERATOR, LEGAL_PENDING_TEXT, LEGAL_UPDATED } from '@/config/legal'

// 《用户协议》《隐私政策》两页共用的排版（V06）：一张白卡，标题 → 更新日期与运营方 → 引言 →
// 分节正文 → 「联系我们」。正文由页面传进来（每页的文字留在各自的页面文件里）；
// 主体名称与联系方式只从 `src/config/legal.ts` 取，没定的联系方式显示醒目的「上线前公布」。

withDefaults(defineProps<{
  title: string
  intro?: string
  sections: LegalSection[]
}>(), {
  intro: '',
})

const contacts = computed(() => [
  { key: 'phone', label: '电话', value: LEGAL_CONTACT.phone, mono: true },
  { key: 'email', label: '邮箱', value: LEGAL_CONTACT.email, mono: false },
])
</script>

<template>
  <view class="legal">
    <view class="lqg-card legal__card">
      <text class="legal__title">{{ title }}</text>
      <text class="legal__meta">更新日期：{{ LEGAL_UPDATED }}</text>
      <text class="legal__meta">运营方：{{ LEGAL_OPERATOR }}</text>
      <text v-if="intro" class="legal__p legal__intro">{{ intro }}</text>

      <view v-for="sec in sections" :key="sec.title" class="legal__sec">
        <text class="legal__h">{{ sec.title }}</text>
        <text v-for="(para, idx) in sec.paras" :key="idx" class="legal__p">{{ para }}</text>
      </view>

      <view class="legal__sec">
        <text class="legal__h">联系我们</text>
        <text class="legal__p">对本文件或你的信息有任何疑问、意见，或者要查询、更正、删除你的信息，可以通过下面的方式联系我们：</text>
        <view class="legal__row">
          <text class="legal__k">运营方</text>
          <text class="legal__v">{{ LEGAL_OPERATOR }}</text>
        </view>
        <view v-for="item in contacts" :key="item.key" class="legal__row">
          <text class="legal__k">{{ item.label }}</text>
          <text v-if="item.value" class="legal__v" :class="{ 'lqg-mono': item.mono }">{{ item.value }}</text>
          <text v-else class="lqg-tag lqg-tag--pending">{{ LEGAL_PENDING_TEXT }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.legal {
  padding: var(--lqg-gap) var(--lqg-gutter) var(--lqg-sp-8);
}

.legal__card {
  display: flex;
  flex-direction: column;
}

.legal__title {
  display: block;
  font-size: var(--lqg-fs-nav);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.legal__meta {
  display: block;
  margin-top: var(--lqg-sp-2);
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.legal__intro {
  margin-top: var(--lqg-sp-6);
}

.legal__sec {
  margin-top: var(--lqg-sp-7);
}

.legal__h {
  display: block;
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.legal__p {
  display: block;
  margin-top: var(--lqg-sp-3);
  font-size: var(--lqg-fs-base);
  line-height: 1.75;
  color: var(--lqg-ink-2);
}

.legal__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 40px;
  margin-top: var(--lqg-sp-2);
  border-top: 1px solid var(--lqg-line);
}

.legal__k {
  flex: none;
  font-size: var(--lqg-fs-base);
  color: var(--lqg-ink-3);
}

.legal__v {
  margin-left: var(--lqg-sp-5);
  font-size: var(--lqg-fs-base);
  color: var(--lqg-ink);
  text-align: right;
}
</style>
