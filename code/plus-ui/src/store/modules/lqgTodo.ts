import { defineStore } from 'pinia';
import { getHomeTodo, HomeTodoVO } from '@/api/lqg/home';
import { sampleKindOfPath } from '@/views/lqg/sample/pages';

// ============================================================================
// 工作台首页待办 · 前端唯一的一份（SYS-HOME-001 · UI:admin.home）
//
// ★★ 为什么必须放在 store 里（ticket §0.1 硬要求 ③）：
//   首页待办卡片与侧边菜单角标**共用同一次** `GET /lqg/home/todo` 的结果。
//   侧边栏若自己再请求一次，两个请求之间有人核验了一条样本，就会出
//   「角标 3、进来卡片 2」——老师会怀疑系统，而且这种 bug 只在时序上出现、极难复现。
//   所以：**本文件是前端唯一调 `/lqg/home/todo` 的地方**，
//   页面与侧边栏都只读 `todo`（accept 2 第 5/6 段断的就是这条：
//   `Sidebar` 里必须出现 `lqgTodo`、且不许出现 `home/todo`）。
//
// ★ `refresh()` 里那个 inflight 去重：登录后首页组件与侧边栏**在同一帧挂载**，
//   两边都要求「现在是新的」，没有去重就会并发打两个请求；有了它，
//   同一时刻的多个调用者拿到的是同一个 Promise（= 同一次请求）。
//
// ★ 0 的语义（accept 2 第 3 段）：
//   · 卡片：为 0 **不隐藏**，显示 `0` 并变灰（`views/lqg/home/index.vue` 的 `is-zero`）；
//   · 菜单角标：为 0 **不显示**（角标挂个 0 是个红点，很吵）—— 由调用方 `:hidden="n <= 0"`。
//   `badgeOf()` 只负责给数，不做这两种判断（判断留在各自的渲染处，一眼能看清没写反）。
// ============================================================================

/** 待办键（与后端 HomeTodoVO 逐字对齐） */
export type LqgTodoKey = keyof HomeTodoVO;

/** 全零兜底：接口还没回来 / 回来缺键时，卡片显示 0 而不是 undefined */
const ZERO: HomeTodoVO = {
  pendingSamples: 0,
  pendingTissue: 0,
  pendingOrganoid: 0,
  pendingEmbeds: 0,
  cryoOverdue: 0,
  pendingExtUsers: 0,
  renderFailed: 0
};

export const useLqgTodoStore = defineStore('lqgTodo', () => {
  /** 待办数（唯一来源；卡片与角标都读它） */
  const todo = ref<HomeTodoVO>({ ...ZERO });
  const loading = ref(false);
  /** 拿到过一次真结果（侧边栏据此决定要不要补一次请求） */
  const loaded = ref(false);
  /** 最近一次失败原因（接口挂了时页面显示一行提示，别静默显示 0 —— 那又变成「功能坏了 vs 没有待办」分不清） */
  const error = ref('');

  /** 同一时刻只允许一次请求在飞 */
  let inflight: Promise<void> | null = null;

  /**
   * 拉一次 `/lqg/home/todo`（**每次调用现算**：后端不缓存，所以这里也不缓存数字）。
   */
  const refresh = (): Promise<void> => {
    if (inflight) {
      return inflight;
    }
    inflight = (async () => {
      loading.value = true;
      try {
        const res = await getHomeTodo();
        todo.value = { ...ZERO, ...(res.data || {}) };
        error.value = '';
        loaded.value = true;
      } catch (e: any) {
        // 保留上一次的数字（总比全部变 0 好），但把原因摆出来
        error.value = e?.message || String(e);
      } finally {
        loading.value = false;
        inflight = null;
      }
    })();
    return inflight;
  };

  /**
   * 侧边栏用：还没拉过就拉一次，拉过就什么都不做（不重复请求）。
   */
  const ensureLoaded = (): Promise<void> => (loaded.value ? Promise.resolve() : refresh());

  /**
   * 侧边菜单角标取值：按路由路径找它对应的那个数。
   *
   * <p>路径映射（与蓝图 UI:admin.home 的「可直达」卡片一一对应）：
   * <pre>
   *   /sample           样本记录信息表  → pendingTissue    （CR-20260924-10：原「样本总表」拆成两页，
   *   /sample-organoid  类器官收样记录  → pendingOrganoid    红色数字按页分开；路径取自 views/lqg/sample/pages.ts）
   *   /embed        石蜡包埋        → pendingEmbeds
   *   /cryo         冻存管理        → cryoOverdue
   *   /auth/extuser 外部用户（人员与单位下的子菜单）→ pendingExtUsers
   *   其它          没有角标        → 0
   * </pre>
   *
   * @param path 侧边栏菜单项解析后的完整路径（可能带 query）
   * @returns 角标数字；0 表示「这个地方没有待办」，调用方据此不显示角标
   */
  const badgeOf = (path?: string): number => {
    const raw = (path || '').split('?')[0].replace(/\/+$/, '');
    if (!raw) {
      return 0;
    }
    const sampleKind = sampleKindOfPath(raw);
    if (sampleKind === 'tissue') {
      return Number(todo.value.pendingTissue) || 0;
    }
    if (sampleKind === 'organoid') {
      return Number(todo.value.pendingOrganoid) || 0;
    }
    if (raw.endsWith('/embed')) {
      return Number(todo.value.pendingEmbeds) || 0;
    }
    if (raw.endsWith('/cryo')) {
      return Number(todo.value.cryoOverdue) || 0;
    }
    // 待核验外部用户挂在「人员与单位 → 外部用户」子菜单上（飞书 2026-09-30 工作台行19①；原来挂在父菜单 /auth）
    if (raw.endsWith('/auth/extuser')) {
      return Number(todo.value.pendingExtUsers) || 0;
    }
    return 0;
  };

  return { todo, loading, loaded, error, refresh, ensureLoaded, badgeOf };
});
