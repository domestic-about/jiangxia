/**
 * ext 域 · 接口层。
 *
 * <p>外部接口的唯一出入口（ExtScopeService 隔离卡点，见 ADR-0004）——EXT 域各 ticket 落这里。
 *
 * <p>空包先摆出来是为了让后续 ticket 的代码落点固定（ADR-0001：域之间用包隔开，
 * ticket 的 touches 精确到包）；本 ticket 不在这些域里写业务逻辑。
 */
package org.dromara.lqg.ext.controller;
