/**
 * auth 域 · 业务层。
 *
 * <p>人员、单位、组别与权限（内部人员授权 / 外部用户核验 / 单位组别维护）——AUTH 域各 ticket 落这里。
 *
 * <p>空包先摆出来是为了让后续 ticket 的代码落点固定（ADR-0001：域之间用包隔开，
 * ticket 的 touches 精确到包）；本 ticket 不在这些域里写业务逻辑。
 */
package org.dromara.lqg.auth.service;
