/**
 * 静态服务 plus-ui 构建产物（走「build 产物」这条 L2 验证路径，不依赖 vite dev server）。
 *
 * 为什么需要它：`vite build` 的产物是 history 路由，必须有 SPA fallback；而且它在浏览器里
 * 直接请求 `/dev-api/**`（`.env.development` 的 VITE_APP_BASE_API），所以还要一个转发到
 * 后端 8081 的代理。
 *
 * ★ 用 build:dev 产物而不是 build:prod：prod 构建里 VITE_APP_ENCRYPT=true，客户端会把请求体
 *   AES 加密，而后端按 ADR / run-backend.sh 带 `--api-decrypt.enabled=false` → 登录必然失败。
 *   那是**环境不匹配**（不是产品缺陷），所以产物这一路取 build:dev（与 dev 同一份 env、
 *   但已经过 rollup 打包，不再是 vite dev 的内存模块图）。
 *
 * 跑法：node doc/waves/regression/D1/L2-r2-serve-static.mjs [port] [distDir] [apiTarget]
 *       默认 8083 / code/plus-ui/dist / http://127.0.0.1:8081
 */
import { createServer } from 'node:http'
import { createReadStream, existsSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const WS = path.resolve(HERE, '../../../..')
const PORT = Number(process.argv[2] || 8083)
const DIST = path.resolve(process.argv[3] || path.join(WS, 'code/plus-ui/dist'))
const API = process.argv[4] || 'http://127.0.0.1:8081'
const API_PREFIX = '/dev-api'

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json; charset=utf-8',
}

const server = createServer((req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${PORT}`)

  // ── API 代理：/dev-api/** → 后端（去掉前缀）─────────────────────────────
  if (url.pathname.startsWith(API_PREFIX)) {
    const target = new URL(API)
    const chunks = []
    req.on('data', c => chunks.push(c))
    req.on('end', () => {
      const body = chunks.length ? Buffer.concat(chunks) : undefined
      const headers = { ...req.headers, host: target.host }
      if (body) headers['content-length'] = String(body.length)
      const up = fetch(target.origin + url.pathname.slice(API_PREFIX.length) + url.search, {
        method: req.method,
        headers,
        body,
        redirect: 'manual',
      })
      up.then(async (r) => {
        const buf = Buffer.from(await r.arrayBuffer())
        const out = {}
        r.headers.forEach((v, k) => { if (!['content-encoding', 'content-length', 'transfer-encoding', 'connection'].includes(k)) out[k] = v })
        res.writeHead(r.status, out)
        res.end(buf)
      }).catch((e) => {
        res.writeHead(502, { 'content-type': 'text/plain; charset=utf-8' })
        res.end(`proxy error: ${e.message}`)
      })
    })
    return
  }

  // ── 静态文件 + SPA fallback ─────────────────────────────────────────────
  let file = path.join(DIST, decodeURIComponent(url.pathname))
  if (!file.startsWith(DIST)) { res.writeHead(403); res.end('forbidden'); return }
  if (!existsSync(file) || statSync(file).isDirectory()) {
    const idx = path.join(file, 'index.html')
    file = existsSync(idx) ? idx : path.join(DIST, 'index.html')
  }
  res.writeHead(200, {
    'content-type': MIME[path.extname(file).toLowerCase()] || 'application/octet-stream',
    'cache-control': 'no-store',
  })
  createReadStream(file).pipe(res)
})

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[static] ${DIST} → http://127.0.0.1:${PORT}  (proxy ${API_PREFIX} → ${API})`)
})
