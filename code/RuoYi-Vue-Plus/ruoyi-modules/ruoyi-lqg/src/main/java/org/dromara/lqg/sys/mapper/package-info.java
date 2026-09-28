/**
 * sys 域 · 持久层。
 *
 * <p>系统域：健康探针、字典、系统参数、首页待办（本 ticket 先落 GET /lqg/sys/ping）——SYS 域各 ticket 落这里。
 *
 * <p>空包先摆出来是为了让后续 ticket 的代码落点固定（ADR-0001：域之间用包隔开，
 * ticket 的 touches 精确到包）；本 ticket 不在这些域里写业务逻辑。
 */
package org.dromara.lqg.sys.mapper;
