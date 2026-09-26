<script setup lang="ts">
import { ref } from 'vue'
import type { MockSeed } from '@/api/mock-seeds'
import { HOME_PAGE } from '@/router/config'
import { MOCK_PANEL, MOCK_SEEDS, mockCodes } from '@/api/mock-seeds'
import { LEGAL_OPERATOR } from '@/config/legal'
import { useUserStore } from '@/store/user'

// 登录页（UI:mp.login）。
//
// 一屏：实验室名称与一句话说明 → 协议勾选 → 一个主按钮「微信手机号快捷登录」
// （`open-type="getPhoneNumber"`）。**没有**「内部登录 / 外部登录」两个入口，没有账号密码框。
// 拒绝授权手机号 → 停在本页并提示「需要手机号才能送检和查看结果」。
//
// 流程：wx.login 拿 code → 按钮回调拿 phoneCode → POST /auth/login（grantType=xcx）
// → 存 token → GET /mp/me 入 store → 进首页。
//
// 测试身份登录入口（ADR-0008）：开了 mock 登录的构建（本地开发、测试环境体验版）里才有；
// 生产构建里整段是死代码被摇掉，产物里搜不到 seed 清单、`mock:` 前缀与面板文案（见 api/mock-seeds.ts）。
definePage({
  style: {
    navigationBarTitleText: '登录',
  },
})

const store = useUserStore()
/** 实验室名称（UI:mp.login「实验室名称与一句话说明」，G23）= 协议里的运营方，同一个配置 */
const labName = LEGAL_OPERATOR
const agreed = ref(false)
const submitting = ref(false)

// 只有 dev / test 构建 + VITE_MOCK_LOGIN=1 才挂这个入口；判据只有一份：vite.config.ts 里 define 的
// 构建期常量 `__LQG_MOCK_LOGIN__`。面板文案、身份清单在 api/mock-seeds.ts —— 关着时这里直接取
// null / 空数组，模板里只剩一个恒假的 wx:if，文案、清单与 mock-seeds 模块都不进生产包。
//
// ★ 必须在**本模块里**直接用 `__LQG_MOCK_LOGIN__` 判断，不能包 computed、也不能只靠 import 进来的常量：
//   那两种写法打包器都看不出它恒假，`onMockLogin` 与整份 seed 清单会原样留在生产包里
//   （D8 独立验收实测过：旧写法的生产包里 api/mock-seeds.js 带着全部手机号）。
//
// ★ SYS-STAGING-001 起 test 也算：测试环境的体验版就是给甲方试用 seed 数据的，
//   而甲方主体的 appid 还没拿到（SYS-RELEASE-001），真实微信登录换不出 seed 里绑定的 openid
//   → 登进去是个空系统。ADR-0008 本来就允许 test profile 开 mock（后端 application-test.yml
//   的 mock-login=true）。生产构建（mode=production）不在此列。
//
// 注意：模板里**不要**写 `<!-- #ifdef/#ifndef -->` 条件编译注释——uni 的 html
// 预处理器会和 @uni-ku/root 的根节点注入打架，构建报
// `Cannot destructure property 'tabBar' of 'this.meta'`（本票踩过，已实测）。
const mockPanel = __LQG_MOCK_LOGIN__ ? MOCK_PANEL : null
const mockSeeds = __LQG_MOCK_LOGIN__ ? MOCK_SEEDS : []

function toast(title: string) {
  uni.showToast({ title, icon: 'none' })
}

async function afterLogin() {
  uni.reLaunch({ url: HOME_PAGE })
}

/** 真实路径：wx.login 拿 code */
function getWxCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: res => resolve(res.code),
      fail: () => reject(new Error('没能获取微信登录凭证')),
    })
  })
}

async function doLogin(xcxCode: string, phoneCode: string) {
  if (submitting.value) {
    return
  }
  submitting.value = true
  try {
    await store.login({ xcxCode, phoneCode })
    await afterLogin()
  }
  catch (e) {
    toast(e instanceof Error ? e.message : '登录失败，请重试')
  }
  finally {
    submitting.value = false
  }
}

/** 主按钮：微信手机号快捷登录 */
async function onGetPhoneNumber(e: any) {
  if (!agreed.value) {
    toast('请先阅读并勾选《用户协议》与《隐私政策》')
    return
  }
  const detail = e?.detail || {}
  if (!detail.code) {
    // 用户拒绝授权手机号：停在本页
    toast('需要手机号才能送检和查看结果')
    return
  }
  try {
    const xcxCode = await getWxCode()
    await doLogin(xcxCode, detail.code)
  }
  catch (err) {
    toast(err instanceof Error ? err.message : '登录失败，请重试')
  }
}

// 测试身份入口：选一个 seed 身份直接登（只在开了 mock 登录的构建里存在）。
// ★ 第一行的常量判断别删：模板里对 setup 绑定的引用会包一层 unref，打包器折叠不掉那个恒假分支，
//   本函数仍被引用；靠这一行让函数体在生产构建里成为死代码，mock-seeds 模块才整个不进包。
function onMockLogin(seed: MockSeed) {
  if (!__LQG_MOCK_LOGIN__) {
    return
  }
  if (!agreed.value) {
    toast('请先阅读并勾选《用户协议》与《隐私政策》')
    return
  }
  const codes = mockCodes(seed)
  if (!codes) {
    return
  }
  doLogin(codes.xcxCode, codes.phoneCode)
}

function openAgreement() {
  uni.navigateTo({ url: '/pages/legal/agreement' })
}

function openPrivacy() {
  uni.navigateTo({ url: '/pages/legal/privacy' })
}
</script>

<template>
  <view class="login">
    <view class="login__hero">
      <text class="login__lab">{{ labName }}</text>
      <text class="login__title">类器官送检</text>
      <text class="login__slogan">送检、收样、包埋、冻存，一个入口记到底</text>
    </view>

    <view class="lqg-card login__card">
      <view class="login__agree" @click="agreed = !agreed">
        <view class="login__box" :class="{ 'login__box--on': agreed }">
          <text v-if="agreed" class="login__tick">✓</text>
        </view>
        <view class="login__terms">
          <text class="login__term">我已阅读并同意</text>
          <text class="login__link" @click.stop="openAgreement">《用户协议》</text>
          <text class="login__term">与</text>
          <text class="login__link" @click.stop="openPrivacy">《隐私政策》</text>
        </view>
      </view>

      <button
        class="login__btn"
        open-type="getPhoneNumber"
        :disabled="submitting"
        @getphonenumber="onGetPhoneNumber"
      >
        微信手机号快捷登录
      </button>

      <text class="login__tip">首次登录会用你的微信手机号创建账号，不需要注册</text>
    </view>

    <!-- 测试身份入口：只在开了 mock 登录的构建里有内容（文案与清单都来自 api/mock-seeds.ts） -->
    <view v-if="mockPanel" class="lqg-card login__mock">
      <text class="login__mock-t">{{ mockPanel.title }}</text>
      <text class="login__mock-d">{{ mockPanel.desc }}</text>
      <button
        v-for="item in mockSeeds"
        :key="item.key"
        class="login__mock-btn"
        @click="onMockLogin(item)"
      >
        {{ item.label }}
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.login {
  padding: var(--lqg-sp-8) var(--lqg-gutter);
}

.login__hero {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-3);
  padding: 40px 0 var(--lqg-sp-8);
}

.login__lab {
  font-size: var(--lqg-fs-base);
  font-weight: var(--lqg-fw-medium);
  color: var(--lqg-primary);
}

.login__title {
  font-size: 27px;
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.login__slogan {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-2);
}

.login__card {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-7);
}

.login__agree {
  display: flex;
  align-items: flex-start;
  gap: var(--lqg-sp-3);
}

.login__box {
  width: 18px;
  height: 18px;
  flex: none;
  margin-top: 1px;
  border: 1px solid var(--lqg-line);
  border-radius: var(--lqg-radius-badge);
  display: flex;
  align-items: center;
  justify-content: center;
}

.login__box--on {
  background: var(--lqg-primary);
  border-color: var(--lqg-primary);
}

.login__tick {
  font-size: var(--lqg-fs-xs);
  color: var(--lqg-on-primary);
}

.login__terms {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px;
}

.login__term {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-2);
}

.login__link {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-primary);
}

.login__btn {
  height: var(--lqg-btn-h);
  line-height: var(--lqg-btn-h);
  font-size: var(--lqg-fs-lg);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-on-primary);
  background: var(--lqg-primary);
  border-radius: var(--lqg-radius-ctl);
  box-shadow: var(--lqg-shadow-brand);
}

.login__btn::after {
  border: none;
}

.login__tip {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
  text-align: center;
}

.login__mock {
  margin-top: var(--lqg-gap);
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-3);
}

.login__mock-t {
  font-size: var(--lqg-fs-body);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.login__mock-d {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.login__mock-btn {
  height: 40px;
  line-height: 40px;
  font-size: var(--lqg-fs-base);
  color: var(--lqg-primary);
  background: var(--lqg-primary-soft);
  border-radius: var(--lqg-radius-seg);
}

.login__mock-btn::after {
  border: none;
}
</style>
