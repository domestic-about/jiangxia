// 2026-10-05 工作台对抗性 UX 测试（WEB-xx）修复用到的通用文案；键路径 lqg.ux.*（约定见 sys.zh_CN.ts 顶部）
export default {
  closeGuard: {
    title: '有未保存的改动',
    message: '这里还有没保存的改动，关掉就会丢掉。确定不要了吗？',
    discard: '不保存，关闭',
    stay: '继续编辑'
  },
  deleteTitle: '删除',
  deleteConfirm: '删除',
  sourceUnitRequired: '请选择来源单位，或在「来源单位名称」里填写',
  tableEmpty: {
    failed: '没能加载出来（网络或服务器出错），不是没有数据。',
    retry: '重新加载'
  },
  extVerify: {
    pickAction: '请先选择「通过」还是「驳回」',
    pickMode: '请选择新建单位与组别，还是归入已有的单位与组别',
    confirmTitle: '确认通过核验',
    confirmApprove: '通过后「{name}」即成为已核验的合作单位人员，可以看到同组的记录。此操作不能在这里撤回。',
    confirmCreate: '同时会新建单位「{unit}」和组别「{group}」。'
  },
  qcFooter: {
    save: '保存',
    publish: '完成并同步',
    saveAndPublish: '保存并同步',
    dirtyHint: '有改动还没保存',
    saveBeforeUnpublish: '先把这一页的改动保存，再撤回',
    emptyItems: '以下几项还空着：{items}。'
  },
  saveUnpublish: {
    title: '这份文档已同步给送检方',
    message: '保存后会变回草稿，送检方暂时看不到这份文档；改完需要重新点「完成并同步」。确定保存吗？',
    confirm: '保存为草稿'
  }
};
