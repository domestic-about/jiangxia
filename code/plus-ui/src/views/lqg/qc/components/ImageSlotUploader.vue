<template>
  <div class="lqg-image-slot">
    <div class="lqg-image-slot__grid">
      <!-- 已有图：点图放大（el-image 的 preview-src-list）、可拖拽排序、可删 -->
      <div
        v-for="(img, idx) in images"
        :key="String(img.id)"
        class="lqg-image-slot__item"
        :class="{ 'is-dragover': dragOverIndex === idx }"
        :draggable="!disabled"
        @dragstart="handleDragStart(idx)"
        @dragover.prevent="dragOverIndex = idx"
        @dragleave="dragOverIndex = null"
        @drop.prevent="handleDrop(idx)"
        @dragend="resetDrag"
      >
        <el-image
          :ref="(el: any) => setImageRef(img.id, el)"
          :src="displayUrl(img)"
          :preview-src-list="[enlargeUrl(img)]"
          :initial-index="0"
          preview-teleported
          hide-on-click-modal
          fit="cover"
          class="lqg-image-slot__pic"
          @click="openPreview(img)"
        >
          <template #error>
            <div class="lqg-image-slot__broken">{{ t('lqg.qc.image.broken') }}</div>
          </template>
        </el-image>
        <span class="lqg-image-slot__order">{{ idx + 1 }}</span>
        <div v-if="!disabled" class="lqg-image-slot__mask">
          <el-button link type="danger" icon="Delete" @click.stop="handleRemove(img)" />
        </div>
      </div>

      <!-- 上传按钮：满 3 张就消失（后端也兜底拒绝第 4 张） -->
      <el-upload
        v-if="!disabled && images.length < MAX_PER_SLOT"
        :action="OSS_UPLOAD_URL"
        :headers="headers"
        :show-file-list="false"
        :accept="ACCEPT"
        :before-upload="handleBeforeUpload"
        :on-success="handleUploadSuccess"
        :on-error="handleUploadError"
        class="lqg-image-slot__add"
      >
        <div class="lqg-image-slot__addbox">
          <el-icon><Plus /></el-icon>
          <span>{{ t('lqg.qc.image.add') }}</span>
        </div>
      </el-upload>
    </div>

    <div class="lqg-image-slot__tip">
      {{ t('lqg.qc.image.tip', { max: MAX_PER_SLOT }) }}
    </div>
  </div>
</template>

<script setup name="LqgImageSlotUploader" lang="ts">
import { globalHeaders } from '@/utils/request';
import {
  OSS_UPLOAD_URL,
  addDocImage,
  removeDocImage,
  sortDocImages,
  type DocImageVO,
  type QcDocType
} from '@/api/lqg/qc';
import { useI18n } from 'vue-i18n';

// ============================================================================
// 一个图片位的上传区（QC-WEB-001 / UI:admin.qc.editor）
//
// 一个样本的样本质控表有三个图片位（orig / observe / pretreat），每位 1-3 张：
//   · 上传：先 POST /resource/oss/upload 拿 ossId，再 POST …/{docType}/image 绑定；
//   · 排序：HTML5 拖拽 → PUT …/image/sort，请求体是 `{ids:[该位**全部** id]}`（issue #210）；
//   · 删除：DELETE …/image/{id}（只解绑；101/102 没有 system:oss:remove，别去删 OSS 对象）；
//   · 点图放大：el-image 的 preview-src-list —— 显示的可能是后端给的 JPEG 预览图
//     （TIFF 等格式），点开放大的默认是原图；原图是 TIFF 时退回预览图（浏览器放不出 TIFF）。
// ============================================================================

const props = defineProps<{
  sampleId: string | number;
  docType: QcDocType;
  /** 图片位：orig / observe / pretreat（样本质控表） */
  slot: string;
  /** 该位现有的图（后端按 sort 升序给） */
  images: DocImageVO[];
  disabled?: boolean;
}>();

const emit = defineEmits<{ (e: 'changed'): void }>();

const { t } = useI18n();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const MAX_PER_SLOT = 3;
const MAX_SIZE_MB = 20;
const ACCEPT = '.jpg,.jpeg,.png,.tif,.tiff,.bmp,.webp,.gif';
const headers = ref(globalHeaders());

const dragFromIndex = ref<number | null>(null);
const dragOverIndex = ref<number | null>(null);

/**
 * ★ 点图放大：el-image 的 click 处理器只挂在**它自己渲染的 `<img>`** 上
 *   （element-plus `image.mjs`：`onClick: clickHandler` 在 img 上）——
 *   图片加载失败时 img 被 `#error` 槽替换，那个 div 上没有处理器，点了就没反应。
 *   本机的确定性测试数据里有 `service='seed'` 的假地址图（本来就取不到字节），
 *   所以这里自己按 id 存一份组件引用，点整块（含失败态）都走 `showPreview()`
 *   —— 保证「点图一定放大」，不依赖图能不能加载出来。
 */
const imageRefs = new Map<string, any>();
const setImageRef = (id: string | number, el: any) => {
  if (el) {
    imageRefs.set(String(id), el);
  }
};
const openPreview = (img: DocImageVO) => {
  imageRefs.get(String(img.id))?.showPreview?.();
};

/** 展示用：优先后端预览图（TIFF 等格式浏览器只认它），没有才退回原图 */
const displayUrl = (img: DocImageVO) => img.previewUrl || img.url;

/** 点图放大用：原图优先；原图是 TIFF 时浏览器放不出来 → 退回后端预览图 */
const enlargeUrl = (img: DocImageVO) => {
  const raw = img.url || img.previewUrl || '';
  const path = raw.split('?')[0].toLowerCase();
  if (/\.tiff?$/.test(path)) {
    return img.previewUrl || raw;
  }
  return raw;
};

const handleBeforeUpload = (file: File) => {
  const ext = '.' + (file.name.split('.').pop() || '').toLowerCase();
  if (!ACCEPT.split(',').includes(ext)) {
    proxy?.$modal.msgError(t('lqg.qc.image.badType'));
    return false;
  }
  if (file.size / 1024 / 1024 > MAX_SIZE_MB) {
    proxy?.$modal.msgError(t('lqg.qc.image.tooLarge', { max: MAX_SIZE_MB }));
    return false;
  }
  proxy?.$modal.loading(t('lqg.qc.uploading'));
  return true;
};

const handleUploadError = () => {
  proxy?.$modal.closeLoading();
  proxy?.$modal.msgError(t('lqg.qc.uploadFailed'));
};

/** 上传到 OSS 成功后：再把它绑到这个图片位上 */
const handleUploadSuccess = async (res: any) => {
  try {
    if (res?.code !== 200) {
      proxy?.$modal.msgError(res?.msg || t('lqg.qc.uploadFailed'));
      return;
    }
    await addDocImage(props.sampleId, props.docType, { slot: props.slot, ossId: res.data.ossId });
    proxy?.$modal.msgSuccess(t('lqg.qc.image.added'));
    emit('changed');
  } finally {
    proxy?.$modal.closeLoading();
  }
};

const handleRemove = async (img: DocImageVO) => {
  await proxy?.$modal.confirm(t('lqg.qc.image.removeConfirm'));
  await removeDocImage(props.sampleId, props.docType, img.id);
  proxy?.$modal.msgSuccess(t('lqg.qc.image.removed'));
  emit('changed');
};

const handleDragStart = (idx: number) => {
  dragFromIndex.value = idx;
};

const resetDrag = () => {
  dragFromIndex.value = null;
  dragOverIndex.value = null;
};

/** 拖拽落位：本地先算出目标顺序，再按「该位全部 id」整组提交（issue #210） */
const handleDrop = async (toIdx: number) => {
  const from = dragFromIndex.value;
  resetDrag();
  if (from === null || from === toIdx) {
    return;
  }
  const ids = props.images.map((i) => i.id);
  const [moved] = ids.splice(from, 1);
  ids.splice(toIdx, 0, moved);
  sortDocImages(props.sampleId, props.docType, ids)
    .then(() => emit('changed'))
    .catch(() => {
      // 失败时请求层已经提示过；父组件不刷新，顺序保持原样
    });
};
</script>

<style scoped lang="scss">
.lqg-image-slot {
  .lqg-image-slot__grid {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .lqg-image-slot__item,
  .lqg-image-slot__addbox {
    width: 96px;
    height: 96px;
    border-radius: 6px;
    overflow: hidden;
  }
  .lqg-image-slot__item {
    position: relative;
    border: 1px solid var(--lqg-line);
    background: var(--lqg-bg);
    cursor: move;
    &.is-dragover {
      border-color: var(--lqg-primary);
      box-shadow: 0 0 0 2px var(--lqg-primary-soft);
    }
  }
  .lqg-image-slot__pic {
    width: 100%;
    height: 100%;
    display: block;
  }
  .lqg-image-slot__broken {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 11px;
    color: var(--lqg-ink-3);
  }
  .lqg-image-slot__order {
    position: absolute;
    top: 2px;
    left: 2px;
    min-width: 16px;
    padding: 0 3px;
    border-radius: 3px;
    background: var(--lqg-ink);
    color: var(--lqg-card);
    font-size: 11px;
    text-align: center;
  }
  .lqg-image-slot__mask {
    position: absolute;
    right: 0;
    bottom: 0;
    padding: 0 2px;
    background: var(--lqg-card);
    border-top-left-radius: 6px;
  }
  .lqg-image-slot__addbox {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    border: 1px dashed var(--lqg-line);
    color: var(--lqg-ink-3);
    font-size: 11px;
    background: var(--lqg-card);
    &:hover {
      border-color: var(--lqg-primary);
      color: var(--lqg-primary);
    }
  }
  .lqg-image-slot__tip {
    margin-top: 4px;
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
}
</style>
