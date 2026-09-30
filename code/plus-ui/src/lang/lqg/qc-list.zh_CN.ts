// ============================================================================
// 域内 i18n（zh_CN）· QC 域 —— CR-20260930-11「质控文档」板块列表（飞书「网页工作台」第 17 行）
//
// 约定同 qc-session / qc-publish：文件名 `<域>-<段>`，'-' 前段是域名 → 并进 `lqg.qc.*`。
//   本文件只用一个新顶层键 `list`；配对的英文在同目录 qc-list.en_US.ts（key 集合必须一致）。
// ============================================================================

export default {
  list: {
    title: '质控文档',
    subtitle:
      '类器官样本质量控制记录表：一行是一个已核验有效的样本，右边三列是它的样本质控表、类器官质控表、类器官质量评分表各自的状态。点「进入」编辑这个样本的三份文档。',
    filter: {
      keyword: '关键字',
      keywordPlaceholder: '内部编号 / 来源单位',
      sampleKind: '样本类别',
      progress: '填写进度',
      receiveRange: '收样日期',
      receiveBegin: '开始日期',
      receiveEnd: '结束日期'
    },
    kind: {
      tissue: '样本记录信息表',
      organoid: '类器官送样记录'
    },
    progress: {
      none: '未开始',
      doing: '填写中',
      done: '已全部完成'
    },
    status: {
      none: '未填写',
      draft: '草稿',
      published: '已完成'
    },
    col: {
      internalNo: '内部编号',
      sampleKind: '样本类别',
      sourceUnit: '来源单位',
      typeName: '组织 / 类器官类型',
      receiveDate: '收样日期',
      sampleQc: '样本质控表',
      organoidQc: '类器官质控表',
      score: '类器官质量评分表',
      progress: '进度',
      lastUpdate: '最近修改',
      action: '操作'
    },
    doneCount: '{n} / 3 已完成',
    totalScore: '{n} 分',
    open: '进入',
    search: '搜索',
    reset: '重置',
    refresh: '刷新',
    empty: '没有符合条件的样本（只有已核验有效的样本才有质控文档）'
  }
};
