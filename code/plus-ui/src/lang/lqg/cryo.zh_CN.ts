// ============================================================================
// 域内 i18n（zh_CN）· CRYO 域 —— CRYO-WEB-001（工作台冻存管理页）
//
// 约定（SYS-WEB-001 立）：键路径 = `lqg.cryo.<key>`；
//   文件名 `cryo[-<票>].zh_CN.ts` 都归并进 `lqg.cryo`（src/lang/index.ts 按 '-' 前段归并）。
//   **不要**往 src/lang/zh_CN.ts 大文件里加 key。
//
// 配对的英文在同目录 cryo.en_US.ts（两个文件的 key 集合必须一致）。
// ★ 文案里不写冻存超期的**天数**（阈值是系统参数 lqg.cryo.overdue-days，页面上只显示
//   后端算出来的「已超 N 天」，写死 14 会与 SYS-WEB-001 的参数设置页打架）。
// ============================================================================

export default {
  // ── 页面与筛选区 ──────────────────────────────────────────────────────────
  title: '-80 冻存管理',
  // 页头一句话（2026-09-24 本机验收，四张表统一口径）：这张表是什么、和样本的关系、谁来填
  subtitle:
    '一行是一个冻存批次，挂在某个样本下（一个样本可以冻多批），点「内部编号」回到样本。冻存记录只有中心内部人员填写，合作单位看不到也不能填；超期的置顶、整行浅红，取空的标「已取空」。',
  search: '搜索',
  reset: '重置',
  loading: '加载中…',
  empty: '没有符合条件的冻存批次',

  /** 顶部页签：四个数字都取后端的 tabCounts（整表口径，翻页不变）；「已取空」是 2026-09-24 加的 */
  tab: {
    all: '全部',
    overdue: '-80 超期',
    ln2: '液氮',
    emptied: '已取空'
  },

  filter: {
    internalNo: '内部编号',
    internalNoPlaceholder: '内部编号（等值）',
    cryoName: '冻存样品',
    cryoNamePlaceholder: '冻存样品名称（模糊）',
    location: '位置',
    locationAll: '全部',
    locationMinus80: '-80 冰箱',
    locationLn2: '液氮',
    overdueOnly: '只看超期',
    freezeTimeRange: '冻存时间',
    freezeTimeBegin: '冻存时间起',
    freezeTimeEnd: '冻存时间止'
  },

  // ── 带 sampleId 进来时顶部的提示条（2026-09-24 本机验收） ─────────────────────
  scope: {
    only: '只看{sample}的冻存批次',
    total: '共 {n} 批',
    showAll: '看全部',
    openSample: '打开样本',
    sampleFallback: '样本 {id}'
  },

  cell: {
    openSample: '回到这条样本'
  },

  // ── 工具栏 ────────────────────────────────────────────────────────────────
  toolbar: {
    add: '新增冻存批次',
    refresh: '刷新',
    export: '导出 -80 冻存',
    exporting: '正在导出…',
    exportDone: '导出完成',
    exportEmpty: '当前筛选没有可导出的批次'
  },

  // ── 表格列（模板 9 列 + 代数 / 当前剩余（与导出同序）+ 内部编号 / 当前位置 / 最后修改 + 操作） ──
  col: {
    freezeTime: '冻存时间',
    cryoName: '冻存样品',
    initQty: '冻存数量/支',
    density: '冻存密度',
    inMinus80: '暂存-80度超低温冰箱',
    frozenBy: '冻存人',
    toLn2Time: '-80度超低温冰箱转移至液氮时间',
    ln2Location: '液氮储存位置',
    remark: '备注',
    internalNo: '内部编号',
    passage: '代数',
    remainingQty: '当前剩余/支',
    location: '当前位置',
    overdue: '超期',
    updateTime: '最后修改',
    action: '操作'
  },

  /** 行内徽标 / 特殊单元格的文案 */
  badge: {
    /** ★ 未超期时 overdueDays 是 null（不是 0）：只有超期行才渲染这个徽标 */
    overdue: '已超 {days} 天',
    overdueToday: '今天到期',
    emptyQty: '已取空'
  },

  flag: {
    yes: '是',
    no: '否'
  },

  location: {
    minus80: '-80 冰箱',
    ln2: '液氮'
  },

  flowType: {
    take: '取走',
    add: '补入',
    adjust: '盘点调整',
    unknown: '—'
  },

  rowAction: {
    take: '取走',
    add: '补入',
    adjust: '盘点调整',
    toLn2: '转液氮',
    flow: '流水',
    edit: '编辑',
    delete: '删除',
    deleteConfirm: '确认删除冻存批次「{name}」？有未删流水的批次删不掉。',
    deleted: '已删除',
    notYet: '该功能尚未接入'
  },

  // ── 新增 / 编辑抽屉 ───────────────────────────────────────────────────────
  drawer: {
    addTitle: '新增冻存批次',
    editTitle: '编辑冻存批次',
    sectionBatch: '批次信息',
    sectionStore: '存放位置',
    /** 模板里没有、系统要的字段（代数），放在模板列之后 */
    sectionExtra: '补充信息',
    lastModified: '最后修改：{name} · {time}',
    lastModifiedNever: '最后修改：从未修改',
    save: '保存',
    cancel: '取 消',
    saved: '已保存',
    loadFailed: '读取失败，请重试',

    // 选样本（远程搜索，只列已核验有效的样本）
    sample: '选择样本',
    samplePlaceholder: '输入内部编号搜索（只列已核验有效的样本）',
    sampleRequired: '请选择所挂样本',
    sampleLocked: '所挂样本已确定，不能更换',

    // 字段
    cryoName: '冻存样品',
    cryoNamePlaceholder: '手填，如 T-hli01-GZ-N-P2-EM2-2e5',
    cryoNameRequired: '冻存样品名称必填',
    passage: '代数',
    passagePlaceholder: '如 P2',
    passageRequired: '代数必填，格式形如 P2',
    freezeTime: '冻存时间',
    freezeTimeRequired: '冻存时间必填',
    initQty: '冻存数量/支',
    /** ★ 初始支数可改（CR-20260917-04）；改了同样逐笔校验，任一步 < 0 → 后端 400 */
    initQtyTip: '这是「初始」支数（导出「冻存数量/支」用的就是它）；改小到透支会被后端拒绝，剩余只在流水里变。',
    initQtyRequired: '冻存数量必须是正整数',
    density: '冻存密度',
    densityPlaceholder: '如 2e5',
    inMinus80: '暂存-80度超低温冰箱',
    inMinus80Required: '请选择是 / 否',
    frozenBy: '冻存人',
    toLn2Time: '转移至液氮时间',
    ln2Location: '液氮储存位置',
    ln2LocationPlaceholder: '如 1号罐-1架-A2',
    ln2LocationRequired: '选「否」或登记了转液氮时间时必须填液氮储存位置',
    remark: '备注'
  },

  // ── 流水抽屉 ──────────────────────────────────────────────────────────────
  flow: {
    title: '出入库流水',
    subtitle: '按时间倒序；「操作后剩余」是读时算的（不落库）。每行可改可删，改删后剩余与超期标记当场刷新。',
    loading: '加载中…',
    empty: '这批还没有任何出入库登记',
    colTime: '时间',
    colType: '类型',
    colDelta: '变化量',
    colFrom: '取自',
    colOperator: '经手人',
    colPurpose: '用途',
    colBalanceAfter: '操作后剩余',
    colLastModified: '最后修改',
    lastModifiedNever: '从未修改',
    lastModified: '已改 · {name} {time}',
    currentRemaining: '当前剩余：{qty} 支',
    close: '关 闭',
    edit: '修改',
    remove: '删除',
    removeConfirm: '确认删除这一笔「{type} {delta}」登记？删掉之后追溯就断了（是软删，库里仍留痕）。',
    removed: '已删除',
    edited: '已修改',
    removedRejected: '删除被拒绝（会让后面某一步剩余为负）：{msg}',
    editRejected: '修改被拒绝：{msg}',
    /** 被拒时后端会指出是哪一笔（如「取走支数超过当前剩余（…）」） */

    // 取走 / 补入 / 盘点调整（三个同款弹窗）
    dialogTake: '取走登记',
    dialogAdd: '补入登记',
    dialogAdjust: '盘点调整',
    dialogEdit: '修改登记',
    qty: '支数',
    qtyTake: '取走支数',
    qtyAdd: '补入支数',
    qtyAdjust: '调整量（可正可负、不为 0）',
    qtyTakeRequired: '取走支数必须是正整数',
    qtyAddRequired: '补入支数必须是正整数',
    qtyAdjustRequired: '调整量不能为 0',
    qtyOverRemaining: '取走支数不能超过当前剩余 {qty} 支',
    purpose: '用途 / 原因',
    purposePlaceholder: '如 复苏培养',
    purposeRequired: '盘点调整必须写原因',
    operatorName: '经手人',
    flowTime: '发生时间',
    flowTypeLocked: '登记类型不能改；要换类型就删掉重登。',
    save: '保存',
    cancel: '取 消',
    saved: '已登记',
    editSaved: '已保存',
    saveFailed: '保存失败，请重试',
    balanceTipCurrent: '当前剩余 {qty} 支',
    /** 取走让这一批从有变成 0 支时，提交前多问的那一句（与小程序同一句） */
    emptyConfirm: '登记后这一批就取空了（剩 0 支），确定吗？'
  },

  // ── 转液氮弹窗 ────────────────────────────────────────────────────────────
  toLn2: {
    title: '登记转液氮',
    tip: '保存后批次位置当场变液氮、超期标记与页签数字当场刷新（不必等定时任务）。',
    time: '转移至液氮时间',
    timeRequired: '转移时间必填，且不能早于冻存时间',
    location: '液氮储存位置',
    locationRequired: '液氮储存位置必填',
    save: '保存',
    cancel: '取 消',
    saved: '已登记转液氮'
  },

  // ── 提示语 ────────────────────────────────────────────────────────────────
  msg: {
    sampleFiltered: '已按样本过滤',
    /** 普通保存失败（拿不到后端 msg 时的兜底；拿得到就用后端原话） */
    saveFailed: '保存失败，请重试',
    loadFailed: '读取失败，请重试'
  }
};
