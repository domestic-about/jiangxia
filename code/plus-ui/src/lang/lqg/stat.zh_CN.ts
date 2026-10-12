// 操作人统计（CR-20261012-22：甲方要按人看「这个月 / 今年 / 一共做了多少个」）；键路径 lqg.stat.*
// 表名、列名、「未填」由接口给（工作台与小程序同一份），这里只放页面自己的文案。
export default {
  operator: {
    title: '操作人统计',
    subtitle: '按人、按时间段看每张表做了多少。四张表各算各的，不加在一起；质控表不统计（它的操作人是从样本带出来的）。数字可以点，跳到对应列表逐条核对。',
    preset: {
      week: '本周',
      month: '本月',
      year: '今年',
      lastYear: '去年',
      all: '全部',
      custom: '自定义'
    },
    customBegin: '开始日期（可不填）',
    customEnd: '结束日期（可不填）',
    customTo: '至',
    customHint: '只填结束日期 = 这一天及之前；只填开始日期 = 这一天及之后',
    query: '查询',
    refresh: '刷新',
    inverted: '开始日期不能晚于结束日期',
    range: {
      all: '全部时间',
      between: '{begin} 至 {end}',
      until: '{end} 及之前',
      since: '{begin} 及之后'
    },
    by: '按{label}',
    colName: '姓名',
    total: '合计',
    me: '我',
    unlinked: '未关联账号',
    unlinkedTip: '这是以前手填的姓名，没有对应的内部人员账号，所以单独列一行。点数字到列表里，把这些记录的这一栏重新选一次，就会并到那个人名下。',
    noneTip: '这一栏还没填的记录。点数字到列表里补上。',
    empty: '这段时间没有记录',
    note: {
      tissue: '只算已核验有效的记录',
      organoid: '只算已核验有效的记录',
      embed: '只算已核验有效的石蜡块；没填石蜡包埋时间的只在「全部」里',
      cryo: '按冻存人算；支数 = 冻存数量之和（不是当前剩余）'
    }
  }
};
