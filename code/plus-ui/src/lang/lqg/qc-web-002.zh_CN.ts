// ============================================================================
// 域内 i18n（zh_CN）· QC 域 —— QC-WEB-002（类器官质控表页签 + 类器官质量评分表页签）
//
// 约定（SYS-WEB-001 立、QC-WEB-001 续）：文件名 = `<域>-<票>`，'-' 前段是域名
//   → 本文件并进 `lqg.qc.*`（见 src/lang/index.ts 的 mergeLqgMessages）。
//   ★ 顶层键**必须与 qc.zh_CN.ts 不重**（同域合并是浅合并：撞同一个顶层键会整块覆盖）。
//   本文件只用两个新顶层键：`organoid` / `score`。
//
// 配对的英文在同目录 qc-web-002.en_US.ts（两个文件的 key 集合必须一致）。
// ============================================================================

export default {
  // ── 类器官质控表页签（editor/OrganoidQcTab.vue）─────────────────────────────
  organoid: {
    observeTitle: '样本观察情况 · 图片',
    formedTime: '形成类器官时间',
    growthState: '生长状态',
    growthDesc: '类器官生长情况',
    plannedDrugScreen: '预计筛药',
    feedbackTime: '反馈时间',
    timePlaceholder: 'yyyy-MM-dd，也可直接手输文字',
    pickDate: '选日期',
    timeHint: '选日期会填 yyyy-MM-dd；也可以直接手输文字（如「约第 5 天」）'
  },

  // ── 类器官质量评分表页签（editor/ScoreTab.vue）─────────────────────────────
  // ★ 只出现「项目名 / 档位原文 / 分值 / 合计」，**没有任何质量等级结论**
  score: {
    title: '类器官质量评分',
    variableCol: '项目',
    optionsCol: '分档',
    scoreCol: '类器官质量评分',
    points: '{n} 分',
    total: '合计',
    totalPending: '四项都选了才出合计',
    immediateHint: '页面上的分值与合计是即时反馈；保存只提交四个档位，落库分值由后端按字典回填。',
    persisted: '已落库（后端回填）：{items}，合计 {total}',
    persistedEmpty: '已落库（后端回填）：合计 —（还有项没选）',
    // 四个变量名（sys_dict_type.dict_name 的原文）；选项文字与分值来自字典，不在这里写
    preCulture: '培养前样本评分',
    cultureDays: '培养天数',
    organoidCount: '类器官数量（药敏实验实际测得）',
    diameter: '类器官直径'
  }
};
