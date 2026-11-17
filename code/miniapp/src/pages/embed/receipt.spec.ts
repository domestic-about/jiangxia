import { describe, expect, it } from 'vitest'
import { emptyEmbedForm, internalEmbedPayload, externalEmbedPayload, toEmbedFormValue } from '@/api/embed'
import { receiptTimeLabel } from './receipt'

describe('按记录保存手动收样类别', () => {
  it('旧记录未选择，不能根据样本类型推断', () => {
    const f = toEmbedFormValue({ sampleType: '类器官' })
    expect(f.receiptKind).toBe('')
    expect(receiptTimeLabel('tissueReceiveTime', f.receiptKind, '')).toBe('组织/类器官收样时间')
  })
  it.each(['tissue', 'organoid'])('选择%s仅切换名称、不改日期，回读仍保留选择', (kind) => {
    const f = emptyEmbedForm()
    f.receiptKind = kind
    f.tissueReceiveTime = '2026-11-01'
    f.tissueProcessTime = '2026-11-02'
    const body = internalEmbedPayload(f)
    expect(body).toMatchObject({ receiptKind: kind, tissueReceiveTime: '2026-11-01', tissueProcessTime: '2026-11-02' })
    expect(toEmbedFormValue({ receiptKind: kind }).receiptKind).toBe(kind)
    expect(receiptTimeLabel('tissueProcessTime', kind, '')).toBe(`${kind === 'tissue' ? '组织' : '类器官'}处理时间`)
    expect(externalEmbedPayload(f)).not.toHaveProperty('receiptKind')
  })
})
