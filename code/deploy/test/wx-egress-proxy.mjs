#!/usr/bin/env node
// 小程序上传的「出口代理」：本机（或 CI 的 macOS runner）编译上传，网络请求从测试机的固定 IP 出去。
//
// 为什么要它（2026-09-30）：原来的做法是把产物同步到测试机、在测试机上跑 miniprogram-ci。
//   miniprogram-ci 编译时一个子进程要吃约 2.5–3 GB 内存；测试机 7.4 GB 是和别的项目合用的
//   （gz-ruoyi-admin-staging、tianda-mysql、next-server……），可用常年只有 3 GB 出头 →
//   编译子进程被内核 OOM 杀掉（dmesg: Out of memory: Killed process … MainThread），
//   而 miniprogram-ci 的 promise 不会因此失败，只会永远挂着（Node 退出码 13 / 一直不结束）。
//   编译挪到内存充裕的机器上，只把「发给微信的请求」经测试机转出去 —— 微信看到的仍是白名单里的固定 IP，
//   测试机上不装任何东西、不改任何配置。
//
// 原理：一个只监听 127.0.0.1 的 HTTP 代理，只认 CONNECT（miniprogram-ci 走 https，经 HTTPS_PROXY 发 CONNECT）；
//   每个 CONNECT 起一条 ssh，在测试机上跑一行 node 中继连到目标，把本地连接和它对接起来
//   （TLS 仍是端到端的，测试机只转字节）。
//   ssh 用 ControlMaster 复用同一条连接，每个请求不必再握手。
//
// 用法（deploy.sh 的 miniapp 阶段会自己起停它）：
//   node wx-egress-proxy.mjs --ssh root@<测试机> --port 18899
//   HTTPS_PROXY=http://127.0.0.1:18899 node code/miniapp/scripts/upload-mp.mjs --mode=test --skip-build
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { spawn } from 'node:child_process'

const arg = (name, fallback) => {
  const hit = process.argv.find(a => a.startsWith(`--${name}=`))
  if (hit) return hit.slice(name.length + 3)
  const i = process.argv.indexOf(`--${name}`)
  return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback
}
const SSH_TARGET = arg('ssh', '')
const PORT = Number(arg('port', '18899'))
if (!SSH_TARGET) {
  console.error('[wx-proxy] 缺 --ssh root@<测试机>')
  process.exit(2)
}

const CONTROL = path.join(os.tmpdir(), `lqg-wx-proxy-${process.pid}.sock`)
const SSH_OPTS = ['-o', 'BatchMode=yes', '-o', 'ConnectTimeout=15', '-o', 'ControlMaster=auto', '-o', `ControlPath=${CONTROL}`, '-o', 'ControlPersist=120']

// 测试机上的「中继」：一行 node，把 ssh 的标准输入输出接到目标的 TCP 连接上。
// ★ 不用 `ssh -W`：测试机 sshd 关了端口转发（AllowTcpForwarding no → "Session open refused by peer"），
//   这里不去改别人也在用的服务器配置；测试机上本来就有 node（原来就在那儿跑 miniprogram-ci）。
//   目标主机名、端口在本地已按白名单格式校验过，拼进远端命令是安全的；代码里不含单引号。
const RELAY = 'const s=require("net").connect(+process.argv[2],process.argv[1]);'
  + 'process.stdin.pipe(s);s.pipe(process.stdout);'
  // ★ 不在 close 里 process.exit：往管道写 stdout 是异步的，硬退出会把最后一段响应吞掉
  + 's.on("error",e=>{console.error(e.message);process.exitCode=1;process.stdin.destroy()});s.on("close",()=>process.stdin.destroy())'

const server = http.createServer((req, res) => {
  // 只做 CONNECT 隧道；明文 http 一律拒绝（miniprogram-ci 只用 https）
  res.writeHead(405, { 'content-type': 'text/plain' })
  res.end('only CONNECT is supported\n')
})

server.on('connect', (req, client, head) => {
  const [host, port = '443'] = String(req.url || '').split(':')
  if (!host || !/^[\w.-]+$/.test(host) || !/^\d+$/.test(port)) {
    client.end('HTTP/1.1 400 Bad Request\r\n\r\n')
    return
  }
  console.log(`[wx-proxy] CONNECT ${host}:${port} → 经 ${SSH_TARGET} 转出`)
  const ssh = spawn('ssh', [...SSH_OPTS, SSH_TARGET, `node -e '${RELAY}' ${host} ${port}`], { stdio: ['pipe', 'pipe', 'pipe'] })
  let established = false
  ssh.stderr.on('data', d => process.stderr.write(`[wx-proxy] ssh: ${d}`))
  ssh.on('spawn', () => {
    established = true
    client.write('HTTP/1.1 200 Connection Established\r\n\r\n')
    if (head && head.length) ssh.stdin.write(head)
    client.pipe(ssh.stdin)
    ssh.stdout.pipe(client)
  })
  ssh.on('error', (e) => {
    console.error(`[wx-proxy] ssh 起不来：${e.message}`)
    if (!established) client.end('HTTP/1.1 502 Bad Gateway\r\n\r\n')
    else client.destroy()
  })
  // 任何一头断了都只收拾这一条连接：管道上的 EPIPE / ECONNRESET 不许冒成未处理的 error 把整个代理带崩
  //（带崩之后 miniprogram-ci 后面的请求全是 ECONNREFUSED）
  ssh.stdin.on('error', () => client.destroy())
  ssh.stdout.on('error', () => client.destroy())
  ssh.on('close', (code) => {
    if (code) console.error(`[wx-proxy] ${host}:${port} 的中继退出码 ${code}`)
    client.destroy()
  })
  client.on('error', () => ssh.kill())
  client.on('close', () => ssh.kill())
})

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[wx-proxy] 监听 127.0.0.1:${PORT}，出口 = ${SSH_TARGET}`)
})

process.on('uncaughtException', (e) => {
  console.error(`[wx-proxy] 忽略一条连接上的异常：${e?.message || e}`)
})

const shutdown = () => {
  server.close()
  spawn('ssh', [...SSH_OPTS, '-O', 'exit', SSH_TARGET], { stdio: 'ignore' }).on('close', () => process.exit(0))
  setTimeout(() => process.exit(0), 3000).unref()
}
process.on('SIGTERM', shutdown)
process.on('SIGINT', shutdown)
