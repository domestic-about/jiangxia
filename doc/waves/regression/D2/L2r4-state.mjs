/** D2 r4 L2 分片内部状态（脚本之间传 NEW_ID；reseed 后自动失效）。 */
import { readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const F = path.join(HERE, '.l2r4-state.json')

export function saveState(obj) {
  writeFileSync(F, JSON.stringify(obj, null, 2))
}
export function loadState() {
  try { return JSON.parse(readFileSync(F, 'utf8')) }
  catch { return {} }
}
export const STATE_FILE = F
