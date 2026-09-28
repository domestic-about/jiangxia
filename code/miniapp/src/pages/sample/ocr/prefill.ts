// 识别预填的**纯函数**（OCR-MP-001 §2）。
//
// 权威：`UI:mp.sample.form.ocr`（表单顶部识别条 → 识别结果只作预填）与
// `FLOW:F-OCR-01.step4`（只往空着的表单项里填，已手填的不覆盖；预填项加「识别 · 请核对」标记，
// 用户改动后标记消失）。期望值全部在 `doc/verify/fixtures/prefill-cases.json` 里，
// 本文件不自带期望表，`prefill.fixture.spec.ts` 逐例对着 fixture 断。
//
// 四件最容易做反的事（ticket §0 口径复述 1 / §2 与 accept 1 的 counterfeit）：
//
// 1. **只往空项里填**：`Object.assign(form, ocrFields)` 就是事故 —— 用户先填了两项再拍照，
//    回来发现被冲掉，这是最伤人的一种 bug。判空的唯一口径是「trim 后为空」。
// 2. **识别值为空不填也不标**：后端契约说解析不出的键不出现，但「有键而值为空串 / null」
//    在线上仍可能出现（provider 换实现、夹具改口径）；填个空串再打个「请核对」的标，
//    等于让填写人核对一个不存在的东西。
// 3. **表单里没有的键忽略**：识别结果里出现 `internalNo` / `verifyStatus` 这类**内部字段**时，
//    绝不能落进表单 —— 那是把内部字段夹带进提交体（accept 1 counterfeit 第 2 条）。
//    本文件**不维护「可预填字段」白名单**：`form` 自己就是白名单（键不在 `form` 里就忽略），
//    `prefill.ts` 的 `PREFILLABLE_KEYS` 只是给测试与页面看的**文档性清单**。
// 4. **提交的永远是表单当前值**（`FLOW:F-OCR-01.step5`）：本函数只改表单，不碰提交逻辑。

/** 能被识别预填的样本记录信息表字段（**文档性清单**，真正的白名单是调用方给的 `form` 的键集） */
export const PREFILLABLE_KEYS = [
  'sourceUnitName',
  'donorName',
  'gender',
  'age',
  'hospitalNo',
  'tissueType',
] as const

export type PrefillableKey = (typeof PREFILLABLE_KEYS)[number]

/** `POST /mp/ocr/recognize` 的 `data.fields`：认不出的键不出现；前端不做二次猜测 */
export type OcrFields = Record<string, unknown> | null | undefined

export interface PrefillResult<F extends object> {
  /**
   * 合并后的表单。与入参**同一个对象**（页面里 `form` 是个 `ref`，原地改最省事，
   * 也不会有两个 `ref` 各自持一份表单的经典 bug）。
   */
  form: F
  /** 被这次识别**真的填进去**的字段名，升序（「识别 · 请核对」小标就按它渲染） */
  marks: string[]
}

/** trim 后为空才算「没填」；非字符串（数字 / null / undefined）一律当空 */
function blank(value: unknown): boolean {
  if (value === null || value === undefined) {
    return true
  }
  // 数字 0 是**填过**的值（年龄 0 不合法，但判空是判空，不是校验）
  if (typeof value === 'number' || typeof value === 'boolean') {
    return false
  }
  return String(value).trim() === ''
}

/** 识别结果里的值能不能当「识别出一个值」用：空串 / 空白 / null / undefined 都不算 */
function hasValue(value: unknown): boolean {
  if (value === null || value === undefined) {
    return false
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return true
  }
  return String(value).trim() !== ''
}

function asText(value: unknown): string {
  return typeof value === 'string' ? value : String(value)
}

/**
 * 把识别结果合并进表单：只填空项、已手填的不覆盖、空值不填不标、表单里没有的键忽略。
 *
 * @param form       表单当前值（**调用方保证它是普通对象**；`null` / 非对象按空表单处理）
 * @param ocrFields  识别接口 `data.fields`
 * @returns          `{ form, marks }`，`marks` 升序
 */
export function mergeOcrPrefill<F extends object>(
  form: F | null | undefined,
  ocrFields: OcrFields,
): PrefillResult<F> {
  // ★ 内部一律按 `Record<string, unknown>` 操作：往 `F & Record<…>` 这种**泛型交叉**上写
  //   计算键，TS 会报 2862（泛型只能读着索引）。收口在这一处，返回时再还原成 `F`。
  const target: Record<string, unknown> = form && typeof form === 'object'
    ? form as unknown as Record<string, unknown>
    : {}
  const marks: string[] = []
  if (!ocrFields || typeof ocrFields !== 'object') {
    return { form: target as F, marks }
  }
  Object.keys(ocrFields).forEach((key) => {
    // 口径 3：表单里没有这个键 → 忽略（识别结果里的内部字段一个都不许落进表单）
    if (!Object.prototype.hasOwnProperty.call(target, key)) {
      return
    }
    const value = (ocrFields as Record<string, unknown>)[key]
    // 口径 2：识别值为空 → 不填、不标
    if (!hasValue(value)) {
      return
    }
    // 口径 1：已经手填的不覆盖
    if (!blank(target[key])) {
      return
    }
    target[key] = asText(value)
    marks.push(key)
  })
  marks.sort()
  return { form: target as F, marks }
}

/**
 * 把 `POST /mp/ocr/recognize` 的响应收成 `{ ocrFields, rawLines }`。
 *
 * ★ 「接口不给 `data` 键」「`fields` 是 `null`」「`rawLines` 不是数组」三种情况在线上都出现过，
 * 收口在一个纯函数里，页面只管调它 —— 页面里 `body.data.fields` 一路点下去，
 * 遇到 `data` 缺失就是 `TypeError`，识别失败会被伪装成「页面崩了」。
 *
 * ★★ 两种入参**都要收**（本票实测踩过，代价是一次「接口明明把六个字段都回全了、页面却说
 *   没识别出来」的假失败）：
 *     - 接口**整个响应体** `{code,msg,data:{rawLines,fields}}`（`utils/request.ts` 那条路的形状）；
 *     - 已经拆过一层的 **`data` 自己** `{rawLines,fields}`（`api/ocr.ts` 的 `uploadFile`
 *       直接 resolve `body.data`，调用方手里拿的就是这一层）。
 *     只认前者时，`{rawLines,fields}` 会被当成「没有 data 键」→ fields 恒空 → 每次识别都报
 *     「没识别出来，请手动填写」；而单测喂的是前者，**红不了**。
 *     判据是「自己有没有 `data` 这个键」，不是「有没有 `fields`」—— 后者两层的形状里都可能有。
 */
export function readOcrResponse(body: unknown): { ocrFields: Record<string, unknown>, rawLines: string[] } {
  const root = (body && typeof body === 'object' ? body : {}) as Record<string, unknown>
  const inner = Object.prototype.hasOwnProperty.call(root, 'data') && root.data && typeof root.data === 'object'
    ? root.data as Record<string, unknown>
    : root
  const fields = inner.fields && typeof inner.fields === 'object'
    ? inner.fields as Record<string, unknown>
    : {}
  const lines = Array.isArray(inner.rawLines) ? inner.rawLines : []
  return {
    ocrFields: fields,
    rawLines: lines.map(line => (line === null || line === undefined ? '' : String(line))),
  }
}

/** 识别成功后那条提示语里的 N：「已识别 N 项」= 真的填进表单的项数（不是接口回了几个键） */
export function recognizedCount(marks: readonly string[]): number {
  return marks.length
}

/** 识别失败 / 超时 / 被限流时唯一的提示语（`UI:mp.sample.form.ocr`：不阻塞填表） */
export const OCR_FAIL_TEXT = '没识别出来，请手动填写'
