// ============================================================================
// 域内 i18n（zh_CN）· QC 域 —— QC-WEB-001（工作台质控文档编辑页）
//
// 约定（SYS-WEB-001 立）：这是**无票号后缀**的域文件 → default 导出直接是域对象，
//   键路径 = `lqg.qc.<key>`（src/lang/index.ts 按文件名 '-' 前段归并域名）。
//   QC-WEB-002 续用时新建 `qc-web-002.zh_CN.ts`，default 导出 `{ ... }` 会被合并进同一层
//   （与 auth-staff / auth-group 的写法不同：那两个是带票号后缀的文件）。
//   **不要**往 src/lang/zh_CN.ts 大文件里加 key。
//
// 配对的英文在同目录 qc.en_US.ts（两个文件的 key 集合必须一致）。
// ============================================================================

export default {
  // ── 上传（图片位与附件共用的两条提示）────────────────────────────────────────
  uploading: '正在上传，请稍候…',
  uploadFailed: '上传失败',

  // ── 图片位（components/ImageSlotUploader.vue）─────────────────────────────
  image: {
    add: '上传图片',
    tip: '1-{max} 张，可拖拽调整顺序、点图放大',
    broken: '图片加载失败',
    badType: '只支持 jpg / png / tif / tiff / bmp / webp / gif 图片',
    tooLarge: '图片大小不能超过 {max} MB',
    added: '图片已上传',
    removed: '图片已移除',
    removeConfirm: '确认移除这张图片？'
  },

  // ── 通用附件（components/AttachmentList.vue）──────────────────────────────
  attachment: {
    title: '附件',
    add: '添加附件',
    empty: '还没有附件',
    tip: '可挂多个附件；单个不超过 50 MB',
    sizeUnknown: '大小未知',
    tooLarge: '附件大小不能超过 {max} MB',
    added: '附件已添加',
    removed: '附件已移除',
    removeConfirm: '确认移除附件「{name}」？'
  },

  editor: {
    // 页头
    title: '质控文档',
    readonlyHint: '以下七项从样本主档带出，只读；要改请去样本总表',
    back: '‹ 返回样本总表',
    sourceUnit: '来源单位',
    donorName: '患者姓名',
    gender: '性别',
    receiveDate: '收样时间',
    processTime: '处理时间',
    operatorName: '操作人',
    internalNo: '内部编号',

    // 页签
    tabSampleQc: '样本质控表',
    tabOrganoidQc: '类器官质控表',
    tabScore: '类器官质量评分表',
    statusDraft: '草稿',
    statusPublished: '已完成',
    organoidPlaceholder: '类器官质控表的编辑在 QC-WEB-002 接真（本张先占位）',
    scorePlaceholder: '类器官质量评分表的编辑在 QC-WEB-002 接真（本张先占位）',

    // 右栏预览面板：面板自身在 components/PreviewPane.vue
    // （DOC-PUBLISH-001 起生效；它的文案在 lang/lqg/qc-publish.zh_CN.ts 的 lqg.qc.preview.*）
    previewTitle: '预览',
    previewPlaceholder: '保存草稿后点「预览」',
    previewPlaceholderSub: '点「预览」生成页面图；左侧与将要下载的 Word / PDF 同源',
    renderFailed: '这份文档渲染失败了',
    renderNoReason: '（后端没给原因）',
    regenerate: '重新生成',
    renderPending: '正在生成…',
    renderDone: '已生成 {pages} 页',
    regenerated: '已重新生成',
    renderStillFailed: '还是失败：',

    // 页脚按钮
    saveDraft: '保存草稿',
    preview: '预览',
    publish: '完成并同步给送检方',
    footerHint: '「预览」只看不发布；「完成并同步」之后送检方才看得到',

    // 提示与文案
    saved: '草稿已保存',
    loadFailed: '质控文档加载失败',
    missingSampleId: '地址里缺少 sampleId：请从样本总表的「质控文档」进入',
    unsavedTitle: '有未保存的改动',
    unsavedMessage: '这一页还有没保存的改动，离开就会丢掉。确定离开吗？',
    unsavedLeave: '离开',
    unsavedStay: '留下继续编辑'
  },

  // ── 样本质控表页签（editor/SampleQcTab.vue）───────────────────────────────
  tab: {
    patientNo: '患者编号',
    patientNoPlaceholder: '如 P-0231',
    samplingSite: '取样部位',
    samplingMethod: '取样方式',
    clinicalDiagnosis: '临床诊断 / 既往治疗',
    receiveDesc: '收样描述',
    viability: '细胞活率测定（附件）',
    viabilityAdd: '上传活率报告',
    viabilityUnnamed: '已上传的活率报告',
    viabilityReplaceHint: '点击文件名可替换',
    viabilityRemove: '移除',
    viabilityPicked: '已选择「{name}」，保存草稿后生效',
    origTitle: '收样原始情况 · 图片',
    observeTitle: '样本观察情况 · 图片',
    pretreatTitle: '样本预处理情况 · 图片',
    descLabel: '情况描述'
  }
};
