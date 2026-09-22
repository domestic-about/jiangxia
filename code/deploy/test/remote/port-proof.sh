#!/usr/bin/env bash
# SYS-STAGING-001 · accept 第 2 条的**等价取证**（本机 nc 被透明代理劫持，见 ops §4）
#
# 本机 `nc -z` 恒报「可达」（连 1.1.1.1:59999 都可达）→ 票面那条 `! nc -z HOST 5432/6379/9000`
# 在开发机上**必然假红**，与实现无关。改在**测试机上打自己的公网 IP**（发夹探针）——
# 这是可判别的：公网没绑的端口连不上、公网绑了的连得上（阳性对照）。
#
# 本脚本在测试机上跑，输出三件里的两件：
#   ① 发夹探针（公网 IP 上的 15432/16379/19000 应不可达；80/443/3306/8080 应可达=阳性对照）
#   ② ss -lntp 的 bind 地址（本项目一律 127.0.0.1，不是 0.0.0.0）
# 第三件（安全组入方向清单）要用 aliyun CLI，在开发机上取，由 deploy.sh prove-ports 合并。
set -uo pipefail
PUB_IP="${LQG_TEST_PUB_IP:-118.178.109.11}"

probe() { # probe <host> <port> → 打印 reachable / unreachable
  if timeout 6 bash -c "exec 3<>/dev/tcp/$1/$2" 2>/dev/null; then echo reachable; else echo unreachable; fi
}

echo "=== ① 发夹探针：从 box 打**自己的公网 IP** ${PUB_IP}（$(date '+%F %T')）==="
echo "--- 本项目：公网**不该**可达（只绑 127.0.0.1）---"
for p in 15432 16379 19000 19001 8082 8083; do
  printf '  %s:%-6s → %s\n' "${PUB_IP}" "$p" "$(probe "${PUB_IP}" "$p")"
done
echo "--- 阳性对照：这台机器上**确实公网可达**的既有端口（证明探针在区分，不是全都不可达）---"
for p in 80 443 22; do
  printf '  %s:%-6s → %s\n' "${PUB_IP}" "$p" "$(probe "${PUB_IP}" "$p")"
done
echo "--- 阴性对照：绝不存在的端口（证明探针不会假报可达）---"
printf '  %s:%-6s → %s\n' "1.1.1.1" "59999" "$(probe 1.1.1.1 59999)"
printf '  %s:%-6s → %s\n' "${PUB_IP}" "34567" "$(probe "${PUB_IP}" 34567)"

echo
echo "=== ② 同机环回对照：同样的端口走 127.0.0.1 **应该**可达（服务在跑，只是没对公网发布）==="
for p in 15432 16379 19000 8082 8083; do
  printf '  127.0.0.1:%-6s → %s\n' "$p" "$(probe 127.0.0.1 "$p")"
done

echo
echo "=== ③ ss -lntp：bind 地址（本项目的行一律 127.0.0.1，不是 0.0.0.0）==="
ss -lntp | awk 'NR==1{print "  "$0} /LISTEN/{print "  "$0}' | grep -E "Local|:(15432|16379|19000|19001|8082|8083|3306|8080|5432|6379|9000)\b" || true

echo
echo "=== ④ docker 端口映射（compose 的 HostIp 字段）==="
docker ps --filter 'name=lqg-test-' --format '  {{.Names}}\t{{.Ports}}'
echo "--- 别人的容器（只读，未碰）---"
docker ps --format '  {{.Names}}\t{{.Ports}}' | grep -v 'lqg-test-' || true
