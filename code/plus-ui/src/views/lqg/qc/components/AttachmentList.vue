<template>
  <div class="lqg-attachment-list">
    <div class="lqg-attachment-list__head">
      <span class="lqg-attachment-list__title">{{ t('lqg.qc.attachment.title') }}</span>
      <el-upload
        v-if="!disabled"
        :action="OSS_UPLOAD_URL"
        :headers="headers"
        :show-file-list="false"
        :before-upload="handleBeforeUpload"
        :on-success="handleUploadSuccess"
        :on-error="handleUploadError"
        class="lqg-attachment-list__upload"
      >
        <el-button size="small" plain icon="Upload">{{ t('lqg.qc.attachment.add') }}</el-button>
      </el-upload>
    </div>

    <ul v-if="attachments.length" class="lqg-attachment-list__items">
      <li v-for="file in attachments" :key="String(file.id)" class="lqg-attachment-list__item">
        <el-link
          :href="file.url"
          target="_blank"
          :underline="false"
          type="primary"
          class="lqg-attachment-list__name"
          @click.prevent="openFile(file)"
        >
          <el-icon><Document /></el-icon>
          <span>{{ file.fileName }}</span>
        </el-link>
        <!-- ★ fileSize 可能为 0 / 缺失（issue #209）：不显示成「0 B」，也不当空文件拒绝 -->
        <span class="lqg-attachment-list__size">{{ sizeText(file.fileSize) }}</span>
        <el-button
          v-if="!disabled"
          link
          type="danger"
          icon="Delete"
          @click="handleRemove(file)"
        />
      </li>
    </ul>
    <div v-else class="lqg-attachment-list__empty">{{ t('lqg.qc.attachment.empty') }}</div>

    <div class="lqg-attachment-list__tip">{{ t('lqg.qc.attachment.tip') }}</div>
  </div>
</template>

<script setup name="LqgAttachmentList" lang="ts">
import { globalHeaders } from '@/utils/request';
import {
  OSS_UPLOAD_URL,
  addDocAttachment,
  removeDocAttachment,
  type DocAttachmentVO,
  type QcDocType
} from '@/api/lqg/qc';
import { useI18n } from 'vue-i18n';
import { uploadBizErrorMessage, uploadErrorMessage } from './uploadFeedback';
import { QC_FRESH_URLS } from './freshUrls';

// ============================================================================
// 通用附件区（QC-WEB-001 / UI:admin.qc.editor 的右/底部「附件」块）
//
// 与「细胞活率测定附件」的区别：那一个是表上单独一栏（`viability_oss_id`，只能一个），
// 这里是可多挂的通用附件（`t_lqg_doc_attachment`）；两者互不影响。
//
// ★ fileSize 是**可选**的（issue #209）：上传接口的响应里没有字节数，用 el-upload 给的
//   原始 File.size 顺手带上；拿不到就不带（后端只在给了的时候校验 ≤50MB）。
// ★ 删除只解绑（DELETE /lqg/qc/**），不删 OSS 对象（101/102 没有 system:oss:remove）。
// ★ 附件地址是 10 分钟签名链接（私有桶）：点开之前先让编辑页保鲜（老了重取一次），
//   再用这一条附件的新地址打开 —— 页面开久了点附件，不会打开一个 403 的页面（freshUrls.ts）。
// ============================================================================

const props = defineProps<{
  sampleId: string | number;
  docType: QcDocType;
  attachments: DocAttachmentVO[];
  disabled?: boolean;
}>();

const emit = defineEmits<{ (e: 'changed'): void }>();

const { t } = useI18n();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const headers = ref(globalHeaders());

const MAX_SIZE_MB = 50;

/** 编辑页提供的「用之前先保鲜」（单独用本组件、没有编辑页时是空操作） */
const ensureFreshUrls = inject(QC_FRESH_URLS, async () => {});

/** 打开附件：先保鲜，再按 id 取这一条的最新地址（保鲜后 props 里已是新的一批） */
const openFile = async (file: DocAttachmentVO) => {
  await ensureFreshUrls();
  const latest = props.attachments.find((a) => String(a.id) === String(file.id)) ?? file;
  if (latest.url) {
    window.open(latest.url, '_blank');
  }
};

/** 字节数 → 人话；0 / 缺失一律显示占位（不当成空文件） */
const sizeText = (size?: number | null) => {
  if (!size || size <= 0) {
    return t('lqg.qc.attachment.sizeUnknown');
  }
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
};

const handleBeforeUpload = (file: File) => {
  if (file.size / 1024 / 1024 > MAX_SIZE_MB) {
    proxy?.$modal.msgError(`「${file.name}」` + t('lqg.qc.attachment.tooLarge', { max: MAX_SIZE_MB }));
    return false;
  }
  proxy?.$modal.loading(t('lqg.qc.uploading'));
  return true;
};

/** 上传请求本身失败（超限的空 400 / 413 / 登录过期 / 网络断了 …）：说清原因，不只说「上传失败」 */
const handleUploadError = (error: unknown, file: any) => {
  proxy?.$modal.closeLoading();
  proxy?.$modal.msgError(uploadErrorMessage(t, error, file?.name, MAX_SIZE_MB));
};

const handleUploadSuccess = async (res: any, file: any) => {
  try {
    if (res?.code !== 200) {
      proxy?.$modal.msgError(uploadBizErrorMessage(t, res, file?.name));
      return;
    }
    const payload: { ossId: string | number; fileName: string; fileSize?: number } = {
      ossId: res.data.ossId,
      fileName: res.data.fileName
    };
    if (file?.size) {
      payload.fileSize = file.size;
    }
    await addDocAttachment(props.sampleId, props.docType, payload);
    proxy?.$modal.msgSuccess(t('lqg.qc.attachment.added'));
    emit('changed');
  } catch {
    // 挂附件失败：请求层已经把后端的原因弹出来了（例如「单个附件不能超过 50MB」），这里不再重复弹
  } finally {
    proxy?.$modal.closeLoading();
  }
};

const handleRemove = async (file: DocAttachmentVO) => {
  await proxy?.$modal.confirm(t('lqg.qc.attachment.removeConfirm', { name: file.fileName }));
  await removeDocAttachment(props.sampleId, props.docType, file.id);
  proxy?.$modal.msgSuccess(t('lqg.qc.attachment.removed'));
  emit('changed');
};
</script>

<style scoped lang="scss">
.lqg-attachment-list {
  .lqg-attachment-list__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .lqg-attachment-list__title {
    font-size: 13px;
    font-weight: 600;
    color: var(--lqg-ink);
  }
  .lqg-attachment-list__items {
    margin: 6px 0 0;
    padding: 0;
    list-style: none;
  }
  .lqg-attachment-list__item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 0;
    border-bottom: 1px solid var(--lqg-line);
  }
  .lqg-attachment-list__name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .lqg-attachment-list__size {
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
  .lqg-attachment-list__empty {
    margin-top: 6px;
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
  .lqg-attachment-list__tip {
    margin-top: 4px;
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
}
</style>
