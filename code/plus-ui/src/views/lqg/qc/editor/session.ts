// ============================================================================
// 质控编辑页「一个标签装一个样本」的换样本判据（Kevin 本机验收「网页工作台」第 8 行第 2 点）
//
// 原来的坏法：编辑页是 keep-alive 缓存的整页（菜单 5510 is_cache=0），组件按路由 path 复用，
// sampleId 只在 setup 里读一次 —— 从任何列表再点「质控文档」，都还是第一次进的那个样本。
//
// ★ 采用「同一个标签，sampleId 变了就整页重新加载」（没做成每个样本一个标签：若依的标签页、
//   keep-alive 缓存都按 path / 路由名认页，拆成多标签要动框架的标签页逻辑，多个缓存实例还关不干净）。
//   标签标题带上当前样本的内部编号，看得出这个标签此刻装的是哪一个样本。
// ★ 换样本前的未保存提示：
//   · 从编辑页点别的菜单 / 标签离开时，原有的离开提示（onBeforeRouteLeave）已经问过「离开就会丢掉」；
//     用户选了「离开」再从列表点另一个样本 → 直接换，不再问第二遍；
//   · 编辑页还开着、只是地址里的 sampleId 变了（浏览器前进后退、页内链接）→ 离开提示不会触发，这里问一次；
//   · 选了「离开」之后又从标签回到**同一个**样本 → 没保存的改动还在（keep-alive 留着），接着编辑。
// ============================================================================

/** 地址里的 sampleId（缺省 = 空串：没选样本，页面显示引导） */
export const sampleIdOf = (query: Record<string, unknown>): string => {
  const raw = query?.sampleId;
  const value = Array.isArray(raw) ? raw[0] : raw;
  return value === undefined || value === null ? '' : String(value).trim();
};

/** 换样本怎么办：stay = 还是这个样本，什么都不动；open = 直接换；confirm = 先问一句（有没保存的改动） */
export type SampleSwitch = 'stay' | 'open' | 'confirm';

/**
 * @param current 页面上此刻装的样本
 * @param next 地址里要的样本
 * @param dirty 任一页签有没保存的改动
 * @param leaveConfirmed 这些改动在离开编辑页时已经确认过「丢掉」
 */
export const decideSampleSwitch = (current: string, next: string, dirty: boolean, leaveConfirmed: boolean): SampleSwitch => {
  if (next === current) {
    return 'stay';
  }
  return dirty && !leaveConfirmed ? 'confirm' : 'open';
};
