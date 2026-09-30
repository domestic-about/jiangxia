<script setup lang="ts">
import { ref } from 'vue'
import type { QcAttachment, QcDocType } from '@/api/qc'
import { addQcAttachment, removeQcAttachment, uploadToOss } from '@/api/qc'
import { fileSizeText } from '@/pages/doc/download'
import { openRemoteFile } from '@/utils/openRemoteFile'
import { pickErrorText, pickFile } from '@/utils/pickFiles'

// 编辑质控文档页的「附件」（通用附件，可多个；与样本质控表上单独那一栏「细胞活率测定」是两回事）。
// 与工作台 AttachmentList 同一套后端：传对象存储 → POST …/attachment 绑定；删除只解绑。
// ★ 立即生效，不跟「保存」走。
// ★ 小程序里文件只能从微信聊天记录里选（见 utils/pickFiles.ts），所以按钮下面写一句怎么做。
const props = withDefaults(defineProps<{
  sampleId: string | number
  docType: QcDocType
  attachments: QcAttachment[]
  disabled?: boolean
}>(), {
  disabled: false,
})

const emit = defineEmits<{
  (e: 'changed'): void
}>()

const busy = ref(false)

async function add() {
  if (props.disabled || busy.value) {
    return
  }
  let file
  try {
    file = await pickFile()
  }
  catch (err) {
    uni.showToast({ title: pickErrorText(err), icon: 'none', duration: 3500 })
    return
  }
  if (!file) {
    return
  }
  busy.value = true
  uni.showLoading({ title: '上传中', mask: true })
  try {
    const uploaded = await uploadToOss(file.path, file.name)
    await addQcAttachment(props.sampleId, props.docType, {
      ossId: uploaded.ossId,
      fileName: file.name || uploaded.fileName,
      ...(file.size ? { fileSize: file.size } : {}),
    })
    uni.hideLoading()
    uni.showToast({ title: '已添加附件', icon: 'none' })
    emit('changed')
  }
  catch (err) {
    uni.hideLoading()
    uni.showToast({ title: (err as Error)?.message || '上传失败', icon: 'none', duration: 3500 })
  }
  finally {
    busy.value = false
  }
}

function remove(item: QcAttachment) {
  if (props.disabled || busy.value) {
    return
  }
  uni.showModal({
    title: '删除这个附件？',
    content: item.fileName,
    confirmText: '删除',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      busy.value = true
      try {
        await removeQcAttachment(props.sampleId, props.docType, item.id)
        emit('changed')
      }
      finally {
        busy.value = false
      }
    },
  })
}
</script>

<template>
  <view class="qat">
    <view v-for="item in attachments" :key="String(item.id)" class="qat__row">
      <view class="qat__main" @click="openRemoteFile(item.url, item.fileName)">
        <text class="qat__name">{{ item.fileName }}</text>
        <text v-if="item.fileSize" class="qat__size">{{ fileSizeText(item.fileSize) }}</text>
      </view>
      <text v-if="!disabled" class="qat__del" @click="remove(item)">删除</text>
    </view>
    <text v-if="attachments.length === 0 && disabled" class="qat__empty">没有附件</text>
    <view v-if="!disabled" class="qat__add" @click="add">
      <text class="qat__add-t">{{ busy ? '上传中…' : '+ 添加附件' }}</text>
    </view>
    <text v-if="!disabled" class="qat__hint">小程序只能从微信聊天记录里选文件：先把文件发到「文件传输助手」再来选</text>
  </view>
</template>

<style lang="scss" scoped>
.qat {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-3);
}

.qat__row {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-3);
  padding: var(--lqg-sp-3) 0;
  border-bottom-width: 1px;
  border-bottom-style: solid;
  border-bottom-color: #dfe6e7;
}

.qat__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.qat__name {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-primary);
  word-break: break-all;
}

.qat__size {
  font-size: var(--lqg-fs-xs);
  color: var(--lqg-ink-3);
}

.qat__del {
  flex: none;
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-danger);
}

.qat__empty {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.qat__add {
  align-self: flex-start;
  padding: var(--lqg-sp-2) var(--lqg-sp-5);
  border-radius: var(--lqg-radius-ctl);
  background: var(--lqg-primary-soft);
}

.qat__add-t {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-primary);
}

.qat__hint {
  font-size: var(--lqg-fs-xs);
  color: var(--lqg-ink-3);
}
</style>
