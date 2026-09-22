#!/usr/bin/env bash
# 票面 accept（逐字重放）：OCR-MP-001 accept[2] form=API
# 构建是本次产物；识别条只在新增时出现；小程序包里没有任何第三方识别服务的域名或密钥
set -euo pipefail
cd code/miniapp && rm -rf dist/build/mp-weixin && pnpm build:mp-weixin >/dev/null &&
grep -q "@/components/lqg/OcrBar.vue" src/pages/sample/form.vue && grep -q 'showOcr' src/pages/sample/form.vue &&
grep -q 'chooseMedia' src/components/lqg/OcrBar.vue && grep -q '/mp/ocr/recognize' src/api/ocr.ts &&
! grep -rnEi 'aliyuncs\.com|dashscope|api\.weixin\.qq\.com/cv|baidubce|tencentcloudapi|secret[_-]?key|api[_-]?key' src dist/build/mp-weixin
