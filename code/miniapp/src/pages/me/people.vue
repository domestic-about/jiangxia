<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ReviewPerson } from '@/api/people'
import { fetchReviewAccess, fetchReviewPeople, reviewPerson } from '@/api/people'
import type { SelectorUnit } from '@/api/unit-group'
import { fetchUnits } from '@/api/unit-group'
import ErrorState from '@/components/lqg/ErrorState.vue'
import EmptyState from '@/components/lqg/EmptyState.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import { useUserStore } from '@/store/user'
import { normalizeIdentity } from '@/types/identity'
import { emptyReview, reviewBody, selfInputOf } from './people-review'

definePage({ style: { navigationBarTitleText: '人员与组别审核', enablePullDownRefresh: true } })
const store = useUserStore()
const loading = ref(true)
const failed = ref(false)
const allowed = ref(false)
const saving = ref(false)
const rows = ref<ReviewPerson[]>([])
const units = ref<SelectorUnit[]>([])
const status = ref('pending')
const tabs = [{ value: 'pending', label: '待核验' }, { value: 'verified', label: '已核验' }, { value: 'rejected', label: '已驳回' }, { value: '', label: '全部' }]
const statusText = (value: string) => tabs.find(tab => tab.value === value)?.label || value
const selected = ref<ReviewPerson | null>(null)
const choice = ref(emptyReview())
const unitIndex = ref(-1)
const groupIndex = ref(-1)
const selectedUnit = computed(() => units.value[unitIndex.value])
const groups = computed(() => selectedUnit.value?.groups ?? [])
const errorText = ref('')
let loadVersion = 0
async function load() {
  const version = ++loadVersion
  loading.value = true
  failed.value = false
  selected.value = null
  try {
    if (!store.me) await store.loadMe()
    if (normalizeIdentity(store.identity) !== 'internal') { allowed.value = false; return }
    allowed.value = await fetchReviewAccess() === true
    if (!allowed.value) return
    const [people, options] = await Promise.all([fetchReviewPeople(status.value), fetchUnits()])
    if (version === loadVersion) { rows.value = people; units.value = options }
  }
  catch (e) { if (version === loadVersion) { failed.value = true; errorText.value = e instanceof Error ? e.message : '加载失败' } }
  finally { if (version === loadVersion) loading.value = false }
}
function pickStatus(value: string) { if (saving.value || loading.value) return; status.value = value; load() }
function open(row: ReviewPerson) {
  if (saving.value || !['pending', 'verified'].includes(row.bindStatus)) return
  selected.value = row
  choice.value = emptyReview()
  unitIndex.value = -1
  groupIndex.value = -1
  errorText.value = ''
}
function pickUnit(event: { detail: { value: string | number } }) {
  unitIndex.value = Number(event.detail.value)
  groupIndex.value = -1
  choice.value.unitId = selectedUnit.value?.unitId
  choice.value.groupId = undefined
}
function pickGroup(event: { detail: { value: string | number } }) {
  groupIndex.value = Number(event.detail.value)
  choice.value.groupId = groups.value[groupIndex.value]?.groupId
}
async function save() {
  if (!selected.value || saving.value) return
  errorText.value = ''
  let body
  try { body = reviewBody(selected.value, choice.value) }
  catch (e) { errorText.value = e instanceof Error ? e.message : '请检查填写内容'; return }
  const person = selected.value
  saving.value = true
  try {
    const consequence = body.createUnit ? '将按申请人填写的名称新建或匹配单位、组别。' : ''
    const confirmed = await new Promise<boolean>(resolve => uni.showModal({
      title: body.action === 'approve' ? '确认审核通过' : '确认驳回',
      content: `${person.name || '该用户'}：${body.action === 'approve' ? (person.bindStatus === 'verified' ? '调整后可与目标组成员互看样本。' : '通过后可与同组成员互看样本。') : `驳回原因：${body.reason}`} ${consequence}`,
      success: result => resolve(result.confirm), fail: () => resolve(false),
    }))
    if (!confirmed) return
    await reviewPerson(person.userId, body)
    uni.showToast({ title: '已保存', icon: 'success' })
    await load()
  }
  catch (e) { errorText.value = e instanceof Error ? e.message : '保存失败，请重试' }
  finally { saving.value = false }
}
onShow(load)
onPullDownRefresh(async () => { if (!saving.value) await load(); uni.stopPullDownRefresh() })
</script>

<template>
  <view class="people">
    <LoadingState v-if="loading" />
    <ErrorState v-else-if="failed" :text="errorText" @retry="load" />
    <EmptyState v-else-if="!allowed" state="empty" text="仅内部人员可审核，请使用内部人员账号" />
    <template v-else>
      <view class="lqg-filter">
        <text v-for="tab in tabs" :key="tab.value" class="lqg-filter__chip" :class="status === tab.value ? 'people__on' : ''" @click="pickStatus(tab.value)">{{ tab.label }}</text>
      </view>
      <view class="people__note">内部人员均可审核外部用户申请的单位与组别；微信体验成员资格请由微信公众平台管理员处理。</view>
      <EmptyState v-if="!rows.length" state="empty" text="没有符合条件的人员" />
      <view v-for="row in rows" :key="String(row.userId)" class="lqg-card people__card">
        <view class="people__name">{{ row.name || '未填写姓名' }} <text class="people__status">{{ statusText(row.bindStatus) }}</text></view>
        <text class="people__line">{{ row.phone || '—' }}</text>
        <text class="people__line">单位：{{ row.unitNameInput || row.unitName || '未填写' }}</text>
        <text class="people__line">组别：{{ row.groupNameInput || row.groupName || '未填写' }}</text>
        <text v-if="row.rejectReason" class="people__line">驳回原因：{{ row.rejectReason }}</text>
        <text v-if="!['pending', 'verified'].includes(row.bindStatus)" class="people__note">{{ row.bindStatus === 'rejected' ? '需由申请人修改资料并重新提交后再审核' : '尚未提交单位与组别申请' }}</text>
        <button v-else-if="selected?.userId !== row.userId" class="people__button" :disabled="saving" @click="open(row)">{{ row.bindStatus === 'verified' ? '调整归组' : '审核' }}</button>
        <view v-else class="people__review">
          <view class="people__actions">
            <button :disabled="saving" :class="choice.action === 'approve' ? 'people__on' : ''" @click="choice.action = 'approve'">{{ row.bindStatus === 'verified' ? '调整归组' : '通过' }}</button>
            <button v-if="row.bindStatus === 'pending'" :disabled="saving" :class="choice.action === 'reject' ? 'people__on' : ''" @click="choice.action = 'reject'">驳回</button>
          </view>
          <template v-if="choice.action === 'approve'">
            <view class="people__modes">
              <button v-if="selfInputOf(row)" :disabled="saving" :class="choice.mode === 'create' ? 'people__on' : ''" @click="choice.mode = 'create'">按申请内容新建单位与组别</button>
              <button v-else-if="row.unitId && row.groupId" :disabled="saving" :class="choice.mode === 'keep' ? 'people__on' : ''" @click="choice.mode = 'keep'">保留当前单位与组别</button>
              <button :disabled="saving" :class="choice.mode === 'merge' ? 'people__on' : ''" @click="choice.mode = 'merge'">归并到已有单位与组别</button>
            </view>
            <template v-if="choice.mode === 'merge'">
              <picker :disabled="saving" :range="units" range-key="unitName" :value="Math.max(0, unitIndex)" @change="pickUnit"><view class="people__picker">单位：{{ selectedUnit?.unitName || '请选择' }} ›</view></picker>
              <picker :disabled="saving || !selectedUnit || !groups.length" :range="groups" range-key="groupName" :value="Math.max(0, groupIndex)" @change="pickGroup"><view class="people__picker">组别：{{ groups[groupIndex]?.groupName || (selectedUnit && !groups.length ? '该单位暂无启用组别' : '请选择') }} ›</view></picker>
            </template>
          </template>
          <textarea v-if="choice.action === 'reject'" v-model="choice.reason" :disabled="saving" :maxlength="200" class="people__reason" placeholder="请填写驳回原因（必填）" />
          <text v-if="errorText" class="people__error">{{ errorText }}</text>
          <view class="people__actions">
            <button :disabled="saving" @click="selected = null">取消</button>
            <button :loading="saving" :disabled="saving" class="people__on" @click="save">确认保存</button>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<style scoped lang="scss">
.people { padding: 16px; }
.people__card { padding: 16px; margin: 14px 0; }
.people__name { display: flex; justify-content: space-between; gap: 12px; font-size: 17px; font-weight: 600; overflow-wrap: anywhere; }
.people__status { flex-shrink: 0; font-size: 13px; color: var(--lqg-primary); }
.people__line { display: block; margin-top: 8px; overflow-wrap: anywhere; }
.people__note { color: var(--lqg-ink-3); line-height: 1.6; margin: 12px 0; font-size: 13px; }
.people__actions { display: flex; gap: 12px; margin-top: 14px; }
.people__actions button { flex: 1; margin: 0; font-size: 15px; }
.people__button, .people__modes button { font-size: 14px; margin-top: 12px; }
.people__on { color: var(--lqg-primary); background: var(--lqg-primary-soft); }
.people__picker { padding: 14px 8px; border-bottom: 1px solid var(--lqg-line); }
.people__reason { width: 100%; height: 100px; margin-top: 14px; padding: 10px; box-sizing: border-box; border: 1px solid var(--lqg-line); }
.people__error { display: block; color: var(--lqg-danger); margin-top: 10px; }
</style>
