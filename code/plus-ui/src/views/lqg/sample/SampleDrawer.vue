<template>
  <div class="lqg-sample-drawer">
    <el-drawer v-model="visible" :title="title" size="720px" append-to-body :close-on-click-modal="true" @closed="handleClosed">
      <div v-if="loading" class="lqg-sample-drawer__loading">{{ t('lqg.sample.loading') }}</div>

      <template v-else>
        <!-- 有效样本顶部小字：最后修改：某某 · 时间（updateTime 为 null = 从没改过） -->
        <div v-if="mode === 'edit'" class="lqg-sample-drawer__lastmod">
          <span v-if="neverModified(form)">
            {{ t('lqg.sample.drawer.lastModifiedNever') }}
          </span>
          <span v-else>
            {{ t('lqg.sample.drawer.lastModified', { name: form.updateByName || '—', time: form.updateTime }) }}
          </span>
        </div>


        <!-- 送检信息 -->
        <div class="lqg-sample-drawer__section">{{ t('lqg.sample.drawer.sectionSubmit') }}</div>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" :disabled="readonly">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item :label="t('lqg.sample.field.sampleKind')" prop="sampleKind">
                <!-- 类别由所在页面定（样本记录信息表 / 类器官收样记录，CR-20260924-10）：只显示、不给改 -->
                <SegButtons v-model="form.sampleKind" :options="kindOptions" :clearable="false" disabled />
              </el-form-item>
            </el-col>
            <el-col v-if="mode !== 'create'" :span="12">
              <el-form-item :label="t('lqg.sample.field.submitNo')">
                <span class="lqg-sample-drawer__mono">{{ form.submitNo || '—' }}</span>
              </el-form-item>
            </el-col>
            <el-col v-if="mode !== 'create'" :span="12">
              <el-form-item :label="t('lqg.sample.field.verifyStatus')">
                <dict-tag :options="lqg_verify_status" :value="form.verifyStatus" />
              </el-form-item>
            </el-col>
            <el-col v-if="mode !== 'create' && form.submitSource === 'external'" :span="12">
              <el-form-item :label="t('lqg.sample.field.submitterName')">
                <span>{{ form.submitterName || '—' }}</span>
              </el-form-item>
            </el-col>

            <el-col :span="12">
              <el-form-item :label="t('lqg.sample.field.sourceUnit')" prop="sourceUnitId">
                <el-select v-model="form.sourceUnitId" clearable filterable class="lqg-sample-drawer__control">
                  <el-option v-for="unit in units" :key="String(unit.unitId)" :label="unit.unitName" :value="unit.unitId" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-if="!form.sourceUnitId" :span="12">
              <el-form-item :label="t('lqg.sample.field.sourceUnitName')" prop="sourceUnitName">
                <el-input v-model="form.sourceUnitName" :placeholder="t('lqg.sample.field.sourceUnitPlaceholder')" maxlength="100" clearable />
              </el-form-item>
            </el-col>

            <el-col :span="12">
              <el-form-item :label="t('lqg.sample.field.operatorName')" prop="operatorName">
                <el-input v-model="form.operatorName" maxlength="50" clearable />
              </el-form-item>
            </el-col>

            <el-col v-if="isTissue" :span="12">
              <el-form-item :label="t('lqg.sample.field.tissueType')" prop="tissueType">
                <el-input v-model="form.tissueType" maxlength="100" clearable />
              </el-form-item>
            </el-col>
            <el-col v-if="isTissue" :span="12">
              <el-form-item :label="t('lqg.sample.field.donorName')" prop="donorName">
                <el-input v-model="form.donorName" maxlength="50" clearable />
              </el-form-item>
            </el-col>
            <el-col v-if="isTissue" :span="12">
              <el-form-item :label="t('lqg.sample.field.gender')" prop="gender">
                <SegButtons v-model="form.gender" :options="genderOptions" />
              </el-form-item>
            </el-col>
            <el-col v-if="isTissue" :span="12">
              <el-form-item :label="t('lqg.sample.field.age')" prop="age">
                <el-input v-model="form.age" maxlength="20" clearable />
              </el-form-item>
            </el-col>
            <el-col v-if="isTissue" :span="12">
              <el-form-item :label="t('lqg.sample.field.hospitalNo')" prop="hospitalNo">
                <el-input v-model="form.hospitalNo" maxlength="50" clearable />
              </el-form-item>
            </el-col>
            <el-col v-if="isTissue" :span="24">
              <el-form-item :label="t('lqg.sample.field.hasPathology')" prop="hasPathology">
                <SegButtons v-model="form.hasPathology" :options="flagOptions" />
              </el-form-item>
            </el-col>

            <el-col v-if="!isTissue" :span="12">
              <el-form-item :label="t('lqg.sample.field.organoidType')" prop="organoidType">
                <el-input v-model="form.organoidType" maxlength="100" clearable />
              </el-form-item>
            </el-col>
            <!-- 代数（CR-20260924-10：甲方 2026-09-24 第 18 行）：紧跟类器官类型，送检段 = 外部能填能改、核验时实验室能改 -->
            <el-col v-if="!isTissue" :span="12">
              <el-form-item :label="t('lqg.sample.field.passage')" prop="passage">
                <el-input v-model="form.passage" maxlength="4" clearable :placeholder="t('lqg.sample.field.passagePlaceholder')" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item :label="t('lqg.sample.field.remark')" prop="remark">
                <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <!-- 收样信息 -->
        <div class="lqg-sample-drawer__section">{{ t('lqg.sample.drawer.sectionReceive') }}</div>
        <el-alert v-if="mode === 'verify'" type="warning" :closable="false" show-icon class="mb8">
          {{ isTissue ? t('lqg.sample.verifyHintTissue') : t('lqg.sample.verifyHintOrganoid') }}
        </el-alert>
        <el-form ref="receiveRef" :model="form" :rules="receiveRules" label-width="110px" :disabled="readonly">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item :label="t('lqg.sample.field.receiveDate')" prop="receiveDate">
                <el-date-picker
                  v-model="form.receiveDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  class="lqg-sample-drawer__control"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('lqg.sample.field.internalNo')" prop="internalNo">
                <el-input v-model="form.internalNo" maxlength="64" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('lqg.sample.field.processTime')" prop="processTime">
                <el-date-picker
                  v-model="form.processTime"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  class="lqg-sample-drawer__control"
                />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item :label="t('lqg.sample.field.isFixed')" prop="isFixed">
                <SegButtons v-model="form.isFixed" :options="flagOptions" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item :label="t('lqg.sample.field.hasQcSheet')" prop="hasQcSheet">
                <SegButtons v-model="form.hasQcSheet" :options="flagOptions" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item :label="t('lqg.sample.field.hasViabilityReport')" prop="hasViabilityReport">
                <SegButtons v-model="form.hasViabilityReport" :options="flagOptions" />
              </el-form-item>
            </el-col>
            <el-col v-if="form.verifyStatus === 'invalid' || mode === 'edit'" :span="24">
              <el-form-item :label="t('lqg.sample.field.invalidReason')" prop="invalidReason">
                <span>{{ form.invalidReason || '—' }}</span>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </template>

      <template #footer>
        <div class="lqg-sample-drawer__footer">
          <!-- 更多：改判无效（有效样本才出；名下已有下游记录时禁用并说明） -->
          <el-dropdown v-if="mode === 'edit'" class="lqg-sample-drawer__more" @command="handleMore">
            <el-button plain>
              {{ t('lqg.sample.drawer.more') }}<el-icon class="el-icon--right"><arrow-down /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="invalid" :disabled="hasChildren">
                  {{ t('lqg.sample.drawer.changeToInvalid') }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <span v-if="hasChildren" class="lqg-sample-drawer__hint">{{ t('lqg.sample.drawer.hasChildrenTip') }}</span>

          <div class="lqg-sample-drawer__actions">
            <template v-if="mode === 'verify'">
              <el-button type="primary" :loading="submitting" @click="submitVerifyValid">{{ t('lqg.sample.drawer.saveValid') }}</el-button>
              <el-button type="danger" plain :loading="submitting" @click="openInvalidDialog">{{ t('lqg.sample.drawer.saveInvalid') }}</el-button>
            </template>
            <template v-else-if="mode === 'edit'">
              <el-button type="primary" :loading="submitting" @click="submitEdit">{{ t('lqg.sample.drawer.save') }}</el-button>
            </template>
            <template v-else>
              <el-button type="primary" :loading="submitting" @click="submitCreate">{{ t('lqg.sample.drawer.save') }}</el-button>
            </template>
            <el-button @click="visible = false">{{ t('lqg.sample.drawer.cancel') }}</el-button>
          </div>
        </div>
      </template>
    </el-drawer>

    <!-- 判无效的原因（外部看得到这句话） -->
    <el-dialog v-model="invalidDialog.visible" :title="t('lqg.sample.drawer.invalidReasonTitle')" width="460px" append-to-body>
      <el-form ref="invalidRef" :model="invalidDialog" :rules="invalidRules" label-width="90px">
        <el-form-item :label="t('lqg.sample.field.invalidReason')" prop="reason">
          <el-input
            v-model="invalidDialog.reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            :placeholder="t('lqg.sample.drawer.invalidReasonPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="danger" :loading="submitting" @click="submitInvalid">{{ t('lqg.sample.drawer.saveInvalid') }}</el-button>
        <el-button @click="invalidDialog.visible = false">{{ t('lqg.sample.drawer.cancel') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="LqgSampleDrawer" lang="ts">
import SegButtons from '@/components/lqg/SegButtons/index.vue';
import { addSample, getSample, neverModified, updateSample, verifySample } from '@/api/lqg/sample';
import type { SampleForm, SampleSubmitSegment, SampleVO, SampleVerifyForm } from '@/api/lqg/sample';
import type { SourceUnitVO } from '@/api/lqg/auth/group';
import type { SampleKind } from './pages';
import { isPassageValue, normalizePassage } from './passage';
import { useI18n } from 'vue-i18n';

const props = defineProps<{
  units: SourceUnitVO[];
  /** 所在页面的样本类别（新增时按它建；CR-20260924-10 起两页各新增各的） */
  kind?: SampleKind;
}>();

const emit = defineEmits<{ (e: 'saved'): void }>();

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { t } = useI18n();
const { lqg_verify_status } = toRefs<any>(proxy?.useDict('lqg_verify_status'));

type Mode = 'create' | 'edit' | 'verify';

const visible = ref(false);
const loading = ref(false);
const submitting = ref(false);
const mode = ref<Mode>('create');
/**
 * 「名下有包埋 / 冻存 / 质控文档」= 有效样本不许改判无效（UI:admin.sample.edit）。
 * ★ 详情接口目前不带这个标记（SampleChildrenCheckers 还没有下游实现，SAMPLE-MODEL-001 注册 0 个），
 *   所以这里默认 false，真正兜底在后端：SampleVerifyService 会拒并回原因 —— 见完工报告「遗留」。
 */
const hasChildren = ref(false);

const formRef = ref<ElFormInstance>();
const receiveRef = ref<ElFormInstance>();
const invalidRef = ref<ElFormInstance>();

const emptyForm = (): SampleForm & Partial<SampleVO> => ({
  id: null,
  submitNo: null,
  sampleKind: 'tissue',
  submitSource: null,
  submitterName: null,
  verifyStatus: null,
  invalidReason: null,
  sourceUnitId: null,
  sourceUnitName: null,
  donorName: null,
  gender: null,
  age: null,
  hospitalNo: null,
  tissueType: null,
  organoidType: null,
  passage: null,
  hasPathology: null,
  receiveDate: null,
  internalNo: null,
  isFixed: null,
  processTime: null,
  hasQcSheet: null,
  hasViabilityReport: null,
  operatorName: null,
  remark: null,
  updateByName: null,
  updateTime: null
});

const form = ref<ReturnType<typeof emptyForm>>(emptyForm());

const invalidDialog = reactive<{ visible: boolean; reason: string }>({ visible: false, reason: '' });

const isTissue = computed(() => form.value.sampleKind !== 'organoid');
/**
 * 整表只读 —— 本抽屉没有只读档。
 *
 * ★ FIX V02（issue #147）：核验模式下送检段**全部可改、并且真的会保存**——
 *   「判为有效并保存」/「判为无效」把整份送检段随 `PUT /lqg/sample/{id}/verify` 的 `submitSegment`
 *   一起带上，后端与核验结论同一个事务落库（规则与「保存」走的 `PUT /lqg/sample` 同一份）。
 *   以前送检段显示成可编辑、请求体里却没有它，改了也被静默丢弃。
 *   来源单位也放开：外部只填了单位名（没挂上单位 id）的样本，核验时就能在下拉里归口到正式单位（V01）。
 * 收样段在核验模式下正是要填的部分。这里常量 false 只是让两个 el-form 的 disabled 绑定留着口子。
 */
const readonly = computed(() => false);

const title = computed(() =>
  mode.value === 'create'
    ? isTissue.value
      ? t('lqg.sample.drawer.addTissue')
      : t('lqg.sample.drawer.addOrganoid')
    : mode.value === 'verify'
      ? t('lqg.sample.drawer.verify')
      : t('lqg.sample.drawer.edit')
);

const kindOptions = computed(() => [
  { label: t('lqg.sample.kind.tissue'), value: 'tissue' },
  { label: t('lqg.sample.kind.organoid'), value: 'organoid' }
]);
const genderOptions = computed(() => [
  { label: t('lqg.sample.gender.male'), value: 'male' },
  { label: t('lqg.sample.gender.female'), value: 'female' },
  { label: t('lqg.sample.gender.unknown'), value: 'unknown' }
]);
const flagOptions = computed(() => [
  { label: t('lqg.sample.flag.yes'), value: 'Y' },
  { label: t('lqg.sample.flag.no'), value: 'N' }
]);

/** 代数：选填；填了必须形如 P3（小写 p 提交时转大写）—— 后端同一规则兜底 */
const validatePassage = (_rule: unknown, value: string | null | undefined, callback: (e?: Error) => void) => {
  if (isPassageValue(value)) {
    callback();
  } else {
    callback(new Error(t('lqg.sample.drawer.passageInvalid')));
  }
};

const rules = computed<ElFormRules>(() => ({
  sampleKind: [{ required: true, message: t('lqg.sample.drawer.kindRequired'), trigger: 'change' }],
  tissueType: isTissue.value ? [{ required: true, message: t('lqg.sample.drawer.tissueRequired'), trigger: 'blur' }] : [],
  organoidType: isTissue.value ? [] : [{ required: true, message: t('lqg.sample.drawer.organoidRequired'), trigger: 'blur' }],
  passage: isTissue.value ? [] : [{ validator: validatePassage, trigger: 'blur' }]
}));

const receiveRules = computed<ElFormRules>(() => ({
  receiveDate: [{ required: true, message: t('lqg.sample.drawer.receiveDateRequired'), trigger: 'change' }],
  internalNo: [{ required: true, message: t('lqg.sample.drawer.numberRequired'), trigger: 'blur' }]
}));

const invalidRules: ElFormRules = {
  reason: [{ required: true, message: t('lqg.sample.drawer.invalidReasonRequired'), trigger: 'blur' }]
};

// ── 打开 ────────────────────────────────────────────────────────────────────
const openAdd = (kind?: string) => {
  form.value = emptyForm();
  form.value.sampleKind = (kind ?? props.kind) === 'organoid' ? 'organoid' : 'tissue';
  hasChildren.value = false;
  mode.value = 'create';
  visible.value = true;
};

const open = async (row: SampleVO) => {
  mode.value = row.verifyStatus === 'pending' ? 'verify' : 'edit';
  visible.value = true;
  loading.value = true;
  try {
    const res = await getSample(row.id);
    form.value = { ...emptyForm(), ...(res.data as SampleVO) };
    hasChildren.value = false;
  } catch {
    proxy?.$modal.msgError(t('lqg.sample.drawer.loadFailed'));
  } finally {
    loading.value = false;
  }
};

const handleClosed = () => {
  form.value = emptyForm();
  hasChildren.value = false;
};

// ── 保存 ────────────────────────────────────────────────────────────────────
const payload = (): SampleForm => {
  const f = form.value;
  return {
    id: f.id ?? null,
    sampleKind: f.sampleKind,
    sourceUnitId: f.sourceUnitId ?? null,
    sourceUnitName: f.sourceUnitName ?? null,
    donorName: f.donorName ?? null,
    gender: f.gender ?? null,
    age: f.age ?? null,
    hospitalNo: f.hospitalNo ?? null,
    tissueType: f.tissueType ?? null,
    organoidType: f.organoidType ?? null,
    passage: normalizePassage(f.passage),
    hasPathology: f.hasPathology ?? null,
    receiveDate: f.receiveDate ?? null,
    internalNo: f.internalNo ?? null,
    isFixed: f.isFixed ?? null,
    processTime: f.processTime ?? null,
    hasQcSheet: f.hasQcSheet ?? null,
    hasViabilityReport: f.hasViabilityReport ?? null,
    operatorName: f.operatorName ?? null,
    remark: f.remark ?? null
  };
};

const validateAll = async (): Promise<boolean> => {
  const results = await Promise.all([
    formRef.value?.validate().then(() => true).catch(() => false) ?? Promise.resolve(true),
    receiveRef.value?.validate().then(() => true).catch(() => false) ?? Promise.resolve(true)
  ]);
  return results.every(Boolean);
};

const submitCreate = async () => {
  if (!(await validateAll())) {
    return;
  }
  submitting.value = true;
  try {
    await addSample(payload());
    proxy?.$modal.msgSuccess(t('lqg.sample.drawer.saved'));
    visible.value = false;
    emit('saved');
  } finally {
    submitting.value = false;
  }
};

const submitEdit = async () => {
  if (!(await validateAll())) {
    return;
  }
  submitting.value = true;
  try {
    await updateSample(payload());
    proxy?.$modal.msgSuccess(t('lqg.sample.drawer.saved'));
    visible.value = false;
    emit('saved');
  } finally {
    submitting.value = false;
  }
};

/**
 * 送检段（整段）：抽屉上半部分展示的全部送检字段。
 *
 * ★ 与 payload() 里的同名键一一对应（同一份表单、同一份后端规则），只是不带收样段与身份列。
 */
const submitSegmentPayload = (): SampleSubmitSegment => {
  const f = form.value;
  return {
    sourceUnitId: f.sourceUnitId ?? null,
    sourceUnitName: f.sourceUnitName ?? null,
    donorName: f.donorName ?? null,
    gender: f.gender ?? null,
    age: f.age ?? null,
    hospitalNo: f.hospitalNo ?? null,
    tissueType: f.tissueType ?? null,
    organoidType: f.organoidType ?? null,
    // ★ 代数属于送检段（整段替换）：核验时实验室改了就随核验一起落库；不带 = 清空，所以一定要带上
    passage: normalizePassage(f.passage),
    hasPathology: f.hasPathology ?? null,
    remark: f.remark ?? null
  };
};

const verifyPayload = (action: 'valid' | 'invalid', reason?: string): SampleVerifyForm => {
  const f = form.value;
  return {
    action,
    receiveDate: f.receiveDate ?? null,
    internalNo: f.internalNo ?? null,
    isFixed: f.isFixed ?? null,
    processTime: f.processTime ?? null,
    hasQcSheet: f.hasQcSheet ?? null,
    hasViabilityReport: f.hasViabilityReport ?? null,
    operatorName: f.operatorName ?? null,
    reason: reason ?? null,
    // ★ FIX V02：送检段随核验一起保存（不带这个键，送检段的修改会被静默丢弃）
    submitSegment: submitSegmentPayload()
  };
};

const submitVerifyValid = async () => {
  if (!(await validateAll())) {
    return;
  }
  submitting.value = true;
  try {
    await verifySample(form.value.id as string | number, verifyPayload('valid'));
    proxy?.$modal.msgSuccess(t('lqg.sample.drawer.verifiedValid'));
    visible.value = false;
    emit('saved');
  } finally {
    submitting.value = false;
  }
};

const openInvalidDialog = () => {
  invalidDialog.reason = '';
  invalidDialog.visible = true;
};

const submitInvalid = () => {
  invalidRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    // 送检段随判无效一起保存（FIX V02）：先过一遍送检段的必填，免得在弹窗里才看到后端 400
    const submitOk =
      (await formRef.value
        ?.validate()
        .then(() => true)
        .catch(() => false)) ?? true;
    if (!submitOk) {
      proxy?.$modal.msgWarning(
        isTissue.value
          ? t('lqg.sample.drawer.tissueRequired')
          : isPassageValue(form.value.passage)
            ? t('lqg.sample.drawer.organoidRequired')
            : t('lqg.sample.drawer.passageInvalid')
      );
      return;
    }
    submitting.value = true;
    try {
      await verifySample(form.value.id as string | number, verifyPayload('invalid', invalidDialog.reason));
      proxy?.$modal.msgSuccess(t('lqg.sample.drawer.verifiedInvalid'));
      invalidDialog.visible = false;
      visible.value = false;
      emit('saved');
    } finally {
      submitting.value = false;
    }
  });
};

const handleMore = (command: string) => {
  if (command === 'invalid' && !hasChildren.value) {
    openInvalidDialog();
  }
};

defineExpose({ openAdd, open });
</script>

<style scoped lang="scss">
.lqg-sample-drawer {
  .lqg-sample-drawer__section {
    margin: 8px 0 12px;
    padding-left: 8px;
    font-weight: 600;
    color: var(--lqg-ink);
    border-left: 3px solid var(--lqg-primary);
  }
  .lqg-sample-drawer__lastmod {
    margin-bottom: 8px;
    font-size: 12px;
    color: var(--lqg-ink-2);
  }
  .lqg-sample-drawer__control {
    width: 100%;
  }
  .lqg-sample-drawer__mono {
    font-family: var(--lqg-font-mono);
  }
  .lqg-sample-drawer__loading {
    padding: 24px 0;
    color: var(--lqg-ink-3);
  }
  .lqg-sample-drawer__footer {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 12px;
    flex-wrap: wrap;
  }
  .lqg-sample-drawer__more {
    margin-right: auto;
  }
  .lqg-sample-drawer__hint {
    margin-right: auto;
    font-size: 12px;
    color: var(--lqg-warn);
  }
  .lqg-sample-drawer__actions {
    display: inline-flex;
    gap: 8px;
  }
}
</style>
