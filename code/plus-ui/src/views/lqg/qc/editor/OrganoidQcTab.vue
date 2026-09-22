<template>
  <div class="lqg-organoid-qc">
    <el-form :model="form" label-position="top" class="lqg-organoid-qc__form">
      <!-- ① 样本观察情况：图片位 1-3 张（slot=organoid_observe）。操作即落库，
             与文本字段的「保存草稿」是两条路（图片有独立的 POST/DELETE 端点）。 -->
      <section class="lqg-organoid-qc__slot">
        <div class="lqg-organoid-qc__slot-title">{{ t('lqg.qc.organoid.observeTitle') }}</div>
        <ImageSlotUploader
          :sample-id="sampleId"
          :doc-type="docType"
          slot="organoid_observe"
          :images="slotImages"
          :disabled="readonly"
          @changed="emit('changed')"
        />
      </section>

      <!-- ② 五栏文本（补丁语义：整份一起发，空串 = 清空）。
           「形成类器官时间」「反馈时间」= 日期选择器 + 手输：选日期填 yyyy-MM-dd，
           也可以直接写「约第 5 天」这种文字（后端不做格式校验，列是 VARCHAR）。 -->
      <el-form-item :label="t('lqg.qc.organoid.formedTime')">
        <div class="lqg-organoid-qc__datetime">
          <el-input
            v-model="form.formedTime"
            :disabled="readonly"
            clearable
            :placeholder="t('lqg.qc.organoid.timePlaceholder')"
            class="lqg-organoid-qc__datetime-text"
          />
          <el-date-picker
            :model-value="pickerValue(form.formedTime)"
            type="date"
            value-format="YYYY-MM-DD"
            :clearable="false"
            :disabled="readonly"
            :placeholder="t('lqg.qc.organoid.pickDate')"
            class="lqg-organoid-qc__datetime-picker"
            @update:model-value="(value) => (form.formedTime = (value as string) || '')"
          />
        </div>
        <div class="lqg-organoid-qc__datetime-hint">{{ t('lqg.qc.organoid.timeHint') }}</div>
      </el-form-item>

      <el-form-item :label="t('lqg.qc.organoid.growthState')">
        <el-input v-model="form.growthState" :disabled="readonly" clearable />
      </el-form-item>

      <el-form-item :label="t('lqg.qc.organoid.growthDesc')">
        <el-input v-model="form.growthDesc" type="textarea" :rows="3" :disabled="readonly" />
      </el-form-item>

      <el-form-item :label="t('lqg.qc.organoid.plannedDrugScreen')">
        <el-input v-model="form.plannedDrugScreen" type="textarea" :rows="2" :disabled="readonly" />
      </el-form-item>

      <el-form-item :label="t('lqg.qc.organoid.feedbackTime')">
        <div class="lqg-organoid-qc__datetime">
          <el-input
            v-model="form.feedbackTime"
            :disabled="readonly"
            clearable
            :placeholder="t('lqg.qc.organoid.timePlaceholder')"
            class="lqg-organoid-qc__datetime-text"
          />
          <el-date-picker
            :model-value="pickerValue(form.feedbackTime)"
            type="date"
            value-format="YYYY-MM-DD"
            :clearable="false"
            :disabled="readonly"
            :placeholder="t('lqg.qc.organoid.pickDate')"
            class="lqg-organoid-qc__datetime-picker"
            @update:model-value="(value) => (form.feedbackTime = (value as string) || '')"
          />
        </div>
        <div class="lqg-organoid-qc__datetime-hint">{{ t('lqg.qc.organoid.timeHint') }}</div>
      </el-form-item>

      <!-- ③ 底部附件区（UI:admin.qc.editor 的左栏底部；后端 /organoid-qc/attachment 支持） -->
      <section class="lqg-organoid-qc__slot">
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

<script setup name="LqgOrganoidQcTab" lang="ts">
import { saveOrganoidQc, type QcOrganoidDocVO } from '@/api/lqg/qc';
import ImageSlotUploader from '../components/ImageSlotUploader.vue';
import AttachmentList from '../components/AttachmentList.vue';
import { useI18n } from 'vue-i18n';

// ============================================================================
// 「类器官质控表」页签（QC-WEB-002 / FLOW:F-QC-01.step3）
//
// 模板顺序：样本观察情况（图片位 slot=organoid_observe，1-3 张）
//          → 形成类器官时间 → 生长状态 → 类器官生长情况 → 预计筛药 → 反馈时间。
// 五栏全是**自由文本**（`t_lqg_qc_organoid` 五列都是 VARCHAR，见
// FIELD:t_lqg_qc_organoid.planned_drug_screen）：两个「时间」栏既能用日期选择器
// 选（填 yyyy-MM-dd），也允许直接手输「约第 5 天」这类文字 —— 后端不做格式校验。
//
// ★ 保存是补丁语义（同 SampleQcTab）：整份表单一起发；清空某栏发空串，不发 null。
// ★ 图片与附件是「操作即落库」（各自独立的 POST/DELETE），文本才走「保存草稿」的 PUT。
// ============================================================================

const props = defineProps<{
  sampleId: string | number;
  /** 类器官质控表 VO（GET /lqg/qc/{sampleId} 的 data.organoidQc） */
  doc: QcOrganoidDocVO;
  readonly?: boolean;
}>();

const emit = defineEmits<{
  (e: 'changed'): void;
  (e: 'dirty', value: boolean): void;
}>();

const { t } = useI18n();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const docType = 'organoid-qc' as const;

const form = reactive({
  formedTime: '',
  growthState: '',
  growthDesc: '',
  plannedDrugScreen: '',
  feedbackTime: ''
});

const dirty = ref(false);
let syncing = false;

/** 该页签唯一的图片位（`images` 是 map；没图时后端给 `[]`，缺键也要兜住） */
const slotImages = computed(() => props.doc?.images?.['organoid_observe'] ?? []);

/** 只把「本来就是 yyyy-MM-dd」的值回灌给日期选择器；手输的文字不喂给它（否则会被清掉） */
const pickerValue = (value: string) => (/^\d{4}-\d{2}-\d{2}$/.test(value) ? value : null);

/** 从后端 VO 灌进表单；用户有未保存改动时不覆盖（否则传一张图就把半篇文字冲掉） */
const syncFromDoc = (force = false) => {
  if (!props.doc || (dirty.value && !force)) {
    return;
  }
  syncing = true;
  form.formedTime = props.doc.formedTime ?? '';
  form.growthState = props.doc.growthState ?? '';
  form.growthDesc = props.doc.growthDesc ?? '';
  form.plannedDrugScreen = props.doc.plannedDrugScreen ?? '';
  form.feedbackTime = props.doc.feedbackTime ?? '';
  syncing = false;
  setDirty(false);
};

const setDirty = (value: boolean) => {
  dirty.value = value;
  emit('dirty', value);
};

watch(
  () => props.doc,
  () => syncFromDoc(),
  { immediate: true, deep: true }
);
watch(
  form,
  () => {
    if (!syncing) {
      setDirty(true);
    }
  },
  { deep: true }
);

/** 保存草稿：整份表单一起发（五栏都是自由文本，补丁语义） */
const save = async () => {
  await saveOrganoidQc(props.sampleId, {
    formedTime: form.formedTime,
    growthState: form.growthState,
    growthDesc: form.growthDesc,
    plannedDrugScreen: form.plannedDrugScreen,
    feedbackTime: form.feedbackTime
  });
  setDirty(false);
  proxy?.$modal.msgSuccess(t('lqg.qc.editor.saved'));
};

defineExpose({ save, isDirty: () => dirty.value, syncFromDoc });
</script>

<style scoped lang="scss">
.lqg-organoid-qc {
  .lqg-organoid-qc__slot {
    margin-top: 4px;
    padding-top: 8px;
    border-top: 1px solid var(--lqg-line);
  }
  .lqg-organoid-qc__slot-title {
    margin-bottom: 6px;
    font-size: 13px;
    font-weight: 600;
    color: var(--lqg-ink);
  }
  .lqg-organoid-qc__datetime {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
  }
  .lqg-organoid-qc__datetime-text {
    flex: 1;
    min-width: 0;
  }
  .lqg-organoid-qc__datetime-picker {
    width: 150px;
    flex: none;
  }
  .lqg-organoid-qc__datetime-hint {
    width: 100%;
    font-size: 12px;
    color: var(--lqg-ink-3);
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
