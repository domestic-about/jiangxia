<script setup lang="ts">
import { computed, ref } from 'vue'
import type { QcDocType, QcImage } from '@/api/qc'
import { addQcImage, removeQcImage, uploadToOss } from '@/api/qc'
import { pickErrorText, pickImages } from '@/utils/pickFiles'

// 编辑质控文档页的一个「图片位」（收样原始 / 样本观察 / 样本预处理 / 类器官观察）。
//
// 与工作台 ImageSlotUploader 同一套后端：先传对象存储拿 ossId → POST …/image 绑定到这个位；
// 删除 = DELETE …/image/{id}（只解绑，不删对象存储里的文件）。
// ★ 图片是**立即生效**的（不跟「保存」走）：传完 / 删完就通知页面重取，页面上看到的就是库里的。
// ★ 缩略图用 previewUrl（TIFF 等格式后端另存的 JPEG），点开看原图 url；两者都是 10 分钟签名链接。
const props = withDefaults(defineProps<{
  sampleId: string | number
  docType: QcDocType
  /** 图片位（orig / observe / pretreat / organoid_observe）。★ 不叫 slot：小程序 WXML 里组件上的 slot="…" 是具名插槽投放 */
  slotKey: string
  images: QcImage[]
  disabled?: boolean
}>(), {
  disabled: false,
})

const emit = defineEmits<{
  (e: 'changed'): void
}>()

const busy = ref(false)
const progress = ref('')

const thumbs = computed(() => props.images.filter(img => !!img.url))

function preview(index: number) {
  uni.previewImage({ urls: thumbs.value.map(img => img.url), current: index })
}

async function add() {
  if (props.disabled || busy.value) {
    return
  }
  let files
  try {
    files = await pickImages(9)
  }
  catch (err) {
    uni.showToast({ title: pickErrorText(err), icon: 'none', duration: 3500 })
    return
  }
  if (!files || files.length === 0) {
    return
  }
  busy.value = true
  let ok = 0
  try {
    for (let i = 0; i < files.length; i++) {
      progress.value = `上传中 ${i + 1} / ${files.length}`
      const uploaded = await uploadToOss(files[i].path, files[i].name)
      await addQcImage(props.sampleId, props.docType, props.slotKey, uploaded.ossId)
      ok++
    }
    uni.showToast({ title: `已添加 ${ok} 张`, icon: 'none' })
  }
  catch (err) {
    uni.showToast({ title: (err as Error)?.message || '上传失败', icon: 'none', duration: 3500 })
  }
  finally {
    busy.value = false
    progress.value = ''
    if (ok > 0) {
      emit('changed')
    }
  }
}

function remove(img: QcImage) {
  if (props.disabled || busy.value) {
    return
  }
  uni.showModal({
    title: '删除这张图？',
    content: '删除后这份文档里就不再有它（需重新完成并同步，送检方才看得到变化）。',
    confirmText: '删除',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      busy.value = true
      try {
        await removeQcImage(props.sampleId, props.docType, img.id)
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
  <view class="qis">
    <view class="qis__grid">
      <view v-for="(img, index) in thumbs" :key="String(img.id)" class="qis__cell">
        <image class="qis__img" :src="img.previewUrl || img.url" mode="aspectFill" @click="preview(index)" />
        <text v-if="!disabled" class="qis__del" @click.stop="remove(img)">×</text>
      </view>
      <view v-if="!disabled" class="qis__cell qis__add" :class="{ 'qis__add--busy': busy }" @click="add">
        <text class="qis__plus">{{ busy ? '…' : '+' }}</text>
        <text class="qis__add-t">{{ busy ? progress : '添加图片' }}</text>
      </view>
    </view>
    <text v-if="disabled && thumbs.length === 0" class="qis__empty">还没有图片</text>
  </view>
</template>

<style lang="scss" scoped>
.qis__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--lqg-sp-3);
}

.qis__cell {
  position: relative;
  aspect-ratio: 1 / 1;
  border-radius: var(--lqg-radius-ctl);
  overflow: hidden;
  background: var(--lqg-bg);
}

.qis__img {
  width: 100%;
  height: 100%;
  display: block;
}

.qis__del {
  position: absolute;
  top: 0;
  right: 0;
  width: 26px;
  height: 26px;
  line-height: 24px;
  text-align: center;
  font-size: 18px;
  color: var(--lqg-on-primary);
  background: var(--lqg-ink-2);
  opacity: 0.85;
  border-bottom-left-radius: var(--lqg-radius-ctl);
}

.qis__add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--lqg-sp-1);
  border: 1px dashed var(--lqg-line);
  box-sizing: border-box;
}

.qis__add--busy {
  opacity: 0.7;
}

.qis__plus {
  font-size: 26px;
  line-height: 1;
  color: var(--lqg-primary);
}

.qis__add-t {
  font-size: var(--lqg-fs-xs);
  color: var(--lqg-ink-3);
}

.qis__empty {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}
</style>
