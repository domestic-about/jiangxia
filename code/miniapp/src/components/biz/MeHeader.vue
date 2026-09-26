<script setup lang="ts">
import { computed } from 'vue'
import LineIcon from '@/components/ui/LineIcon.vue'
import type { ResolvedIdentity } from '@/types/identity'
import { identityLabel, identityTagClass } from '@/types/identity'

// 「我的」页头（UI:mp.me，落地规范 §5.3）：
// 头像（缺省显示姓的首字）→ 姓名 + 身份徽标 → 手机号（中间四位掩码）→ 所属单位一行。
//
// Kevin 2026-09-24 本机验收「我的页面还是需要添加一些设计感」，页头做出层次：
// - 卡片底是一层很浅的身份色渐变（内部青绿、合作单位蓝，都是 tokens.scss 里已有的 soft 色），
//   右上角压一个放大的淡色类器官图形做底纹 —— 不引入新的颜色；
// - 头像是身份色实心圆 + 白字首字，外圈一道白边；
// - 手机号、单位两行各带一个线性小图标，单位那一行上面有一条细分隔线。
// 单位这一行由页面传（内部 = 中心名称；合作单位 = 「单位 · 组别」），空串就不出这一行。
// 身份徽标只用 `.lqg-tag` + 身份修饰类，颜色不在页面里写。
const props = withDefaults(defineProps<{
  name?: string
  phoneMasked?: string
  identity?: ResolvedIdentity
  /** 所属单位那一行（内部：中心名称；合作单位：单位 · 组别）；空串不显示 */
  org?: string
}>(), {
  name: '',
  phoneMasked: '',
  identity: null,
  org: '',
})

const initial = computed(() => (props.name ? props.name.slice(0, 1) : '·'))
const label = computed(() => identityLabel(props.identity))
const tagClass = computed(() => identityTagClass(props.identity))
const phone = computed(() => props.phoneMasked || '手机号未知')
/** 合作单位用蓝色那一套（与身份徽标同色系），其余一律青绿 */
const toneClass = computed(() => (props.identity === 'external' ? 'mehead--ext' : ''))
</script>

<template>
  <view class="mehead" :class="toneClass">
    <view class="mehead__deco">
      <LineIcon name="organoid" :size="124" />
    </view>

    <view class="mehead__top">
      <view class="mehead__av">
        <text class="mehead__avt">{{ initial }}</text>
      </view>
      <view class="mehead__id">
        <view class="mehead__row">
          <text class="mehead__name">{{ name || '未命名' }}</text>
          <text v-if="label" class="lqg-tag" :class="tagClass">{{ label }}</text>
        </view>
        <view class="mehead__meta">
          <LineIcon name="phone" :size="14" />
          <text class="mehead__phone lqg-mono">{{ phone }}</text>
        </view>
      </view>
    </view>

    <view v-if="org" class="mehead__org">
      <view class="mehead__org-icon">
        <LineIcon name="unit" :size="16" />
      </view>
      <text class="mehead__org-t">{{ org }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 卡片外观（圆角、阴影、裁边）由页面的 `.lqg-card.lqg-card--flush` 给；这里铺一层浅渐变与底纹 */
.mehead {
  position: relative;
  padding: var(--lqg-sp-8) var(--lqg-sp-7) var(--lqg-sp-7);
  background: linear-gradient(135deg, var(--lqg-primary-soft) 0%, var(--lqg-card) 68%);
  color: var(--lqg-primary);
}

.mehead--ext {
  background: linear-gradient(135deg, var(--lqg-ext-soft) 0%, var(--lqg-card) 68%);
  color: var(--lqg-ext);
}

/* 右上角的淡色底纹：身份色、很低的不透明度，只做气氛，不挡字 */
.mehead__deco {
  position: absolute;
  top: -26px;
  right: -22px;
  opacity: 0.07;
}

.mehead__top {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-6);
}

.mehead__av {
  width: 56px;
  height: 56px;
  flex: none;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0 3px var(--lqg-card), var(--lqg-shadow-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}

.mehead__avt {
  font-size: var(--lqg-fs-greet);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-on-primary);
}

.mehead__id {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-2);
  min-width: 0;
}

.mehead__row {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-3);
}

.mehead__name {
  font-size: var(--lqg-fs-greet);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.mehead__meta {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-1);
  color: var(--lqg-ink-3);
}

.mehead__phone {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-2);
}

.mehead__org {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-3);
  margin-top: var(--lqg-sp-7);
  padding-top: var(--lqg-sp-5);
  border-top: 1px solid var(--lqg-line);
}

.mehead__org-icon {
  display: flex;
  flex: none;
}

.mehead__org-t {
  min-width: 0;
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-2);
}
</style>
