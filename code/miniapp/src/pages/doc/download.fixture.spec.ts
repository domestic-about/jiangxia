// 文档文件名 / 平台能力判定的用例（DOC-MP-002 · accept 1 的最后一段）。
//
// ★ 用例**写在 spec 里**（ticket §2 明确允许）：规则就是 ticket §0 口径 5
//   「文件名 = 文档名 + 编号：外部用送检单号、内部用内部编号；合并文件叫『质控文档（合并）』」，
//   这条规则的真相源在 `download.ts` 与后端 `DocKinds.label` 两处，没有可读的 fixture
//   （`doc/verify/fixtures/**` 是只读区，本票不往那里写）。
// ★ 期望值全部写死成**字面量**，不从被测函数反推（否则「改坏实现 → 期望跟着变」）。
import { describe, expect, it } from 'vitest'
import {
  MERGED_FILE_BASE,
  docFileBase,
  downloadFileName,
  extOf,
  fileSizeText,
  formatExt,
  hasMergedRow,
  imageOpenUrl,
  isImageFile,
  normalizeFormat,
  openDocumentType,
  stateText,
  thumbUrlOf,
  waitForMerged,
} from './download'

describe('downloadFileName(docKind, no, format)', () => {
  it('单份 + 内部编号 + pdf：文档名-内部编号.pdf', () => {
    expect(downloadFileName('sample_qc', 'T-hli01', 'pdf')).toBe('样本质控表-T-hli01.pdf')
  })

  it('单份 + 送检单号 + docx（外部身份走的就是这一支）', () => {
    expect(downloadFileName('organoid_score', 'SJ90000001', 'docx')).toBe('类器官质量评分表-SJ90000001.docx')
  })

  it('合并件叫「质控文档（合并）」，不是「合并件」也不是别的', () => {
    expect(MERGED_FILE_BASE).toBe('质控文档（合并）')
    expect(downloadFileName('merged', 'T-hli01', 'pdf')).toBe('质控文档（合并）-T-hli01.pdf')
    expect(downloadFileName('merged', 'SJ90000004', 'docx')).toBe('质控文档（合并）-SJ90000004.docx')
  })

  it('没有编号就不拼那一段（后端 displayName 的回落口径一致）', () => {
    expect(downloadFileName('organoid_qc', '', 'docx')).toBe('类器官质控表.docx')
    expect(downloadFileName('organoid_qc', null, 'pdf')).toBe('类器官质控表.pdf')
    expect(downloadFileName('organoid_qc', '   ', 'pdf')).toBe('类器官质控表.pdf')
  })

  it('格式归一化：大写 / 带空格按 pdf；认不出来按 docx（不静默给错格式的是后端，它会 400）', () => {
    expect(normalizeFormat('PDF')).toBe('pdf')
    expect(normalizeFormat(' pdf ')).toBe('pdf')
    expect(normalizeFormat('docx')).toBe('docx')
    expect(normalizeFormat(undefined)).toBe('docx')
    expect(formatExt('PDF')).toBe('.pdf')
    expect(formatExt('')).toBe('.docx')
  })

  it('不认识的 docKind 不崩：主干回落成原值', () => {
    expect(downloadFileName('bogus_kind', 'X1', 'pdf')).toBe('bogus_kind-X1.pdf')
    expect(docFileBase('')).toBe('质控文档')
  })
})

describe('附件 / 图片的平台能力判定', () => {
  it('openDocument 的 fileType 按扩展名给（含 query 的签名链接也认）', () => {
    expect(openDocumentType('质控文档-T1.pdf')).toBe('pdf')
    expect(openDocumentType('https://oss.example.com/a/b.docx?sign=xyz&e=1')).toBe('docx')
    expect(openDocumentType('x.xlsx')).toBe('xls')
    expect(openDocumentType('y.pptx')).toBe('ppt')
    expect(openDocumentType('z.txt')).toBeUndefined()
    expect(openDocumentType('')).toBeUndefined()
  })

  it('图片附件走 previewImage，文档附件走 openDocument', () => {
    expect(isImageFile('a.JPG')).toBe(true)
    expect(isImageFile('a.png?v=1')).toBe(true)
    expect(isImageFile('a.tif')).toBe(false)
    expect(isImageFile('a.pdf')).toBe(false)
  })

  it('★ 看原图：默认开 url（原图）；TIFF 等小程序打不开的格式退回 previewUrl 并标 fallback', () => {
    const tiff = { url: 'https://oss.example.com/scan.tif', previewUrl: 'https://oss.example.com/scan_preview.jpg' }
    expect(imageOpenUrl(tiff)).toEqual({ url: 'https://oss.example.com/scan_preview.jpg', fallback: true })

    const jpg = { url: 'https://oss.example.com/scan.jpg', previewUrl: 'https://oss.example.com/scan_preview.jpg' }
    expect(imageOpenUrl(jpg)).toEqual({ url: 'https://oss.example.com/scan.jpg', fallback: false })

    // 原图为空也要给一个能开的（退回缩略图），不返回空地址
    expect(imageOpenUrl({ url: '', previewUrl: 'https://oss.example.com/only_preview.jpg' }))
      .toEqual({ url: 'https://oss.example.com/only_preview.jpg', fallback: true })
  })

  it('缩略图用 previewUrl；后端没做缩略图时回落原图', () => {
    expect(thumbUrlOf({ url: 'u', previewUrl: 'p' })).toBe('p')
    expect(thumbUrlOf({ url: 'u', previewUrl: null })).toBe('u')
    expect(thumbUrlOf(null)).toBe('')
  })

  it('附件大小的显示；没有大小就空着（不编一个 0 B）', () => {
    expect(fileSizeText(512)).toBe('512 B')
    expect(fileSizeText(2048)).toBe('2.0 KB')
    expect(fileSizeText(3 * 1024 * 1024)).toBe('3.0 MB')
    expect(fileSizeText(0)).toBe('')
    expect(fileSizeText(null)).toBe('')
  })

  it('extOf 认路径里的扩展名（带 query 也行）', () => {
    expect(extOf('a/b/c.PDF?x=1')).toBe('pdf')
    expect(extOf('https://x/y')).toBe('')
  })
})

describe('★ 合并件「还没渲染好」这个态（DOC-MP-001 §7.4 点名的风险）', () => {
  it('清单里有 merged 行 = 这一版合并件已经渲染成功、可以下', () => {
    expect(hasMergedRow([{ docKind: 'sample_qc' }, { docKind: 'merged' }])).toBe(true)
  })

  it('清单里只有成员、没有 merged 行 = 份数够但合并件还没渲染好（要轮询，不能直接下）', () => {
    expect(hasMergedRow([{ docKind: 'sample_qc' }, { docKind: 'organoid_qc' }])).toBe(false)
    expect(hasMergedRow([])).toBe(false)
    expect(hasMergedRow(null)).toBe(false)
  })

  it('状态文案不泄露内部错误：失败态是一句人话', () => {
    expect(stateText('failed')).toBe('文档暂时无法预览，请稍后再试')
    expect(stateText('generating')).toBe('文档生成中')
    expect(stateText('ready')).toBe('')
  })

  it('★ waitForMerged：渲染完之前一直轮询，渲染好立刻返回 true（不等满整轮）', async () => {
    let calls = 0
    const ok = await waitForMerged(async () => {
      calls += 1
      return calls >= 3 // 第 3 次探到「清单里有 merged 行了」
    }, { intervalMs: 1, timeoutMs: 500 })
    expect(ok).toBe(true)
    expect(calls).toBe(3)
  })

  it('★ waitForMerged：一直没渲染好就超时返回 false（调用方给「可重试」，不是死等）', async () => {
    let calls = 0
    const ok = await waitForMerged(async () => {
      calls += 1
      return false
    }, { intervalMs: 1, timeoutMs: 20 })
    expect(ok).toBe(false)
    expect(calls).toBeGreaterThan(1)
  })
})
