import { describe, expect, it } from 'vitest'
import { qcEmptyItems } from './completeness'

describe('qcEmptyItems：同步前列出还空着的项（不拦，只提示）', () => {
  it('空白的样本质控表：八项全列出', () => {
    expect(qcEmptyItems('sample-qc', { images: {} })).toHaveLength(8)
  })
  it('填齐的样本质控表：空数组', () => {
    const img = [{ id: 1 }]
    expect(qcEmptyItems('sample-qc', {
      patientNo: 'P-1',
      samplingSite: '肝',
      samplingMethod: '穿刺',
      clinicalDiagnosis: '无',
      viabilityOssId: 9,
      images: { orig: img, observe: img, pretreat: img },
    })).toEqual([])
  })
  it('只缺图片与活率：只列这几项', () => {
    expect(qcEmptyItems('sample-qc', { patientNo: 'P-1', samplingSite: '肝', samplingMethod: '穿刺', clinicalDiagnosis: '无', images: { orig: [{}] } }))
      .toEqual(['细胞活率测定（附件）', '样本观察情况的图片', '样本预处理情况的图片'])
  })
  it('空白字符串算空', () => {
    expect(qcEmptyItems('organoid-qc', { formedTime: '  ', growthState: '良好', images: { organoid_observe: [{}] } })).toEqual(['形成类器官时间'])
  })
  it('评分表四项', () => {
    expect(qcEmptyItems('score', { preCultureLevel: 'A' })).toEqual(['培养天数', '类器官数量', '类器官直径'])
  })
})
