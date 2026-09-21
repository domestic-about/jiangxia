// ============================================================================
// 域内 i18n（zh_CN）· SYS 域样例 —— SYS-WEB-001 立的约定
//
// 约定（所有 WEB ticket 照此办）：
//   * **不要**往 src/lang/zh_CN.ts / en_US.ts 那两个大文件里加 key —— 那是全部域的共享文件，
//     并行开发必撞车。
//   * 每个域在自己的文件里写 src/lang/lqg/<域>.zh_CN.ts（+ 同名 .en_US.ts），
//     导出 default 对象；键路径 = `lqg.<域>.<key>`。
//   * src/lang/index.ts 用 import.meta.glob('./lqg/*.zh_CN.ts', { eager: true }) 自动合并，
//     新增一个域只需要加文件，不用改入口。
//
// 用法：template 里 {{ $t('lqg.sys.brand') }}，script 里 t('lqg.sys.brand')。
// 域标签与 code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg 的子包一致：
//   sys / auth / sample / embed / cryo / qc / doc / ocr / ext
// ============================================================================

export default {
  // 品牌与壳
  brand: '类器官样本管理',
  homePlaceholder: '工作台首页建设中（内容见 SYS-HOME-001）',
  homePlaceholderHint: '业务页面由各域的 WEB ticket 往 src/views/lqg/<域>/ 里加。',

  // 登录页
  login: {
    title: '类器官样本管理',
    username: '账号',
    password: '密码',
    login: '登 录',
    logging: '登 录 中...',
    rememberPassword: '记住密码',
    copyright: '类器官样本管理'
  }
};
