// ============================================================================
// 域内 i18n（zh_CN）· SAMPLE 域 —— SAMPLE-WEB-001（工作台样本表；CR-20260924-10 起拆成
//   「样本记录信息表」「类器官收样记录」两页，文案见 page.*）
//
// 约定（SYS-WEB-001 立）：键路径 = `lqg.sample.<key>`；
//   文件名 `sample[-<票>].zh_CN.ts` 都归并进 `lqg.sample`（src/lang/index.ts 按 '-' 前段归并）。
//   **不要**往 src/lang/zh_CN.ts 大文件里加 key。
//
// 配对的英文在同目录 sample.en_US.ts（两个文件的 key 集合必须一致）。
// ============================================================================

export default {
  // ── 页面与筛选区 ──────────────────────────────────────────────────────────
  // 两张样本表（CR-20260924-10：甲方 2026-09-24 第 25 行，组织样本与类器官样本分开两张表）
  // 页头一句话（2026-09-24 本机验收，四张表统一口径）：这张表是什么、和别的表什么关系
  page: {
    tissue: {
      title: '样本记录信息表',
      subtitle: '一行是一个组织样本；它做出的石蜡块、冻存批次看「石蜡包埋 / 冻存」一列，点数字直接过去。合作单位送来待核验的浅黄色标出，点「核验」处理。'
    },
    organoid: {
      title: '类器官送样记录',
      subtitle: '一行是一条类器官送样；它做出的石蜡块、冻存批次看「石蜡包埋 / 冻存」一列，点数字直接过去。合作单位送来待核验的浅黄色标出，点「核验」处理。'
    }
  },
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
    organoidType: '类器官类型',
    internalNo: '内部编号',
    operatorName: '操作人',
    donorName: '供体姓名',
    hospitalNo: '住院号',
    exactMatch: '精确匹配',
    all: '全部',
    sourceUnitPlaceholder: '选择来源单位',
    groupPlaceholder: '选择组别',
    groupPlaceholderNoUnit: '先选来源单位',
    internalNoPlaceholder: '内部编号（完整）',
    operatorPlaceholder: '操作人（模糊）',
    tissuePlaceholder: '组织类型（模糊）',
    organoidPlaceholder: '类器官类型（模糊）',
    donorPlaceholder: '供体姓名（精确匹配）',
    hospitalPlaceholder: '住院号（精确匹配）'
  },

  // ── 工具栏 ────────────────────────────────────────────────────────────────
  toolbar: {
    addTissue: '新增样本记录',
    addOrganoid: '新增类器官送样',
    exportTissue: '导出样本记录信息表',
    exportOrganoid: '导出类器官送样记录',
    exportTissueFile: '样本记录信息表',
    exportOrganoidFile: '类器官送样记录',
    exportTissueDone: '样本记录信息表已导出（当前筛选结果）',
    exportOrganoidDone: '类器官送样记录已导出（当前筛选结果）',
    exportEmpty: '当前筛选没有样本，未生成文件',
    refresh: '刷新'
  },

  // ── 表格列 ────────────────────────────────────────────────────────────────
  col: {
    internalNo: '内部编号',
    submitNo: '送检单号',
    sourceUnit: '来源单位',
    // 插入列（CR-20261009-18）；与 lqg.species.label 同字
    species: '种属',
    sampleKind: '类别',
    submitSource: '来源',
    verifyStatus: '核验状态',
    donorName: '供体姓名',
    gender: '性别',
    age: '年龄',
    hospitalNo: '住院号',
    tissueType: '组织类型',
    organoidType: '类器官类型',
    passage: '代数',
    receiveDate: '收样日期',
    isFixed: '有无固定',
    processTime: '处理时间',
    hasQcSheet: '质控表',
    hasViabilityReport: '细胞活率报告',
    hasPathology: '有无病理',
    submitterName: '提交人',
    groupName: '组别',
    operatorName: '操作人',
    hint: '切片染色',
    updateTime: '最后修改',
    remark: '备注',
    relation: '石蜡包埋 / 冻存',
    action: '操作'
  },

  // ── 切片染色提示（SAMPLE-HINT-001 / UI:admin.sample.list.hint） ────────────
  // 读时计算、不可编辑；没有包埋记录显示「—」；悬停列出各石蜡块编号与切片时间。
  // 块数在「石蜡包埋 / 冻存」一列（relation.*），这里只写已切片 / 未切片与染色（2026-09-24 本机验收）。
  hint: {
    sectioned: '已切片',
    notSectioned: '未切片',
    none: '—',
    blockNo: '石蜡块编号',
    sectionTime: '切片时间',
    empty: '还没有石蜡块明细',
    loading: '加载中…',
    loadFailed: '石蜡块明细没能加载',
    open: '点击查看石蜡包埋页'
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
  // ── 石蜡包埋 / 冻存一列（2026-09-24 本机验收「四种表之间的关系看着有点乱」） ─────
  // 蜡块 = 已核验有效的石蜡块；待核验 = 合作单位送来、还没核验的石蜡包埋送样；冻存 = 冻存批次。
  relation: {
    blocks: '蜡块 {n}',
    blocksLabel: '蜡块',
    pending: '待核验 {n}',
    cryo: '冻存 {n} 批',
    cryoLabel: '冻存',
    cryoCount: '{n} 批',
    none: '—',
    add: '新增',
    openBlocks: '看这个样本的石蜡包埋记录',
    openPending: '看合作单位送来、还没核验的石蜡包埋送样',
    openCryo: '看这个样本的冻存批次',
    addBlocks: '给这个样本新增石蜡包埋记录（样本已选好）',
    addCryo: '给这个样本新增冻存批次（样本已选好）',
    sampleGone: '这条样本已删除或找不到了'
  },
  neverModified: '从未修改',
  empty: '没有符合条件的样本',
  rowAction: {
    edit: '编辑',
    verify: '核验',
    qcDoc: '质控文档',
    qcDocInvalid: '只有已核验有效的样本能打开质控文档',
    notYet: '在后续任务接入',
    delete: '删除',
    deleteConfirm: '确认删除样本「{no}」？删除后列表里就不再显示，它的内部编号可以重新使用。',
    deleted: '已删除'
  },

  // ── 抽屉：录入 / 编辑 / 核验（UI:admin.sample.edit） ────────────────────────
  drawer: {
    addTissue: '新增样本记录',
    addOrganoid: '新增类器官送样',
    edit: '编辑样本',
    verify: '核验样本',
    view: '样本详情',
    sectionSubmit: '送检信息',
    sectionReceive: '收样信息',
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
    passageInvalid: '代数请填 P 加数字，如 P3',
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
    passage: '代数',
    passagePlaceholder: '选填，如 P3',
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
  verifyHintOrganoid: '外部送来的类器官送样：送检段只核来源单位、类器官类型、代数、备注；收样段填收样日期、内部编号、处理时间、细胞活率报告、操作人。',
  verifyHintTissue: '外部送来的组织样本：收样日期与内部编号必填，内部编号全库唯一。',
  allSamples: '全部样本',
  loading: '加载中…'
};
