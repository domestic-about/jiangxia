// ============================================================================
// 域内 i18n（zh_CN）· EMBED 域 —— EMBED-WEB-001（工作台石蜡包埋页）
//
// 约定（SYS-WEB-001 立）：键路径 = `lqg.embed.<key>`；
//   文件名 `embed[-<票>].zh_CN.ts` 都归并进 `lqg.embed`（src/lang/index.ts 按 '-' 前段归并）。
//   **不要**往 src/lang/zh_CN.ts 大文件里加 key。
//
// 配对的英文在同目录 embed.en_US.ts（两个文件的 key 集合必须一致）。
// ============================================================================

export default {
  // ── 页面与筛选区 ──────────────────────────────────────────────────────────
  title: '石蜡包埋送样记录',
  // 页头一句话（2026-09-24 本机验收，四张表统一口径）：这张表是什么、和样本的关系、外部送来的在哪核验
  subtitle: '一行是一个石蜡块，挂在某个样本下（一个样本可以有多块），点「样本编号」回到样本。合作单位送来的在这里核验：待核验的置顶、浅黄底，点「合作单位送来待核验」一键筛出。',
  search: '搜索',
  reset: '重置',
  loading: '加载中…',
  empty: '没有符合条件的石蜡包埋记录',

  filter: {
    paraffinBlockNo: '石蜡块编号',
    paraffinBlockNoPlaceholder: '石蜡块编号（模糊）',
    internalNo: '样本编号',
    internalNoPlaceholder: '内部编号（完整）',
    stain: '染色',
    sectionTimeRange: '切片时间',
    sectionTimeBegin: '切片时间起',
    sectionTimeEnd: '切片时间止',
    verifyStatus: '核验状态',
    submitSource: '来源',
    submitSourceAll: '全部',
    sourceExternal: '合作单位（外部）',
    sourceInternal: '中心内部'
  },

  // ── 带 sampleId 进来时顶部的提示条（2026-09-24 本机验收） ─────────────────────
  scope: {
    only: '只看{sample}的石蜡包埋记录',
    total: '共 {n} 条',
    showAll: '看全部',
    openSample: '打开样本',
    sampleFallback: '样本 {id}'
  },

  // ── 工具栏 ────────────────────────────────────────────────────────────────
  toolbar: {
    add: '新增石蜡包埋记录',
    refresh: '刷新',
    partnerPending: '合作单位送来待核验',
    export: '导出石蜡包埋送样记录',
    exporting: '正在导出…',
    exportDone: '导出完成',
    exportEmpty: '当前筛选没有可导出的记录'
  },

  // ── 表格列（模板 16 列 + 前面两个徽标列 + 操作） ────────────────────────────
  col: {
    submitSource: '来源',
    verifyStatus: '核验状态',
    paraffinBlockNo: '石蜡块编号',
    internalNo: '样本编号',
    sampleType: '样本类型',
    organoidSourceType: '类器官来源类型',
    tissueReceiveTime: '组织收样时间',
    tissueProcessTime: '组织处理时间',
    agaroseEmbedTime: '琼脂糖包埋样本时间',
    embedBy: '包埋人',
    dehydrateTime: '脱水时间',
    agaroseSendTime: '琼脂糖包埋样本送样时间',
    paraffinEmbedTime: '石蜡包埋时间',
    sectionTime: '切片时间',
    stain: '染色',
    markerExpression: 'mark的表达情况',
    operatorName: '操作人',
    remark: '备注',
    updateTime: '最后修改',
    action: '操作'
  },

  /** 行内徽标 / 特殊单元格的文案 */
  badge: {
    /** 待核验 / 无效的外部送样：石蜡块编号一格显示「待核验」 */
    pendingBlockNo: '待核验'
  },

  rowAction: {
    verify: '核验',
    edit: '编辑',
    delete: '删除',
    deleteConfirm: '确认删除石蜡块「{no}」这一条送样记录？',
    deleted: '已删除',
    /** 待核验 / 无效的送样不能走普通保存（后端会 400） */
    readonlyTip: '待核验 / 无效的送样只能走核验',
    sectioned: '已切片',
    notSectioned: '未切片'
  },

  /** 染色 / marker 的展示（导出与列表同一套口径） */
  cell: {
    stainNone: '无染色',
    stainOther: '其他',
    stainOtherFull: '其他（{name}）',
    noStain: '—',
    markerColon: '：',
    markerSeparator: '；',
    openSample: '回到这条样本',
    openSampleBySubmitNo: '这条样本还没核验、没有内部编号，这里显示送检单号；点击回到这条样本'
  },

  // ── 抽屉 ──────────────────────────────────────────────────────────────────
  drawer: {
    addTitle: '新增石蜡包埋记录',
    editTitle: '编辑石蜡包埋记录',
    verifyTitle: '核验石蜡包埋送样',
    sectionEmbed: '包埋信息',
    sectionProcess: '工序时间',
    sectionStain: '染色与 marker',
    sectionOther: '操作与备注',
    lastModified: '最后修改：{name} · {time}',
    lastModifiedNever: '最后修改：从未修改',
    save: '保存',
    cancel: '取 消',
    saved: '已保存',
    loadFailed: '读取失败，请重试',
    required: '必填',

    // 选样本（远程搜索，只列有效样本）
    sample: '选择样本',
    samplePlaceholder: '输入内部编号搜索（只列已核验有效的样本）',
    sampleSearching: '搜索中…',
    sampleNoResult: '没有匹配的有效样本',
    sampleRequired: '请选择所挂样本',
    sampleLocked: '所挂样本已确定，不能更换',
    sampleInternalNo: '内部编号',
    sampleSubmitNo: '送检单号',
    sampleTissueReceive: '收样日期',

    // 字段
    paraffinBlockNo: '石蜡块编号',
    paraffinBlockNoPlaceholder: '如 T-E01-1（全库唯一）',
    paraffinBlockNoRequired: '石蜡块编号必填',
    sampleType: '样本类型',
    organoidSourceType: '类器官来源类型',
    tissueReceiveTime: '组织收样时间',
    tissueProcessTime: '组织处理时间',
    agaroseEmbedTime: '琼脂糖包埋样本时间',
    embedBy: '包埋人',
    dehydrateTime: '脱水时间',
    agaroseSendTime: '琼脂糖包埋样本送样时间',
    paraffinEmbedTime: '石蜡包埋时间',
    sectionTime: '切片时间',
    stain: '染色',
    stainOther: '具体染色名称',
    stainOtherPlaceholder: '如 Masson',
    stainNoneExclusive: '「无染色」与其余染色互斥',
    stainOtherRequired: '选了「其他」必须写具体染色名称',
    marker: 'marker 表达',
    markerName: '名称',
    markerNamePlaceholder: '如 Ki67',
    markerExpression: '表达',
    markerAdd: '添加 marker',
    markerRemove: '删除',
    operatorName: '操作人',
    remark: '备注',

    // 核验段
    verifyStatus: '核验状态',
    submitSource: '来源',
    invalidReason: '无效原因',
    invalidReasonPlaceholder: '外部看得到这句话',
    invalidReasonRequired: '请写判无效的原因',
    verifyHint: '判为有效要给石蜡块编号（全库唯一），之后照常补工序与染色；判为无效必须写原因。',
    saveValid: '判为有效并保存',
    saveInvalid: '判为无效',
    verifiedValid: '已判为有效',
    verifiedInvalid: '已判为无效',
    invalidReasonTitle: '判为无效的原因',
    /** ★ ticket §0 口径复述 4：所挂样本还没核验有效时置灰并写明原因 */
    sampleNotVerified: '所挂样本还没核验有效（当前状态：{status}），不能判为有效',
    sampleVerifiedTip: '所挂样本已核验有效，可以判为有效',

    // 染色字典标签用 useDict 出，这里只放按钮组的顺序说明
    stainHint: '可多选；「无染色」与其余互斥；选「其他」要写具体名称。',
    markerHint: '一行一个 marker；没有名称的只写表达（导出时也只写表达）。',

    untitledMarker: '（无名）'
  },

  // ── 提示语 ────────────────────────────────────────────────────────────────
  msg: {
    exportNotYet: '导出尚未接入',
    loadUnitsFailed: '单位 / 组别读取失败',
    /** 普通保存失败（拿不到后端 msg 时的兜底；拿得到就用后端原话 —— issue #145） */
    saveFailed: '保存失败，请重试',
    /** 核验 / 改判失败（同上：拿不到后端 msg 时的兜底） */
    verifyFailed: '核验失败，请重试'
  }
};
