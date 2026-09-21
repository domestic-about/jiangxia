/**
 * ocr 域 · 实体 / BO / VO。
 *
 * <p>拍照识别（可插拔 provider；dev / test 用 StubOcrProvider）——OCR 域各 ticket 落这里。
 *
 * <p>空包先摆出来是为了让后续 ticket 的代码落点固定（ADR-0001：域之间用包隔开，
 * ticket 的 touches 精确到包）；本 ticket 不在这些域里写业务逻辑。
 */
package org.dromara.lqg.ocr.domain;
