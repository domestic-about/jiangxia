// ============================================================================
// 域内 i18n（zh_CN）· SAMPLE 域 —— SAMPLE-WEB-001（工作台样本总表）
//
// 约定（SYS-WEB-001 立）：键路径 = `lqg.sample.<key>`；
//   文件名 `sample[-<票>].zh_CN.ts` 都归并进 `lqg.sample`（src/lang/index.ts 按 '-' 前段归并）。
//   **不要**往 src/lang/zh_CN.ts 大文件里加 key。
//
// 配对的英文在同目录 sample.en_US.ts（两个文件的 key 集合必须一致）。
// ============================================================================

export default {
  // ── 页面与筛选区 ──────────────────────────────────────────────────────────
  title: '样本总表',
  subtitle: '所有样本都在这一张表里：按来源单位、组别、类别、内外部、核验状态批量筛选后查询。',
  search: '搜索',
  reset: '重置',

  filter: {
    sourceUnit: '来源单位',
    group: '组别',
    sampleKind: '样本类别',
    submitSource: '提交来源',
    verifyStatus: '核验状态',
    receiveDateRange: '收样日期',
    receiveDateBegin: '收样日期起',
    receiveDateEnd: '收样日期止',
    tissueType: '组织类型',
    internalNo: '内部编号',
    operatorName: '操作人',
    donorName: '供体姓名',
    hospitalNo: '住院号',
    exactMatch: '精确匹配',
    all: '全部',
    sourceUnitPlaceholder: '选择来源单位',
    groupPlaceholder: '选择组别',
    groupPlaceholderNoUnit: '先选来源单位',
    internalNoPlaceholder: '内部编号（精确）',
    operatorPlaceholder: '操作人（模糊）',
    tissuePlaceholder: '组织类型（模糊）',
    donorPlaceholder: '供体姓名（精确匹配）',
    hospitalPlaceholder: '住院号（精确匹配）'
  },

  // ── 工具栏 ────────────────────────────────────────────────────────────────
  toolbar: {
    addTissue: '新增样本记录',
    addOrganoid: '新增类器官收样',
    exportTissue: '导出样本记录信息表',
    exportOrganoid: '导出类器官收样记录',
    exportNotYet: '导出在下一个任务接入（SAMPLE-EXPORT-001）',
    refresh: '刷新'
  },

  // ── 表格列 ────────────────────────────────────────────────────────────────
  col: {
    internalNo: '内部编号',
    submitNo: '送检单号',
    sourceUnit: '来源单位',
    sampleKind: '类别',
    submitSource: '来源',
    verifyStatus: '核验状态',
    donorName: '供体姓名',
    gender: '性别',
    age: '年龄',
    hospitalNo: '住院号',
    tissueType: '组织类型 / 类器官类型',
    receiveDate: '收样日期',
    isFixed: '有无固定',
    processTime: '处理时间',
    hasQcSheet: '质控表',
    hasViabilityReport: '细胞活率报告',
    hasPathology: '有无病理',
    submitterName: '提交人',
    groupName: '组别',
    operatorName: '操作人',
    updateTime: '最后修改',
    remark: '备注',
    action: '操作'
  },

  // ── 状态与行操作 ──────────────────────────────────────────────────────────
  status: {
    pending: '待核验',
    valid: '有效',
    invalid: '无效'
  },
  source: {
    internal: '内部',
    external: '外部'
  },
  kind: {
    tissue: '组织样本',
    organoid: '类器官'
  },
  flag: {
    yes: '有',
    no: '无'
  },
  gender: {
    male: '男',
    female: '女',
    unknown: '未知'
  },
  neverModified: '从未修改',
  empty: '没有符合条件的样本',
  rowAction: {
    edit: '编辑',
    verify: '核验',
    qcDoc: '质控文档',
    embed: '石蜡包埋',
    cryo: '冻存',
    notYet: '在后续任务接入',
    delete: '删除',
    deleteConfirm: '确认删除样本「{no}」？（软删，内部编号可重用）',
    deleted: '已删除'
  },

  // ── 抽屉：录入 / 编辑 / 核验（UI:admin.sample.edit） ────────────────────────
  drawer: {
    addTissue: '新增样本记录',
    addOrganoid: '新增类器官收样',
    edit: '编辑样本',
    verify: '核验样本',
    view: '样本详情',
    sectionSubmit: '送检信息',
    sectionReceive: '收样信息',
    kindFirst: '先选类别：组织样本 / 类器官，两类的字段不一样。',
    lastModified: '最后修改：{name} · {time}',
    lastModifiedNever: '从未修改',
    save: '保存',
    cancel: '取 消',
    saveValid: '判为有效并保存',
    saveInvalid: '判为无效',
    invalidReasonTitle: '判为无效的原因',
    invalidReasonRequired: '判无效必须填原因',
    invalidReasonPlaceholder: '写清哪里不对，外部看得到这句话',
    more: '更多',
    changeToInvalid: '改判无效',
    hasChildrenTip: '该样本名下已有包埋 / 冻存 / 质控文档，不能改判无效',
    saved: '已保存',
    verifiedValid: '已判为有效',
    verifiedInvalid: '已判为无效',
    loadFailed: '样本详情没能加载',
    required: '必填',
    numberRequired: '请填内部编号',
    receiveDateRequired: '请选收样日期',
    kindRequired: '请选样本类别',
    tissueRequired: '请填组织类型',
    organoidRequired: '请填类器官类型',
    internalNoTaken: '这个内部编号已经被占用了'
  },

  // ── 字段标签（抽屉里） ────────────────────────────────────────────────────
  field: {
    submitNo: '送检单号',
    sampleKind: '样本类别',
    sourceUnit: '来源单位',
    sourceUnitName: '来源单位名称',
    sourceUnitPlaceholder: '没有可选项时在这里手填单位名',
    donorName: '供体姓名',
    gender: '性别',
    age: '年龄',
    hospitalNo: '住院号',
    tissueType: '组织类型',
    organoidType: '类器官类型',
    hasPathology: '有无病理',
    receiveDate: '收样日期',
    internalNo: '内部编号',
    isFixed: '有无固定',
    processTime: '处理时间',
    hasQcSheet: '质控表',
    hasViabilityReport: '细胞活率报告',
    operatorName: '操作人',
    remark: '备注',
    submitterName: '提交人',
    groupName: '组别',
    verifyStatus: '核验状态',
    invalidReason: '无效原因',
    createTime: '创建时间'
  },

  // ── 主体（核验的是外部送来的：组织样本走送检 + 收样，类器官按 CR-20260917-05 切字段） ──
  verifyHintOrganoid: '外部送来的类器官收样：送检段只核来源单位、类器官类型、备注；收样段填收样日期、内部编号、处理时间、细胞活率报告、操作人。',
  verifyHintTissue: '外部送来的组织样本：收样日期与内部编号必填，内部编号全库唯一。',
  allSamples: '全部样本',
  loading: '加载中…'
};
