export const RECEIPT_OPTIONS = [{ value: 'tissue', label: '组织' }, { value: 'organoid', label: '类器官' }]

export function receiptTimeLabel(key: string, kind: unknown, fallback: string): string {
  if (key !== 'tissueReceiveTime' && key !== 'tissueProcessTime') return fallback
  const prefix = kind === 'tissue' ? '组织' : kind === 'organoid' ? '类器官' : '组织/类器官'
  return `${prefix}${key === 'tissueReceiveTime' ? '收样时间' : '处理时间'}`
}
