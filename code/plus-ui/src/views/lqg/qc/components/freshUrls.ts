import type { InjectionKey } from 'vue';

// ============================================================================
// 质控编辑页里图片 / 附件地址的「保鲜」（Kevin 本机验收「网页工作台」第 3 行）
//
// 文件存储是私有桶：`GET /lqg/qc/{sampleId}` 给的 url / previewUrl 是 **10 分钟签名链接**
// （后端 QcOssUrls，与渲染产物同一个有效期）。编辑页常常一开就是半天（标签页还会缓存），
// 过了 10 分钟，点图放大、点附件、新渲染的缩略图都会拿着过期链接 403。
//
// ★ 做法：不把有效期拉长（ADR-0005「只发短时签名链接」，患者图片的链接不该一挂就是一整天），
//   而是**用到的时候看一眼这批地址的岁数**，老了就重取一次 bundle 再用：
//   · 缩略图加载失败（el-image @error）
//   · 点图放大之前
//   · 点附件之前
//   编辑页 provide 一个 `ensureFreshUrls()`，图片位 / 附件组件 inject 来调 —— 三个页签不用逐层传。
// ★ 「刚取回来的地址也打不开」是真的坏了（本机 seed 假数据、对象被删），不再重取，显示「图片加载失败」，
//   避免失败 → 重取 → 失败的死循环。
// ============================================================================

/** 编辑页提供的「用之前先保鲜」：地址老了就重取一次（同一时刻只有一次在途），没老直接返回 */
export const QC_FRESH_URLS: InjectionKey<() => Promise<void>> = Symbol('lqgQcFreshUrls');

/** 签名链接 10 分钟过期；取回来超过 5 分钟就当它可能快过期了（留足打开大图 / 下载大附件的余量） */
export const URL_FRESH_MS = 5 * 60 * 1000;

/**
 * 这批地址该不该重取。
 *
 * @param loadedAt 这批地址取回来的时刻（ms；0 = 还没取过）
 * @param now 现在（ms）
 */
export const urlsMayBeStale = (loadedAt: number, now: number): boolean => loadedAt > 0 && now - loadedAt >= URL_FRESH_MS;
