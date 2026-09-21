// ============================================================================
// 域内 i18n（zh_CN）· AUTH 域 —— AUTH-GROUP-001（来源单位与组别 + 外部用户核验）
// 约定见 src/lang/lqg/sys.zh_CN.ts 顶部：文件名 `auth-group.*` 里的 '-' 之前是域名，
// 所以键路径 = `lqg.auth.*`，与 auth-staff.* 合并（src/lang/index.ts 的 split('-') 解析），
// 不往上游两个共享大文件里加 key。
// ============================================================================

export default {
  unit: {
    title: '来源单位与组别',
    subtitle: '单位与组别**不物理删**，只启用 / 停用。停用的单位与组别不再出现在小程序的选择器里，已绑定的人不受影响。',
    refresh: '刷新',
    addUnit: '新增单位',

    // 左栏（单位）
    unitPanel: '来源单位',
    colUnitName: '单位名称',
    colGroupCount: '组别数',
    colUnitStatus: '状态',
    unitEmpty: '还没有来源单位，点右上角「新增单位」添加。',

    // 右栏（组别）
    groupPanel: '该单位的组别',
    groupPanelHint: '（{unit}）',
    groupPanelNone: '先在左边选一个单位',
    addGroup: '新增组别',
    colGroupName: '组别名称',
    colVerifiedCount: '已核验人数',
    colGroupStatus: '状态',
    groupEmpty: '这个单位还没有组别，点上方「新增组别」添加。',

    // 状态
    statusActive: '启用',
    statusPending: '待核验',
    statusDisabled: '停用',
    toggleEnable: '启用',
    toggleDisable: '停用',

    // 单位弹窗
    unitDialogAdd: '新增来源单位',
    unitDialogEdit: '修改来源单位',
    unitName: '单位名称',
    unitNamePlaceholder: '请输入单位名称（全库唯一）',
    remark: '备注',
    remarkPlaceholder: '可留空',

    // 组别弹窗
    groupDialogAdd: '新增组别',
    groupDialogEdit: '修改组别',
    groupName: '组别名称',
    groupNamePlaceholder: '同一单位内不可重名',
    groupUnit: '所属单位',

    // 校验与提示
    unitNameRequired: '单位名称不能为空',
    groupNameRequired: '组别名称不能为空',
    unitSaved: '单位已保存',
    groupSaved: '组别已保存',
    unitEnabled: '单位已启用',
    unitDisabled: '单位已停用',
    groupEnabled: '组别已启用',
    groupDisabled: '组别已停用',
    toggleUnitConfirm: '确认把单位「{name}」改为{action}？停用后它不再出现在小程序的选择器里，已绑定的人不受影响。',
    toggleGroupConfirm: '确认把组别「{name}」改为{action}？停用后它不再出现在小程序的选择器里，已绑定的人不受影响。'
  },
  extuser: {
    title: '外部用户',
    subtitle: '核验外部人员自选的单位与组别。自填的单位 / 组别在通过时**必须**选「新建」或「归并到已有」——核验通过后他才开始与同组同事互看样本。',
    refresh: '刷新',
    filterStatus: '核验状态',
    filterUnit: '单位',
    filterAll: '全部',

    // 列
    colName: '姓名',
    colPhone: '手机号',
    colUnit: '单位',
    colGroup: '组别',
    colStatus: '核验状态',
    colSampleCount: '提交样本数',
    colLastLogin: '最近登录',
    colAction: '操作',
    selfInputTag: '自填',
    notFilled: '未填写',
    neverLogin: '从未登录',
    empty: '没有符合条件的外部用户。',

    // 状态标签
    statusUnbound: '未填写',
    statusPending: '待核验',
    statusVerified: '已核验',
    statusRejected: '已驳回',

    // 行操作
    verify: '核验',
    regroup: '改归组',

    // 核验弹窗
    dialogTitle: '外部用户组别核验',
    dialogUser: '外部用户',
    dialogCurrent: '当前填写',
    approve: '通过',
    reject: '驳回',
    approveHint: '通过后他与同单位同组的同事互相可见样本。',
    selfInputNotice: '他的单位 / 组别是自己填的，通过前必须二选一：',
    newOption: '新建单位与组别',
    newOptionHint: '把「{unit}」「{group}」建成新的启用单位与组别',
    mergeOption: '归并到已有',
    mergeOptionHint: '从下面选一个已存在的单位与组别',
    mergeUnit: '单位',
    mergeGroup: '组别',
    mergeGroupFirst: '请先选单位',
    rejectReason: '驳回原因',
    rejectReasonPlaceholder: '请填写驳回原因（必填，外部人员能看到）',
    groupMismatch: '组别不属于所选单位',

    // 校验与提示
    rejectReasonRequired: '驳回必须填写原因',
    mergeRequired: '请把「归并到已有」的单位与组别都选上',
    approved: '已通过核验',
    rejected: '已驳回',
    regrouped: '已改归组',
    emptyGroup: '该单位下还没有启用中的组别，请先到「来源单位与组别」里建一个。'
  }
};
