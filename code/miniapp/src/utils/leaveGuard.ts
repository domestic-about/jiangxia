import { computed, ref, watch } from 'vue'

/** 与质控编辑页同一句（pages/qc/edit.vue） */
const LEAVE_MESSAGE = '还有没保存的改动，离开就丢掉了。确定离开吗？'

function setAlert(on: boolean) {
  // #ifdef MP-WEIXIN
  const api = uni as unknown as { enableAlertBeforeUnload?: (o: object) => void, disableAlertBeforeUnload?: (o?: object) => void }
  if (on) {
    api.enableAlertBeforeUnload?.({ message: LEAVE_MESSAGE })
  }
  else {
    api.disableAlertBeforeUnload?.()
  }
  // #endif
}

/**
 * 填写页「有没保存的改动时按返回，先问一句」（UX 测试 MP-04：四张表填了一半误触返回，整页白填）。
 *
 * - `ready()` 第一次为真（加载完、预填完）时记下基线；之后表单值与基线不同 = 有改动 → 打开微信原生的返回询问；
 * - 提交成功后调 `release()`：关掉询问，跳走时不再拦。
 * H5 上没有这个能力（只有小程序生效），需真机确认。
 */
export function useLeaveGuard(source: () => unknown, ready: () => boolean) {
  const baseline = ref('')
  const snap = () => JSON.stringify(source())
  const dirty = computed(() => baseline.value !== '' && snap() !== baseline.value)

  watch(ready, (ok) => {
    if (ok && baseline.value === '') {
      baseline.value = snap()
    }
  }, { immediate: true })
  watch(dirty, d => setAlert(d))

  function release() {
    baseline.value = ''
    setAlert(false)
  }

  return { dirty, release }
}
