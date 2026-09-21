<script setup lang="ts">
import { computed, ref } from 'vue'
import UnitGroupPicker from '@/components/biz/UnitGroupPicker.vue'
import LoadState from '@/components/ui/LoadState.vue'
import type { ProfileUpdatePayload, SelectorUnit } from '@/api/unit-group'
import { fetchUnits, saveProfile } from '@/api/unit-group'
import { HOME_PAGE, goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
import { bindStatusText, bindStatusTone, normalizeBindStatus, unitDisplay } from '@/utils/ext-profile'

// 「我的 → 单位与组别」（UI:mp.me.profile，AUTH-GROUP-001）
//
// 姓名输入框 + UnitGroupPicker（单位 → 组别联动，末项「列表里没有，手动填写」）。
// 保存成功即回到「待核验」；被驳回时显示驳回原因。
// 顶部说明照权威逐字：「核验通过后，可与同组同事互看样本」。
definePage({
  style: {
    navigationBarTitleText: '单位与组别',
  },
})

const store = useUserStore()

const loading = ref(false)
const saving = ref(false)
const loadFailed = ref(false)

const units = ref<SelectorUnit[]>([])

const realName = ref('')
const unitId = ref<string | number | null>(null)
const groupId = ref<string | number | null>(null)
const unitNameInput = ref('')
const groupNameInput = ref('')

/** 当前核验状态（保存成功后由 /mp/me 回填） */
const bindStatus = computed(() => normalizeBindStatus(store.ext?.bindStatus))
const statusText = computed(() => bindStatusText(store.ext?.bindStatus))
const statusTone = computed(() => bindStatusTone(store.ext?.bindStatus))
const rejectReason = computed(() => store.ext?.rejectReason || '')

/** 从 /mp/me 的档案回填表单（自填过的档案：两个 id 为空、两个 *NameInput 有值） */
function fillFromProfile() {
  const ext = store.ext
  realName.value = store.name || ''
  unitId.value = ext?.unitId ?? null
  groupId.value = ext?.groupId ?? null
  unitNameInput.value = ext?.unitNameInput || ''
  groupNameInput.value = ext?.groupNameInput || ''
}

async function load() {
  loading.value = true
  loadFailed.value = false
  try {
    await store.loadMe()
    fillFromProfile()
    try {
      units.value = await fetchUnits()
    }
    catch {
      // 选择器数据拉不到不挡填写（用户还能走「手动填写」那条路）
      units.value = []
    }
  }
  catch {
    loadFailed.value = true
  }
  finally {
    loading.value = false
  }
}

/** 保存：两套写法互斥 —— 选了列表项就只给两个 id；手动填写就只给两个名字 */
async function submit() {
  const name = (realName.value || '').trim()
  if (!name) {
    uni.showToast({ title: '请先填姓名', icon: 'none' })
    return
  }
  const payload: ProfileUpdatePayload = { realName: name }
  if (unitId.value && groupId.value) {
    payload.unitId = unitId.value
    payload.groupId = groupId.value
  }
  else {
    const unitName = (unitNameInput.value || '').trim()
    const groupName = (groupNameInput.value || '').trim()
    if (!unitName || !groupName) {
      uni.showToast({ title: '请选择单位与组别，或手动填写', icon: 'none' })
      return
    }
    payload.unitNameInput = unitName
    payload.groupNameInput = groupName
  }

  saving.value = true
  try {
    await saveProfile(payload)
    // 保存成功 = 回到待核验：重新拉 /mp/me，让「我的」页与这里都显示新状态
    await store.loadMe()
    uni.showToast({ title: '已提交，等待核验', icon: 'none' })
    setTimeout(() => goPage('/pages/me/index'), 600)
  }
  catch (e) {
    // 请求层已经按业务码 toast 过后端给的 msg（例如「组别不属于该单位」）
    if (e instanceof Error && e.message) {
      uni.showToast({ title: e.message, icon: 'none' })
    }
  }
  finally {
    saving.value = false
  }
}

function goHome() {
  uni.switchTab({ url: HOME_PAGE })
}

onLoad(() => {
  // 先按 store 里已有的 /mp/me 填一遍，再异步刷新
  fillFromProfile()
  load()
})
</script>

<template>
  <view class="profile">
    <view class="lqg-note lqg-note--warn profile__tip">
      <text>核验通过后，可与同组同事互看样本</text>
    </view>

    <!-- 当前状态：已核验 / 待核验 / 已驳回 + 驳回原因 -->
    <view class="profile__status">
      <text class="profile__status-label">当前状态</text>
      <text class="lqg-tag" :class="`lqg-tag--${statusTone}`">{{ statusText }}</text>
    </view>
    <view v-if="bindStatus === 'rejected' && rejectReason" class="lqg-note lqg-note--danger profile__reason">
      <text>驳回原因：{{ rejectReason }}</text>
    </view>
    <view v-else-if="bindStatus === 'verified'" class="lqg-note profile__reason">
      <text>当前已核验：{{ unitDisplay(store.ext) || '—' }}</text>
    </view>

    <LoadState v-if="loading && !store.me" state="loading" />
    <view v-else-if="loadFailed" class="lqg-state">
      <text class="lqg-state__text">没能加载你的档案</text>
      <button class="profile__btn" @click="load">重新加载</button>
    </view>

    <template v-else>
      <!-- 姓名 -->
      <view class="profile__card">
        <view class="profile__field">
          <text class="profile__label">姓名</text>
          <input v-model="realName" class="profile__input" placeholder="请填写真实姓名" maxlength="100" />
        </view>
      </view>

      <!-- 单位 → 组别联动选择器（末项「列表里没有，手动填写」） -->
      <view class="profile__pickers">
        <UnitGroupPicker
          v-model:unit-id="unitId"
          v-model:group-id="groupId"
          v-model:unit-name-input="unitNameInput"
          v-model:group-name-input="groupNameInput"
          :units="units"
        />
      </view>

      <view class="lqg-note profile__hint">
        <text>改完单位或组别会回到「待核验」，重新核验通过后才能与同组同事互看样本。</text>
      </view>

      <view class="profile__bar lqg-bar">
        <button class="profile__btn profile__btn--p" :disabled="saving" @click="submit">
          {{ saving ? '提交中…' : '保存' }}
        </button>
        <button class="profile__btn" @click="goHome">返回首页</button>
      </view>
      <view class="lqg-bar-spacer" />
    </template>
  </view>
</template>

<style lang="scss" scoped>
.profile {
  padding: var(--lqg-sp-6) 0 0;
}

.profile__tip {
  margin-top: 0;
}

.profile__status {
  margin: var(--lqg-gap) var(--lqg-gutter) 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.profile__status-label {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-2);
}

.profile__reason {
  margin-top: var(--lqg-sp-5);
}

.profile__card {
  margin: var(--lqg-gap) var(--lqg-gutter) 0;
  background: var(--lqg-card);
  border-radius: var(--lqg-radius-card);
  box-shadow: var(--lqg-shadow-sm);
}

.profile__field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lqg-sp-5);
  min-height: var(--lqg-cell-h);
  padding: var(--lqg-sp-5) var(--lqg-sp-6);
}

.profile__label {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-2);
  flex: none;
}

.profile__input {
  flex: 1;
  min-width: 0;
  text-align: right;
  font-size: var(--lqg-fs-title);
  color: var(--lqg-ink);
}

.profile__pickers {
  margin: var(--lqg-gap) var(--lqg-gutter) 0;
}

.profile__hint {
  margin-top: var(--lqg-gap);
}

.profile__bar {
  gap: var(--lqg-sp-4);
}

.profile__btn {
  flex: 1;
  height: var(--lqg-btn-h);
  line-height: var(--lqg-btn-h);
  font-size: var(--lqg-fs-title);
  border-radius: var(--lqg-radius-ctl);
  background: var(--lqg-card);
  color: var(--lqg-primary);
  border: 1px solid var(--lqg-primary);
  margin: 0;
}

.profile__btn--p {
  background: var(--lqg-primary);
  color: var(--lqg-on-primary);
  border: none;
  box-shadow: var(--lqg-shadow-brand);
}

.profile__btn::after {
  border: none;
}
</style>
