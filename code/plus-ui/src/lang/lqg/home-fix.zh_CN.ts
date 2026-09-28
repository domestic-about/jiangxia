// ============================================================================
// 域内 i18n（zh_CN）· SYS 域首页 —— 独立验收修复（2026-09-23，F2 组：渲染失败与缺图清单）
//
// 文件名 `home-fix`：'-' 前段是域名 → 并进 `lqg.home.*`（src/lang/index.ts 的 mergeLqgMessages）。
//   ★ 同域浅合并：只用一个新顶层键 `issues`，不碰 home.zh_CN.ts 里的 card / recent …
//   配对的英文在 home-fix.en_US.ts（key 集合必须一致）。
// ============================================================================

export default {
  issues: {
    cardTitle: '文档渲染失败 / 缺图',
    cardHint: '生成失败或缺图的质控文档，点开看清单并重新生成',
    cardZero: '没有渲染失败或缺图的文档',
    cardGo: '看清单 →',
    title: '渲染失败与缺图清单',
    subtitle: '一行是一份文档的一个版本；修好图片或内容后点「重新生成」，好了就会从清单里消失',
    empty: '现在没有渲染失败或缺图的文档',
    loadFailed: '清单没拉到：{msg}',
    colSample: '样本',
    colDoc: '文档',
    colAudience: '版本',
    colIssue: '问题',
    colTime: '时间',
    colAction: '操作',
    failed: '渲染失败',
    missing: '内部版缺 {n} 张图',
    internal: '内部版',
    external: '外部版',
    openQc: '去质控页',
    regenerate: '重新生成',
    regenerated: '已重新生成，清单已刷新',
    stillFailed: '重新生成后还是失败：{msg}',
    stillMissing: '重新生成后仍缺 {n} 张图',
    docSampleQc: '样本质控表',
    docOrganoidQc: '类器官质控表',
    docScore: '类器官质量评分表',
    docMerged: '合并件'
  }
};
