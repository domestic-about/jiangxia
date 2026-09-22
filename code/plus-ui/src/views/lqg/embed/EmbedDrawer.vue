<template>
  <div class="lqg-embed-drawer">
    <el-drawer v-model="visible" :title="title" size="780px" append-to-body :close-on-click-modal="true" @closed="handleClosed">
      <div v-if="loading" class="lqg-embed-drawer__loading">{{ t('lqg.embed.loading') }}</div>

      <template v-else>
        <!-- 顶部小字「最后修改：某某 · 时间」——updateTime 为 null = 从没改过（跨票语义） -->
        <div v-if="mode !== 'create'" class="lqg-embed-drawer__lastmod">
          <span v-if="neverModified(form)">
            {{ t('lqg.embed.drawer.lastModifiedNever') }}
          </span>
          <span v-else>
            {{ t('lqg.embed.drawer.lastModified', { name: form.updateByName || '—', time: form.updateTime }) }}
          </span>
        </div>

        <!-- 核验段提示：所挂样本还没核验有效时判为有效置灰并写明原因（ticket §0 口径复述 4） -->
        <el-alert v-if="mode === 'verify'" :type="sampleOk ? 'info' : 'warning'" :closable="false" show-icon class="mb8">
          <template v-if="form.verifyStatus === 'invalid' && form.invalidReason">
            <div>{{ t('lqg.embed.drawer.invalidReason') }}：{{ form.invalidReason }}</div>
          </template>
          <div>{{ sampleOk ? t('lqg.embed.drawer.sampleVerifiedTip') : t('lqg.embed.drawer.sampleNotVerified', { status: sampleStatusText }) }}</div>
          <div class="lqg-embed-drawer__hint">{{ t('lqg.embed.drawer.verifyHint') }}</div>
        </el-alert>

        <!-- ① 包埋信息 -->
        <div class="lqg-embed-drawer__section">{{ t('lqg.embed.drawer.sectionEmbed') }}</div>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
          <el-row :gutter="12">
            <el-col :span="24">
              <el-form-item :label="t('lqg.embed.drawer.sample')" prop="sampleId">
                <!-- 新增时远程搜索（只列已核验有效的样本）；编辑 / 核验时锁定 -->
                <el-select
                  v-if="mode === 'create'"
                  v-model="form.sampleId"
                  remote
                  filterable
                  reserve-keyword
                  :remote-method="searchSamples"
                  :loading="sampleSearching"
                  :placeholder="t('lqg.embed.drawer.samplePlaceholder')"
                  class="lqg-embed-drawer__control"
                  @change="handleSampleChange"
                >
                  <el-option v-for="s in sampleOptions" :key="String(s.id)" :label="sampleLabel(s)" :value="s.id" />
                </el-select>
                <span v-else>
                  <span class="lqg-embed-drawer__mono">{{ form.internalNo || '—' }}</span>
                  <span class="lqg-embed-drawer__muted"> · {{ form.submitNo || '—' }}</span>
                  <span class="lqg-embed-drawer__muted"> · {{ t('lqg.embed.drawer.sampleLocked') }}</span>
                </span>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('lqg.embed.drawer.paraffinBlockNo')" prop="paraffinBlockNo">
                <el-input
                  v-model="form.paraffinBlockNo"
                  :placeholder="t('lqg.embed.drawer.paraffinBlockNoPlaceholder')"
                  maxlength="64"
                  clearable
                  class="lqg-embed-drawer__mono-input"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('lqg.embed.drawer.sampleType')" prop="sampleType">
                <el-input v-model="form.sampleType" maxlength="50" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('lqg.embed.drawer.organoidSourceType')" prop="organoidSourceType">
                <el-input v-model="form.organoidSourceType" maxlength="100" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('lqg.embed.drawer.embedBy')" prop="embedBy">
                <el-input v-model="form.embedBy" maxlength="50" clearable />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <!-- ② 工序时间（七个，全部可空 —— 这是一张会被反复打开补填的表） -->
        <div class="lqg-embed-drawer__section">{{ t('lqg.embed.drawer.sectionProcess') }}</div>
        <el-form label-width="130px">
          <el-row :gutter="12">
            <el-col v-for="field in processFields" :key="field" :span="12">
              <el-form-item :label="t('lqg.embed.drawer.' + field)">
                <el-date-picker
                  v-model="form[field]"
                  type="date"
                  value-format="YYYY-MM-DD"
                  class="lqg-embed-drawer__control"
                  clearable
                />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <!-- ③ 染色与 marker -->
        <div class="lqg-embed-drawer__section">{{ t('lqg.embed.drawer.sectionStain') }}</div>
        <el-form label-width="130px">
          <el-row :gutter="12">
            <el-col :span="24">
              <el-form-item :label="t('lqg.embed.drawer.stain')">
                <div class="lqg-embed-drawer__stack">
                  <!-- ★ 五个按钮；「无染色」与其余互斥（toggleStain 是唯一入口，fixture 单测覆盖） -->
                  <SegButtons
                    :model-value="form.stainTypes ?? []"
                    :options="stainOptions"
                    multiple
                    @update:model-value="handleStainChange"
                  />
                  <span class="lqg-embed-drawer__hint">{{ t('lqg.embed.drawer.stainHint') }}</span>
                </div>
              </el-form-item>
            </el-col>
            <el-col v-if="hasOther" :span="12">
              <el-form-item :label="t('lqg.embed.drawer.stainOther')" prop="stainOther">
                <el-input
                  v-model="form.stainOther"
                  :placeholder="t('lqg.embed.drawer.stainOtherPlaceholder')"
                  maxlength="50"
                  clearable
                />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item :label="t('lqg.embed.drawer.marker')">
                <div class="lqg-embed-drawer__markers">
                  <div v-for="(marker, index) in markers" :key="index" class="lqg-embed-drawer__marker-row">
                    <el-input
                      v-model="marker.markerName"
                      :placeholder="t('lqg.embed.drawer.markerNamePlaceholder')"
                      maxlength="50"
                      clearable
                      class="lqg-embed-drawer__marker-name"
                    />
                    <SegButtons v-model="marker.expression" :options="markerExprOptions" class="lqg-embed-drawer__marker-expr" />
                    <el-button link type="danger" icon="Delete" @click="removeMarker(index)">
                      {{ t('lqg.embed.drawer.markerRemove') }}
                    </el-button>
                  </div>
                  <el-button plain icon="Plus" @click="addMarker">{{ t('lqg.embed.drawer.markerAdd') }}</el-button>
                  <span class="lqg-embed-drawer__hint">{{ t('lqg.embed.drawer.markerHint') }}</span>
                </div>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <!-- ④ 操作人与备注 -->
        <div class="lqg-embed-drawer__section">{{ t('lqg.embed.drawer.sectionOther') }}</div>
        <el-form label-width="130px">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item :label="t('lqg.embed.drawer.operatorName')">
                <el-input v-model="form.operatorName" maxlength="50" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item :label="t('lqg.embed.drawer.remark')">
                <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </template>

      <template #footer>
        <div class="lqg-embed-drawer__footer">
          <template v-if="mode === 'verify'">
            <!-- ★ 所挂样本还没核验有效 → 置灰并写明原因（不点了再等后端报错） -->
            <el-tooltip :disabled="sampleOk" :content="t('lqg.embed.drawer.sampleNotVerified', { status: sampleStatusText })">
              <span>
                <el-button type="primary" :loading="submitting" :disabled="!sampleOk" @click="submitValid">
                  {{ t('lqg.embed.drawer.saveValid') }}
                </el-button>
              </span>
            </el-tooltip>
            <el-button type="danger" plain :loading="submitting" @click="openInvalidDialog">
              {{ t('lqg.embed.drawer.saveInvalid') }}
            </el-button>
          </template>
          <el-button v-else type="primary" :loading="submitting" @click="submitSave">
            {{ t('lqg.embed.drawer.save') }}
          </el-button>
          <el-button @click="visible = false">{{ t('lqg.embed.drawer.cancel') }}</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 判无效的原因（外部看得到这句话） -->
    <el-dialog v-model="invalidDialog.visible" :title="t('lqg.embed.drawer.invalidReasonTitle')" width="460px" append-to-body>
      <el-form ref="invalidRef" :model="invalidDialog" :rules="invalidRules" label-width="90px">
        <el-form-item :label="t('lqg.embed.drawer.invalidReason')" prop="reason">
          <el-input
            v-model="invalidDialog.reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            :placeholder="t('lqg.embed.drawer.invalidReasonPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="danger" :loading="submitting" @click="submitInvalid">{{ t('lqg.embed.drawer.saveInvalid') }}</el-button>
        <el-button @click="invalidDialog.visible = false">{{ t('lqg.embed.drawer.cancel') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="LqgEmbedDrawer" lang="ts">
import SegButtons from '@/components/lqg/SegButtons/index.vue';
import { addEmbed, getEmbed, neverModified, sampleVerified, updateEmbed, verifyEmbed, isExternalPending } from '@/api/lqg/embed';
import type { EmbedForm, EmbedMarkerVO, EmbedVO } from '@/api/lqg/embed';
import { listSamples } from '@/api/lqg/sample';
import type { SampleVO } from '@/api/lqg/sample';
import { STAIN_ORDER, hasOtherStain, stainProblem, toggleStain } from './stain';
import { useI18n } from 'vue-i18n';

const emit = defineEmits<{ (e: 'saved'): void }>();

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { t } = useI18n();
const { lqg_stain_type, lqg_marker_expr, lqg_verify_status } = toRefs<any>(
  proxy?.useDict('lqg_stain_type', 'lqg_marker_expr', 'lqg_verify_status')
);

type Mode = 'create' | 'edit' | 'verify';

/** 七个工序时间（模板 16 列里的那七列） */
const processFields = [
  'tissueReceiveTime',
  'tissueProcessTime',
  'agaroseEmbedTime',
  'dehydrateTime',
  'agaroseSendTime',
  'paraffinEmbedTime',
  'sectionTime'
] as const;

type ProcessField = (typeof processFields)[number];

const visible = ref(false);
const loading = ref(false);
const submitting = ref(false);
const mode = ref<Mode>('create');
const sampleSearching = ref(false);
const sampleOptions = ref<SampleVO[]>([]);

const formRef = ref<ElFormInstance>();
const invalidRef = ref<ElFormInstance>();

const emptyForm = (): EmbedForm & Partial<EmbedVO> => ({
  id: null,
  sampleId: null,
  internalNo: null,
  submitNo: null,
  sampleVerifyStatus: null,
  paraffinBlockNo: null,
  sampleType: null,
  organoidSourceType: null,
  tissueReceiveTime: null,
  tissueProcessTime: null,
  agaroseEmbedTime: null,
  embedBy: null,
  dehydrateTime: null,
  agaroseSendTime: null,
  paraffinEmbedTime: null,
  sectionTime: null,
  stainTypes: [],
  stainOther: null,
  markers: [],
  operatorName: null,
  remark: null,
  verifyStatus: null,
  invalidReason: null,
  updateByName: null,
  updateTime: null
});

const form = ref<ReturnType<typeof emptyForm>>(emptyForm());
const markers = ref<EmbedMarkerVO[]>([]);
const invalidDialog = reactive<{ visible: boolean; reason: string }>({ visible: false, reason: '' });

const title = computed(() =>
  mode.value === 'create'
    ? t('lqg.embed.drawer.addTitle')
    : mode.value === 'verify'
      ? t('lqg.embed.drawer.verifyTitle')
      : t('lqg.embed.drawer.editTitle')
);

/** 染色按钮：字典中文标签（useDict 的参数是库里的真名 lqg_stain_type） */
const stainOptions = computed(() =>
  STAIN_ORDER.map((value) => {
    const found = (lqg_stain_type.value ?? []).find((d: any) => d.value === value);
    return { label: found?.label ?? value, value };
  })
);

const markerExprOptions = computed(() =>
  (lqg_marker_expr.value ?? []).map((d: any) => ({ label: d.label, value: d.value }))
);

/** 选了「其他」才出具体名称输入框 */
const hasOther = computed(() => hasOtherStain(form.value.stainTypes));

/** 所挂样本是否已核验有效（核验抽屉的按钮前置条件） */
const sampleOk = computed(() => sampleVerified(form.value));

const sampleStatusText = computed(() => {
  const found = (lqg_verify_status.value ?? []).find((d: any) => d.value === form.value.sampleVerifyStatus);
  return found?.label ?? form.value.sampleVerifyStatus ?? '—';
});

const rules = computed<ElFormRules>(() => ({
  sampleId: mode.value === 'create' ? [{ required: true, message: t('lqg.embed.drawer.sampleRequired'), trigger: 'change' }] : [],
  // 石蜡块编号：内部新增必填；核验判有效必填（提交前再断一次）
  paraffinBlockNo:
    mode.value === 'create' ? [{ required: true, message: t('lqg.embed.drawer.paraffinBlockNoRequired'), trigger: 'blur' }] : []
}));

const invalidRules: ElFormRules = {
  reason: [{ required: true, message: t('lqg.embed.drawer.invalidReasonRequired'), trigger: 'blur' }]
};

// ── 打开 ────────────────────────────────────────────────────────────────────

const openAdd = () => {
  mode.value = 'create';
  form.value = emptyForm();
  markers.value = [];
  sampleOptions.value = [];
  visible.value = true;
};

const open = async (row: EmbedVO) => {
  mode.value = isExternalPending(row) ? 'verify' : 'edit';
  visible.value = true;
  loading.value = true;
  try {
    const res = await getEmbed(row.id);
    const detail = (res.data ?? row) as EmbedVO;
    form.value = { ...emptyForm(), ...detail };
    markers.value = (detail.markers ?? []).map((m) => ({ markerName: m.markerName ?? null, expression: m.expression ?? null }));
    if (markers.value.length === 0) {
      addMarker();
    }
  } catch {
    proxy?.$modal.msgError(t('lqg.embed.drawer.loadFailed'));
  } finally {
    loading.value = false;
  }
};

const handleClosed = () => {
  form.value = emptyForm();
  markers.value = [];
  invalidDialog.visible = false;
  invalidDialog.reason = '';
};

// ── 选样本（远程搜索，只列已核验有效的样本） ─────────────────────────────────

const searchSamples = async (keyword: string) => {
  sampleSearching.value = true;
  try {
    const res = await listSamples({ verifyStatus: 'valid', internalNo: keyword || null, pageNum: 1, pageSize: 20 });
    sampleOptions.value = (res.rows ?? []) as SampleVO[];
  } finally {
    sampleSearching.value = false;
  }
};

const sampleLabel = (sample: SampleVO) => [sample.internalNo, sample.submitNo].filter(Boolean).join(' · ');

/** 选样本后带出组织收样时间 / 组织处理时间（后端也会兜底带出，这里只是让用户先看到） */
const handleSampleChange = (sampleId: string | number) => {
  const sample = sampleOptions.value.find((s) => String(s.id) === String(sampleId));
  if (!sample) {
    return;
  }
  form.value.tissueReceiveTime = form.value.tissueReceiveTime || sample.receiveDate || null;
  form.value.tissueProcessTime = form.value.tissueProcessTime || (sample.processTime ? String(sample.processTime).slice(0, 10) : null);
};

// ── 染色 / marker ───────────────────────────────────────────────────────────

/**
 * ★ 唯一入口：所有染色切换都过 toggleStain（NONE 与其余互斥，fixture 单测覆盖）。
 *
 * SegButtons 的多选 `update:modelValue` 带回的是**整份新数组**，而 `toggleStain` 是
 * 「一次点击」的语义（点已选中项 = 取消）。两者的差集恰好是这一次点的那一个值，
 * 所以按 diff 逐个过 toggleStain —— 不再自己写一遍互斥分支（写两遍就迟早不一致）。
 */
const handleStainChange = (next: string | number | Array<string | number> | null) => {
  const values = (Array.isArray(next) ? next : next === null || next === '' ? [] : [next]).map(String);
  const current = (form.value.stainTypes ?? []) as string[];
  const changed = [...current.filter((v) => !values.includes(v)), ...values.filter((v) => !current.includes(v))];
  form.value.stainTypes = changed.reduce<string[]>((acc, value) => toggleStain(acc, value), current);
  if (!hasOtherStain(form.value.stainTypes)) {
    form.value.stainOther = null;
  }
};

const addMarker = () => {
  markers.value.push({ markerName: null, expression: null });
};

const removeMarker = (index: number) => {
  markers.value.splice(index, 1);
};

// ── 提交 ────────────────────────────────────────────────────────────────────

const payload = (): EmbedForm => {
  const f = form.value;
  const body: EmbedForm = {
    sampleId: f.sampleId ?? null,
    paraffinBlockNo: f.paraffinBlockNo ?? null,
    sampleType: f.sampleType ?? null,
    organoidSourceType: f.organoidSourceType ?? null,
    tissueReceiveTime: f.tissueReceiveTime ?? null,
    tissueProcessTime: f.tissueProcessTime ?? null,
    agaroseEmbedTime: f.agaroseEmbedTime ?? null,
    embedBy: f.embedBy ?? null,
    dehydrateTime: f.dehydrateTime ?? null,
    agaroseSendTime: f.agaroseSendTime ?? null,
    paraffinEmbedTime: f.paraffinEmbedTime ?? null,
    sectionTime: f.sectionTime ?? null,
    stainTypes: f.stainTypes ?? [],
    stainOther: f.stainOther ?? null,
    operatorName: f.operatorName ?? null,
    remark: f.remark ?? null
  };
  processFields.forEach((field: ProcessField) => {
    body[field] = (f[field] as string | null) ?? null;
  });
  // markers 传了就整组替换（后端语义）；空数组 = 清空，所以只有非空时才带
  body.markers = markers.value
    .filter((m) => (m.markerName ?? '').trim() || (m.expression ?? ''))
    .map((m) => ({ markerName: (m.markerName ?? '').trim() || null, expression: m.expression ?? null }));
  return body;
};

const validateStain = (): boolean => {
  const problem = stainProblem(form.value.stainTypes, form.value.stainOther);
  if (problem) {
    proxy?.$modal.msgWarning(t('lqg.embed.drawer.' + problem));
    return false;
  }
  return true;
};

const submitSave = async () => {
  if (!(await formRef.value?.validate().then(() => true).catch(() => false))) {
    return;
  }
  if (!validateStain()) {
    return;
  }
  submitting.value = true;
  try {
    if (mode.value === 'create') {
      await addEmbed(payload());
    } else {
      await updateEmbed({ ...payload(), id: form.value.id ?? null });
    }
    proxy?.$modal.msgSuccess(t('lqg.embed.drawer.saved'));
    visible.value = false;
    emit('saved');
  } finally {
    submitting.value = false;
  }
};

/**
 * 核验前把抽屉里填的工序 / 染色 / marker 一起存掉（`PUT /lqg/embed` 允许 pending / invalid 之外的状态；
 * 这里先存再核验，用户填的东西不会因为核验请求只收三个键而丢）。
 */
const saveBeforeVerify = async () => {
  if (mode.value !== 'verify' || !form.value.id) {
    return;
  }
  await updateEmbed({ ...payload(), id: form.value.id });
};

const submitValid = async () => {
  if (!(form.value.paraffinBlockNo ?? '').trim()) {
    proxy?.$modal.msgWarning(t('lqg.embed.drawer.paraffinBlockNoRequired'));
    return;
  }
  if (!validateStain()) {
    return;
  }
  submitting.value = true;
  try {
    await saveBeforeVerify();
    await verifyEmbed(form.value.id as string | number, {
      action: 'valid',
      paraffinBlockNo: (form.value.paraffinBlockNo ?? '').trim()
    });
    proxy?.$modal.msgSuccess(t('lqg.embed.drawer.verifiedValid'));
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
    submitting.value = true;
    try {
      await saveBeforeVerify();
      await verifyEmbed(form.value.id as string | number, { action: 'invalid', reason: invalidDialog.reason });
      proxy?.$modal.msgSuccess(t('lqg.embed.drawer.verifiedInvalid'));
      invalidDialog.visible = false;
      visible.value = false;
      emit('saved');
    } finally {
      submitting.value = false;
    }
  });
};

defineExpose({ openAdd, open });
</script>

<style scoped lang="scss">
.lqg-embed-drawer {
  .lqg-embed-drawer__section {
    margin: 8px 0 12px;
    padding-left: 8px;
    font-weight: 600;
    color: var(--lqg-ink);
    border-left: 3px solid var(--lqg-primary);
  }
  .lqg-embed-drawer__lastmod {
    margin-bottom: 8px;
    font-size: 12px;
    color: var(--lqg-ink-2);
  }
  .lqg-embed-drawer__control {
    width: 100%;
  }
  .lqg-embed-drawer__mono {
    font-family: var(--lqg-font-mono);
  }
  .lqg-embed-drawer__mono-input :deep(.el-input__inner) {
    font-family: var(--lqg-font-mono);
  }
  .lqg-embed-drawer__muted,
  .lqg-embed-drawer__hint {
    color: var(--lqg-ink-3);
    font-size: 12px;
  }
  .lqg-embed-drawer__loading {
    padding: 24px 0;
    color: var(--lqg-ink-3);
  }
  .lqg-embed-drawer__stack,
  .lqg-embed-drawer__markers {
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: 100%;
  }
  .lqg-embed-drawer__marker-row {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
  .lqg-embed-drawer__marker-name {
    width: 180px;
  }
  .lqg-embed-drawer__footer {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 8px;
    flex-wrap: wrap;
  }
}
</style>
