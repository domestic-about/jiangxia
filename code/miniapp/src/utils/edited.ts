/**
 * 这一行**新建之后被改过没有**（历史编辑记录的「新增 / 修改」小标）。
 *
 * ★ 不能只看 `updateTime` 空不空：后端新建时就把 update_time 填成和 create_time 同一刻
 *   （UX 测试 MP-08：刚建、从没改过的记录也标「修改」）。判据 = 更新时间比创建时间晚 1 秒以上；
 *   没给创建时间时退回旧判据（有更新时间就算改过）。
 */
export function wasEdited(row: { createTime?: string | null, updateTime?: string | null }): boolean {
  if (!row.updateTime) {
    return false
  }
  if (!row.createTime) {
    return true
  }
  const ms = (v: string) => new Date(v.includes('T') ? v : v.replace(' ', 'T')).getTime()
  const created = ms(row.createTime)
  const updated = ms(row.updateTime)
  if (Number.isNaN(created) || Number.isNaN(updated)) {
    return row.updateTime !== row.createTime
  }
  return updated - created > 1000
}
