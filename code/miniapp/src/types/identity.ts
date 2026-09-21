// 内外部身份：小程序端只认后端 `GET /mp/me` 返回的 `identity` 字段
// （ADR-0003 / UI:mp.home.entries：前端不自行判断身份）。
//
// 后端只会给 'internal' 或 'external'；缺失、空串、大小写不同、
// 其它任何值都视为「身份未知」，一个入口都不渲染——绝不默认当内部。
// 契约：doc/api-contract.md AUTH 段。
export type Identity = 'internal' | 'external'

/** 身份未知时的归一化结果 */
export type ResolvedIdentity = Identity | null

// 把后端返回的原始值归一化成 Identity | null。
// 大小写敏感是刻意的：'INTERNAL' 不是后端契约里的值，按未知处理
// （doc/verify/fixtures/home-entries-cases.json 里那条用例就是这么定的）。
export function normalizeIdentity(raw: unknown): ResolvedIdentity {
  if (raw === 'internal' || raw === 'external') {
    return raw
  }
  return null
}

/** 身份徽标文案（UI:mp.home / UI:mp.me：内部人员 / 合作单位） */
export function identityLabel(identity: ResolvedIdentity): string {
  if (identity === 'internal') {
    return '内部人员'
  }
  if (identity === 'external') {
    return '合作单位'
  }
  return ''
}

/** 身份徽标的修饰类：浅底深字用 `.lqg-tag`，颜色由修饰类给（落地规范 §5.6） */
export function identityTagClass(identity: ResolvedIdentity): string {
  if (identity === 'internal') {
    return 'lqg-tag--internal'
  }
  if (identity === 'external') {
    return 'lqg-tag--external'
  }
  return ''
}
