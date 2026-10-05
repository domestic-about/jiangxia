import { ElMessageBox } from 'element-plus';
import { ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';

/**
 * 表单抽屉的「关之前先问一句」（工作台 UX 测试 WEB-04：按 ESC / 点遮罩 / 点 × / 点「取消」都直接关，填了一半全丢）。
 *
 * - `ready()` 为真（抽屉开着、加载完）时记下表单基线；之后表单值与基线不同 = 有改动；
 * - `beforeClose` 给 el-drawer 的 `:before-close`（ESC、×）；`requestClose(close)` 给「取消」按钮；
 * - 保存成功后页面自己 `visible = false` 关抽屉，不经过这里，不会被拦。
 */
export function useCloseGuard(source: () => unknown, ready: () => boolean) {
  const { t } = useI18n();
  const baseline = ref('');
  const snap = () => JSON.stringify(source());

  watch(ready, (ok) => {
    baseline.value = ok ? snap() : '';
  });

  const dirty = () => baseline.value !== '' && snap() !== baseline.value;

  async function confirmDiscard(): Promise<boolean> {
    if (!dirty()) {
      return true;
    }
    try {
      await ElMessageBox.confirm(t('lqg.ux.closeGuard.message'), t('lqg.ux.closeGuard.title'), {
        confirmButtonText: t('lqg.ux.closeGuard.discard'),
        cancelButtonText: t('lqg.ux.closeGuard.stay'),
        type: 'warning'
      });
      return true;
    } catch {
      return false;
    }
  }

  /** el-drawer / el-dialog 的 :before-close */
  const beforeClose = async (done: () => void) => {
    if (await confirmDiscard()) {
      done();
    }
  };

  /** 「取消」按钮：同一套判断，确认后执行 close */
  const requestClose = async (close: () => void) => {
    if (await confirmDiscard()) {
      close();
    }
  };

  return { beforeClose, requestClose };
}
