// ============================================================================
// 域内 i18n（zh_CN）· AUTH 域 —— AUTH-STAFF-001（内部人员授权）
// 约定见 src/lang/lqg/sys.zh_CN.ts 顶部：键路径 = `lqg.auth.*`，不往共享大文件里加 key。
// ============================================================================

export default {
  staff: {
    title: '内部人员授权',
    subtitle: '按手机号挂载内部人员；同一手机号已有账号时**原地升级**，不新建第二个账号。',
    refresh: '刷新',
    grant: '按手机号授权',

    // 列
    colName: '姓名',
    colPhone: '手机号',
    colRole: '角色',
    colWxBound: '微信绑定',
    colAction: '操作',
    wxBound: '已绑定',
    wxUnbound: '未绑定',

    // 角色
    roleInternal: '内部人员',
    roleAdmin: '实验室管理员',

    // 行操作
    changeRole: '改角色',
    resetPwd: '重置密码',
    revoke: '撤销授权',

    // 授权弹窗
    dialogTitle: '按手机号授权',
    phone: '手机号',
    phonePlaceholder: '请输入 11 位手机号',
    name: '姓名',
    namePlaceholder: '请输入真实姓名',
    password: '工作台初始密码',
    passwordPlaceholder: '请输入工作台初始密码',
    upgradeTip: '该手机号已登录过小程序，将把原账号升级为内部人员',
    newTip: '该手机号还没有账号，将预建一个内部账号',
    checking: '正在核对该手机号…',

    // 改角色弹窗
    roleDialogTitle: '修改角色',
    // 重置密码弹窗
    pwdDialogTitle: '重置工作台密码',
    newPassword: '新密码',

    // 二次确认
    revokeConfirm: '撤销后该账号将降回外部人员并立即退出登录（账号与历史数据保留）。是否确认撤销「{name}」的内部授权？',

    // 校验
    phoneRequired: '手机号不能为空',
    phoneInvalid: '手机号格式不正确',
    nameRequired: '姓名不能为空',
    roleRequired: '角色不能为空',
    passwordRequired: '密码不能为空',
    passwordMin: '密码至少 6 位',

    // 结果提示
    grantUpgraded: '已把原账号升级为内部人员',
    grantCreated: '已预建内部账号',
    roleChanged: '角色已修改',
    pwdReset: '密码已重置',
    revoked: '已撤销内部授权',
    empty: '还没有内部人员，点右上角「按手机号授权」添加。'
  }
};
