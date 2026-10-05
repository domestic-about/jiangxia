// ============================================================================
// 域内 i18n（zh_CN）· SYS 域 —— 工作台首页（SYS-HOME-001 · UI:admin.home）
// 文件名 'home' 的 '-' 之前是域名，所以键路径 = `lqg.home.*`（src/lang/index.ts 自动合并）。
// 不往上游 src/lang/zh_CN.ts 那个共享大文件里加 key。
// ============================================================================

export default {
  title: '工作台',
  subtitle: '待办数每次进来现算，点卡片直达对应列表（为 0 也显示，灰掉的就是「现在没有」）。',
  refresh: '刷新',
  loadFailed: '待办数字没拉到：{msg}（显示的是 0，不代表没有待办）',
  allDone: '现在没有待办',
  go: '去处理 →',

  card: {
    // CR-20260924-10：原「待核验样本」一张卡拆成两张，各进各的样本表
    pendingTissue: {
      title: '待核验样本记录',
      hint: '合作单位送来的组织样本等待核验',
      zero: '没有等待核验的样本记录',
      go: '去样本记录信息表核验 →'
    },
    pendingOrganoid: {
      title: '待核验类器官送样',
      hint: '合作单位送来的类器官送样记录等待核验',
      zero: '没有等待核验的类器官送样',
      go: '去类器官送样记录核验 →'
    },
    pendingEmbeds: {
      title: '待核验石蜡包埋送样',
      hint: '外部提交的石蜡包埋送样等待核验',
      zero: '没有等待核验的石蜡包埋送样',
      go: '去石蜡包埋核验 →'
    },
    cryoOverdue: {
      title: '-80 超期批次',
      hint: '冻存超过阈值天数、还没转液氮',
      zero: '没有超期批次',
      go: '去冻存管理（超期页签）→'
    },
    pendingExtUsers: {
      title: '待核验外部用户',
      hint: '外部人员提交的单位与组别等待归口',
      zero: '没有等待核验的外部用户',
      go: '去人员与单位核验 →'
    },
    renderFailed: {
      title: '文档渲染失败',
      hint: '质控文档生成失败，可在质控文档页重新生成',
      zero: '没有渲染失败的文档',
      go: '去质控文档查看 →'
    }
  },

  recent: {
    title: '最近提交',
    subtitle: '按送检时间倒序，最多 10 条',
    empty: '还没有提交记录',
    colSubmitTime: '提交时间',
    colSubmitNo: '送检单号',
    colSampleKind: '所在表',
    colSourceUnit: '来源单位',
    colSubmitSource: '内外部',
    colVerifyStatus: '核验状态'
  },

  source: {
    internal: '内部',
    external: '外部'
  },

  // 与字典 lqg_verify_status、样本列表同一套叫法（工作台 UX 测试 WEB-21：原来首页叫「已核验 / 已驳回」）
  status: {
    pending: '待核验',
    valid: '有效',
    invalid: '无效'
  }
};
