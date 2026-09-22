#!/usr/bin/env bash
# 票面 accept（逐字重放）：QC-WEB-001 accept[2] form=API
# 前端构建是本次产物；编辑页用了图片位上传与附件组件；带出的只读字段没有进表单；样本总表的「质控文档」入口已点亮
set -euo pipefail
cd code/plus-ui && rm -rf dist && pnpm build:prod >/dev/null && test -f dist/index.html &&
grep -c 'ImageSlotUploader' src/views/lqg/qc/editor/SampleQcTab.vue | awk '{exit !($1 >= 3)}' &&
grep -q 'AttachmentList' src/views/lqg/qc/editor/SampleQcTab.vue && grep -q 'preview-src-list\|previewSrcList' src/views/lqg/qc/components/ImageSlotUploader.vue &&
! grep -nE 'v-model="form\.(internalNo|donorName|sourceUnitName|receiveDate|processTime|operatorName|gender)"' src/views/lqg/qc/editor/*.vue &&
grep -q 'qc-editor' src/views/lqg/sample/index.vue
