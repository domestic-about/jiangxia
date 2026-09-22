// ============================================================================
// 域内 i18n（zh_CN）· QC 域 —— DOC-PUBLISH-001（预览面板 + 完成并同步 / 撤回）
//
// 约定（SYS-WEB-001 立、QC-WEB-001/002 续）：文件名 = `<域>-<票>`，'-' 前段是域名
//   → 本文件并进 `lqg.qc.*`（见 src/lang/index.ts 的 mergeLqgMessages）。
//   ★ 顶层键**必须与 qc.zh_CN.ts / qc-web-002.zh_CN.ts 不重**（同域合并是浅合并：
//     撞同一个顶层键会整块覆盖）。本文件只用一个新顶层键：`preview`。
//   ★ 页脚「预览 / 完成并同步」两个按钮的文案与新增提示仍在 qc.zh_CN.ts 的
//     `lqg.qc.editor.*` 里（那张票建的键，本票就地更新文案，不另起一套）。
//
// 配对的英文在同目录 qc-publish.en_US.ts（两个文件的 key 集合必须一致）。
// ============================================================================

export default {
  // ── 预览面板（components/PreviewPane.vue；UI:admin.doc.preview）────────────
  preview: {
    audienceInternal: '内部版',
    audienceExternal: '外部版',
    internalHint: '内部版含内部编号',
    externalHint: '外部版里内部编号一格为空',
    refresh: '刷新',
    rendering: '正在生成页面图…',
    failedTitle: '这份文档渲染失败了',
    noReason: '（后端没给原因）',
    regenerate: '重新生成',
    notGenerated: '这份文档还没生成过页面图',
    preview: '预览',
    pageNo: '第 {n} 页',
    downloadTitle: '下载（与左侧预览同一份产物）',
    downloadWord: '下载 Word',
    downloadPdf: '下载 PDF',
    downloadMergedWord: '下载合并 Word',
    downloadMergedPdf: '下载合并 PDF',
    mergedNotForScore: '评分表没有独立的合并件：合并件是「已完成的几份」按固定顺序拼起来的',
    mergedUnavailable: '合并件还没法下载：',
    needsResync: '这份文档已经改过、回到了草稿；预览图还是上一版。重新「完成并同步」后送检方才看得到。',

    // 完成并同步 / 撤回
    publishDone: '已同步给送检方',
    publishFailed: '完成并同步失败：',
    unpublishDone: '已撤回，送检方看不到了',
    unpublishFailed: '撤回失败：',
    unpublish: '撤回',
    saveFirst: '先把当前页签的改动保存草稿，再完成并同步',
    publishConfirm: '确定「完成并同步」这份文档吗？完成后送检方就能看到它。',
    unpublishConfirm: '确定撤回吗？撤回后送检方立刻看不到这份文档。',
    syncedFooter: '已同步给送检方 · 修改后需重新同步'
  }
};
