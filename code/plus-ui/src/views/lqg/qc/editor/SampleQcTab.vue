<template>
  <div class="lqg-sample-qc">
    <el-form :model="form" label-position="top" class="lqg-sample-qc__form">
      <!-- ① 短字段：患者编号 / 取样部位 / 取样方式 -->
      <div class="lqg-sample-qc__two">
        <el-form-item :label="t('lqg.qc.tab.patientNo')">
          <el-input v-model="form.patientNo" :placeholder="t('lqg.qc.tab.patientNoPlaceholder')" clearable />
        </el-form-item>
        <el-form-item :label="t('lqg.qc.tab.samplingSite')">
          <el-input v-model="form.samplingSite" clearable />
        </el-form-item>
        <el-form-item :label="t('lqg.qc.tab.samplingMethod')">
          <el-input v-model="form.samplingMethod" clearable />
        </el-form-item>
        <!-- ② 细胞活率测定：单文件，显示文件名，可替换 / 移除（表上单独一栏，不是通用附件） -->
        <el-form-item :label="t('lqg.qc.tab.viability')">
          <div class="lqg-sample-qc__viability">
            <el-upload
              ref="viabilityUploadRef"
              :action="OSS_UPLOAD_URL"
              :headers="headers"
              :show-file-list="false"
              :disabled="readonly"
              :before-upload="handleViabilityBeforeUpload"
              :on-success="handleViabilitySuccess"
              :on-error="handleViabilityError"
            >
              <!-- 有附件时：文件名本身就是「替换」入口；没有时是「上传」按钮 -->
              <template v-if="form.viabilityOssId">
                <span class="lqg-sample-qc__viability-name" :title="t('lqg.qc.tab.viabilityReplaceHint')">
                  <el-icon><Document /></el-icon>
                  {{ form.viabilityFileName || t('lqg.qc.tab.viabilityUnnamed') }}
                </span>
              </template>
              <el-button v-else size="small" plain icon="Upload" :disabled="readonly">
                {{ t('lqg.qc.tab.viabilityAdd') }}
              </el-button>
            </el-upload>
            <el-button v-if="form.viabilityOssId" link type="danger" :disabled="readonly" @click="handleViabilityRemove">
              {{ t('lqg.qc.tab.viabilityRemove') }}
            </el-button>
          </div>
        </el-form-item>
      </div>

      <!-- 临床诊断 / 既往治疗 与 收样描述 并排（飞书 2026-09-30 工作台行18①：左栏信息密度集中些） -->
      <div class="lqg-sample-qc__two">
        <el-form-item :label="t('lqg.qc.tab.clinicalDiagnosis')">
          <el-input v-model="form.clinicalDiagnosis" type="textarea" :rows="3" :disabled="readonly" />
        </el-form-item>

        <el-form-item :label="t('lqg.qc.tab.receiveDesc')">
          <el-input v-model="form.receiveDesc" type="textarea" :rows="3" :disabled="readonly" />
        </el-form-item>
      </div>

      <!-- ③ 三个图片位 + 情况描述（按模板顺序：收样原始 / 样本观察 / 样本预处理）。
           图片在左、情况描述在右同一排（飞书 2026-09-30 工作台行18②）；左栏窄了自动折成上下两行。
           ★ 三个位显式各写一遍（不抽 v-for）：位名与文案一一对应，改模板时不会串位。 -->
      <section class="lqg-sample-qc__slot">
        <div class="lqg-sample-qc__slot-title">{{ t('lqg.qc.tab.origTitle') }}</div>
        <div class="lqg-sample-qc__slot-body">
          <ImageSlotUploader
            :sample-id="sampleId"
            :doc-type="docType"
            slot="orig"
            :images="slotImages('orig')"
            :disabled="readonly"
            @changed="emit('changed')"
          />
          <el-form-item :label="t('lqg.qc.tab.descLabel')" class="lqg-sample-qc__desc">
            <el-input v-model="form.origDesc" type="textarea" :rows="4" :disabled="readonly" />
          </el-form-item>
        </div>
      </section>

      <section class="lqg-sample-qc__slot">
        <div class="lqg-sample-qc__slot-title">{{ t('lqg.qc.tab.observeTitle') }}</div>
        <div class="lqg-sample-qc__slot-body">
          <ImageSlotUploader
            :sample-id="sampleId"
            :doc-type="docType"
            slot="observe"
            :images="slotImages('observe')"
            :disabled="readonly"
            @changed="emit('changed')"
          />
          <el-form-item :label="t('lqg.qc.tab.descLabel')" class="lqg-sample-qc__desc">
            <el-input v-model="form.observeDesc" type="textarea" :rows="4" :disabled="readonly" />
          </el-form-item>
        </div>
      </section>

      <section class="lqg-sample-qc__slot">
        <div class="lqg-sample-qc__slot-title">{{ t('lqg.qc.tab.pretreatTitle') }}</div>
        <div class="lqg-sample-qc__slot-body">
          <ImageSlotUploader
            :sample-id="sampleId"
            :doc-type="docType"
            slot="pretreat"
            :images="slotImages('pretreat')"
            :disabled="readonly"
            @changed="emit('changed')"
          />
          <el-form-item :label="t('lqg.qc.tab.descLabel')" class="lqg-sample-qc__desc">
            <el-input v-model="form.pretreatDesc" type="textarea" :rows="4" :disabled="readonly" />
          </el-form-item>
        </div>
      </section>

      <!-- ④ 通用附件（可多个；与上面的细胞活率测定附件是两回事） -->
      <section class="lqg-sample-qc__slot">
        <AttachmentList
          :sample-id="sampleId"
          :doc-type="docType"
          :attachments="doc.attachments || []"
          :disabled="readonly"
          @changed="emit('changed')"
        />
      </section>
    </el-form>
  </div>
</template>

<script setup name="LqgSampleQcTab" lang="ts">
import { globalHeaders } from '@/utils/request';
import { OSS_UPLOAD_URL, saveSampleQc, type QcSampleDocVO, type QcSampleSaveBO } from '@/api/lqg/qc';
import ImageSlotUploader from '../components/ImageSlotUploader.vue';
import AttachmentList from '../components/AttachmentList.vue';
import { uploadBizErrorMessage, uploadErrorMessage } from '../components/uploadFeedback';
import { useI18n } from 'vue-i18n';

// ============================================================================
// 「样本质控表」页签（QC-WEB-001 / FLOW:F-QC-01.step2）
//
// 按模板顺序：患者编号 → 取样部位 → 取样方式 → 临床诊断/既往治疗 → 收样描述
//            → 细胞活率测定（单文件）→ 三个图片位（各带情况描述）→ 通用附件。
//
// ★ 样本主档带出的七项（来源单位 / 患者姓名 / 性别 / 收样时间 / 处理时间 / 操作人 /
//   内部编号）**只读**展示在页头摘要条（editor/index.vue），**不进本表单**
//   —— 它们在 `t_lqg_sample` 上，这里再存一份就是两个真相源（accept 2 第 4 段断这个）。
// ★ 图片与附件是「操作即落库」（各自有独立的 POST/DELETE 端点），
//   只有文本字段（含细胞活率附件这一栏）走「保存草稿」的 PUT。
// ★ 保存是补丁语义：整份表单一起发；细胞活率附件被摘掉时发 `viabilityOssId: 0`（issue #212）。
// ============================================================================

const props = defineProps<{
  sampleId: string | number;
  /** 样本质控表 VO（GET /lqg/qc/{sampleId} 的 data.sampleQc） */
  doc: QcSampleDocVO;
  readonly?: boolean;
}>();

const emit = defineEmits<{
  (e: 'changed'): void;
  (e: 'dirty', value: boolean): void;
}>();

const { t } = useI18n();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const docType = 'sample-qc' as const;
const headers = ref(globalHeaders());
const viabilityUploadRef = ref<any>();

const form = reactive({
  patientNo: '',
  samplingSite: '',
  samplingMethod: '',
  clinicalDiagnosis: '',
  receiveDesc: '',
  /** null = 没有；0 = 保存时摘掉（issue #212）；其它 = 该 oss 的 id */
  viabilityOssId: null as string | number | null,
  viabilityFileName: null as string | null,
  origDesc: '',
  observeDesc: '',
  pretreatDesc: ''
});

const dirty = ref(false);
let syncing = false;

/** 图片按位取（`images` 是 map，没图的位是 `[]`——上游形状，别当数组读） */
const slotImages = (slot: string) => props.doc?.images?.[slot] ?? [];

/** 从后端 VO 灌进表单；用户有未保存改动时不覆盖（否则上传一张图就把半篇文字冲掉） */
const syncFromDoc = (force = false) => {
  if (!props.doc || (dirty.value && !force)) {
    return;
  }
  syncing = true;
  form.patientNo = props.doc.patientNo ?? '';
  form.samplingSite = props.doc.samplingSite ?? '';
  form.samplingMethod = props.doc.samplingMethod ?? '';
  form.clinicalDiagnosis = props.doc.clinicalDiagnosis ?? '';
  form.receiveDesc = props.doc.receiveDesc ?? '';
  form.viabilityOssId = props.doc.viabilityOssId ?? null;
  form.viabilityFileName = props.doc.viabilityFileName ?? null;
  form.origDesc = props.doc.origDesc ?? '';
  form.observeDesc = props.doc.observeDesc ?? '';
  form.pretreatDesc = props.doc.pretreatDesc ?? '';
  syncing = false;
  setDirty(false);
};

const setDirty = (value: boolean) => {
  dirty.value = value;
  emit('dirty', value);
};

watch(() => props.doc, () => syncFromDoc(), { immediate: true, deep: true });
watch(
  form,
  () => {
    if (!syncing) {
      setDirty(true);
    }
  },
  { deep: true }
);

/** 保存草稿：整份表单发上去（补丁语义，见 api/lqg/qc/index.ts 文件头） */
const save = async () => {
  const payload: QcSampleSaveBO = {
    patientNo: form.patientNo,
    samplingSite: form.samplingSite,
    samplingMethod: form.samplingMethod,
    clinicalDiagnosis: form.clinicalDiagnosis,
    receiveDesc: form.receiveDesc,
    viabilityOssId: form.viabilityOssId ?? null,
    viabilityFileName: form.viabilityOssId ? form.viabilityFileName ?? null : null,
    origDesc: form.origDesc,
    observeDesc: form.observeDesc,
    pretreatDesc: form.pretreatDesc
  };
  await saveSampleQc(props.sampleId, payload);
  setDirty(false);
  proxy?.$modal.msgSuccess(t('lqg.qc.editor.saved'));
};

// ── 细胞活率测定附件（单文件）────────────────────────────────────────────────
const VIABILITY_MAX_MB = 50;

const handleViabilityBeforeUpload = (file: File) => {
  if (file.size / 1024 / 1024 > VIABILITY_MAX_MB) {
    proxy?.$modal.msgError(`「${file.name}」` + t('lqg.qc.attachment.tooLarge', { max: VIABILITY_MAX_MB }));
    return false;
  }
  proxy?.$modal.loading(t('lqg.qc.uploading'));
  return true;
};

/** 上传请求本身失败（超限的空 400 / 413 / 登录过期 / 网络断了 …）：说清原因（与图片位 / 附件同一口径） */
const handleViabilityError = (error: unknown, file: any) => {
  proxy?.$modal.closeLoading();
  proxy?.$modal.msgError(uploadErrorMessage(t, error, file?.name, VIABILITY_MAX_MB));
};

const handleViabilitySuccess = (res: any, file: any) => {
  proxy?.$modal.closeLoading();
  if (res?.code !== 200) {
    proxy?.$modal.msgError(uploadBizErrorMessage(t, res, file?.name));
    return;
  }
  form.viabilityOssId = res.data.ossId;
  form.viabilityFileName = res.data.fileName;
  proxy?.$modal.msgSuccess(t('lqg.qc.tab.viabilityPicked', { name: res.data.fileName }));
};

/** ★ 摘掉 = 保存时发 0（issue #212）；传 null 后端会当成「不动」 */
const handleViabilityRemove = () => {
  form.viabilityOssId = 0;
  form.viabilityFileName = null;
};

defineExpose({ save, isDirty: () => dirty.value, syncFromDoc });
</script>

<style scoped lang="scss">
.lqg-sample-qc {
  .lqg-sample-qc__two {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 0 16px;
  }
  .lqg-sample-qc__viability {
    display: flex;
    align-items: center;
    gap: 6px;
    min-height: 32px;
  }
  .lqg-sample-qc__viability-name {
    max-width: 160px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .lqg-sample-qc__slot {
    margin-top: 4px;
    padding-top: 8px;
    border-top: 1px solid var(--lqg-line);
  }
  .lqg-sample-qc__slot-title {
    margin-bottom: 6px;
    font-size: 13px;
    font-weight: 600;
    color: var(--lqg-ink);
  }
  .lqg-sample-qc__slot-body {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    gap: 0 16px;
    // 图片位最多 3 张 96px + 2 个 8px 间距 = 304px：宽度按满 3 张留，加图时右边的描述不跟着跳
    > :first-child {
      flex: 0 0 304px;
      margin-bottom: 12px;
    }
  }
  .lqg-sample-qc__desc {
    flex: 1 1 240px;
    min-width: 0;
  }
  :deep(.el-form-item) {
    margin-bottom: 12px;
  }
  :deep(.el-form-item__label) {
    padding-bottom: 2px;
    font-size: 12px;
    color: var(--lqg-ink-2);
  }
}
</style>
