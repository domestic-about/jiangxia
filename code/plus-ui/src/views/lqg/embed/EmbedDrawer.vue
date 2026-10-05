<template>
  <div class="lqg-embed-drawer">
    <el-drawer v-model="visible" :title="title" size="780px" class="lqg-drawer-el" append-to-body :close-on-click-modal="false" :before-close="closeGuard.beforeClose" @closed="handleClosed">
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
          <!-- FIX V02b：抽屉里补填的内容随「判为有效并保存」同一次保存（以前显示成可填、实际被静默丢弃） -->
          <div class="lqg-embed-drawer__hint">{{ fillText(locale, 'fillHint') }}</div>
        </el-alert>

        <!-- ① 包埋信息 -->
        <div class="lqg-embed-drawer__section">{{ t('lqg.embed.drawer.sectionEmbed') }}</div>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="180px">
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
        <el-form label-width="180px">
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
        <el-form label-width="180px">
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
        <el-form label-width="180px">
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
          <el-button @click="closeGuard.requestClose(() => (visible = false))">{{ t('lqg.embed.drawer.cancel') }}</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 判无效的原因（外部看得到这句话） -->
    <el-dialog v-model="invalidDialog.visible" :title="t('lqg.embed.drawer.invalidReasonTitle')" width="460px" class="lqg-dialog-el" append-to-body>
      <!-- FIX V02b：判为无效不收实验室补填的那 13 项 —— 抽屉里改过的，这里明说不会保存（不静默丢） -->
      <el-alert
        v-if="invalidDialog.dropped.length"
        type="warning"
        :closable="false"
        show-icon
        class="mb8 lqg-embed-drawer__dropped"
        :title="fillText(locale, 'invalidDropsLab', { fields: invalidDialog.dropped.join(locale === 'en_US' ? ', ' : '、') })"
      />
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
import {
  addEmbed,
  getEmbed,
  neverModified,
  sampleVerified,
  shouldSaveBeforeVerify,
  updateEmbed,
  verifyEmbed,
  isExternalPending
} from '@/api/lqg/embed';
import type { EmbedFillForm, EmbedForm, EmbedMarkerVO, EmbedVO } from '@/api/lqg/embed';
import { listSamples } from '@/api/lqg/sample';
import type { SampleVO } from '@/api/lqg/sample';
import { STAIN_ORDER, hasOtherStain, stainProblem, toggleStain } from './stain';
import { fillText, invalidFill, labChanges } from './verifyFill';
import type { FillLabKey } from './verifyFill';
import { useI18n } from 'vue-i18n';
import { useCloseGuard } from '@/utils/lqgCloseGuard';

const emit = defineEmits<{ (e: 'saved'): void }>();

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { t, locale } = useI18n();
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
/** 判无效弹窗：原因 + 抽屉里改过、却不会随判无效保存的实验室补填项（中文名，FIX V02b） */
const invalidDialog = reactive<{ visible: boolean; reason: string; dropped: string[] }>({ visible: false, reason: '', dropped: [] });
/** 打开抽屉那一刻的补填段（判无效前据此算「改过的实验室补填项」，FIX V02b） */
const fillSnapshot = ref<EmbedFillForm | null>(null);

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

/**
 * 新增。
 *
 * @param presetSample 石蜡包埋页正「只看某个样本」时带进来（2026-09-24 本机验收「新增默认挂这个样本」）：
 *                     下拉里先放好这一项并选中、带出收样 / 处理时间，仍可改选别的样本。
 *                     调用方只传已核验有效的样本（下拉本来就只列有效样本）。
 */
// 有改动时按 ESC / 点 × / 点「取消」先问一句；点遮罩不再关（工作台 UX 测试 WEB-04）
const closeGuard = useCloseGuard(() => form.value, () => visible.value && !loading.value);

const openAdd = (presetSample?: SampleVO | null) => {
  mode.value = 'create';
  form.value = emptyForm();
  markers.value = [];
  sampleOptions.value = presetSample ? [presetSample] : [];
  if (presetSample) {
    form.value.sampleId = presetSample.id;
    handleSampleChange(presetSample.id);
  }
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
    fillSnapshot.value = JSON.parse(JSON.stringify(fillPayload())) as EmbedFillForm;
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
  invalidDialog.dropped = [];
  fillSnapshot.value = null;
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

/**
 * 补填段（15 项，FIX V02b）：抽屉里除所挂样本与石蜡块编号以外的全部内容。
 *
 * ★ 普通保存（payload）与核验时的 `fill` 用的是**这同一份**取值 —— 后端也是同一份规则（EmbedFillWriter），
 *   不会出现「保存存得进、核验存不进」或反过来。
 */
const fillPayload = (): EmbedFillForm => {
  const f = form.value;
  const body: EmbedFillForm = {
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
  // markers 传了就整组替换（后端语义）；只带填过的行（名称或表达有一个就算），全空 = 清空
  body.markers = markers.value
    .filter((m) => (m.markerName ?? '').trim() || (m.expression ?? ''))
    .map((m) => ({ markerName: (m.markerName ?? '').trim() || null, expression: m.expression ?? null }));
  return body;
};

/** 普通保存（POST / PUT /lqg/embed）的整份表单 = 所挂样本 + 石蜡块编号 + 补填段 */
const payload = (): EmbedForm => ({
  sampleId: form.value.sampleId ?? null,
  paraffinBlockNo: form.value.paraffinBlockNo ?? null,
  ...fillPayload()
});

/** 实验室补填项的界面名（沿用抽屉里的字段标签） */
const LAB_LABEL_KEY: Partial<Record<FillLabKey, string>> = { stainTypes: 'stain', markers: 'marker' };
const labLabel = (key: FillLabKey) => t('lqg.embed.drawer.' + (LAB_LABEL_KEY[key] ?? key));

const validateStain = (): boolean => {
  const problem = stainProblem(form.value.stainTypes, form.value.stainOther);
  if (problem) {
    proxy?.$modal.msgWarning(t('lqg.embed.drawer.' + problem));
    return false;
  }
  return true;
};

/**
 * 失败时的 toast 文案：**优先用后端那句 msg**，拿不到才退回通用话术。
 *
 * ★ 为什么 catch 里能拿到 msg：`@/utils/request` 的响应拦截器对 500 / 601 走的是
 * `Promise.reject(new Error(msg))`（`e.message` 就是后端原话）；对 400 它是
 * `ElNotification.error(msg)` + `Promise.reject('error')`（原话已经以通知弹过一次），
 * 这里只剩通用话术可用。两条路都**不许静默**——静默失败正是 issue #145 的第二个症状。
 */
const failText = (e: unknown, fallback: string): string => {
  const msg = e instanceof Error ? e.message : typeof e === 'string' ? e : '';
  return msg && msg !== 'error' ? msg : fallback;
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
  } catch (e) {
    // ★ 失败：toast 出后端 msg、**不关抽屉**、把 rejection 就地吃掉（不再漏成 pageerror）
    proxy?.$modal.msgError(failText(e, t('lqg.embed.msg.saveFailed')));
  } finally {
    submitting.value = false;
  }
};

/**
 * 核验 / 改判前的调用顺序 —— ★ issue #145（S0）的病灶就在这一句上。
 *
 * 1. 待核验 / 无效的记录**不能**先走普通保存：后端 `EmbedService.update` 对这两个状态一律 400
 *    「核验与改判只走 PUT /lqg/embed/{id}/verify」，预保存必被拒、异常会在 `verifyEmbed()`
 *    之前抛出，核验请求永远发不出去（旧代码的 `saveBeforeVerify()` 就是这样，判有效 / 判无效
 *    两条路都走不通）。所以核验动作的必填项**随 verify 请求一起送**。
 * 2. 只有记录本身允许普通保存时（`shouldSaveBeforeVerify`，判据 = 后端 `editable`
 *    = `verifyStatus === 'valid'`）才保留「先保存再核验」的顺序。核验抽屉打开的都是
 *    `isExternalPending` 的行（pending / invalid）→ 这里恒为 false；留这条分支是给
 *    「已生效记录的改判」这类入口用的：那种情况下抽屉里补填的工序 / 染色才存得进去。
 * 3. 核验抽屉里补填的内容**随 verify 请求的 `fill` 一起送**（FIX V02b / issue #147）：以前
 *    `EmbedVerifyBo` 只收 action / paraffinBlockNo / reason，工序 / 染色 / marker 等显示成可填、
 *    实际被静默丢弃。现在后端把 `fill` 与核验结论拼进同一条 UPDATE（规则与普通保存同一份）：
 *    「判为有效并保存」带整份补填段；「判为无效」只带样本类型、类器官来源类型（实验室补填的
 *    13 项在核验有效后才补填，改过的会在原因弹窗里明说不保存）。这里**仍然不**先发普通保存。
 */
const preSaveIfEditable = async () => {
  if (mode.value !== 'verify' || !form.value.id || !shouldSaveBeforeVerify(form.value)) {
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
    await preSaveIfEditable();
    // ★ 待核验的外部送样直接走 /verify，判有效的必填项（石蜡块编号）随这一次请求一起送；
    //   抽屉里补填的内容作为 fill 一并送（FIX V02b：以前被静默丢弃），后端同一事务保存
    await verifyEmbed(form.value.id as string | number, {
      action: 'valid',
      paraffinBlockNo: (form.value.paraffinBlockNo ?? '').trim(),
      fill: fillPayload()
    });
    proxy?.$modal.msgSuccess(t('lqg.embed.drawer.verifiedValid'));
    visible.value = false;
    emit('saved');
  } catch (e) {
    // ★ 判有效失败：toast 出后端 msg（如「石蜡块编号已存在」「所挂样本还未核验有效」）、不关抽屉
    proxy?.$modal.msgError(failText(e, t('lqg.embed.msg.verifyFailed')));
  } finally {
    submitting.value = false;
  }
};

const openInvalidDialog = () => {
  invalidDialog.reason = '';
  // 抽屉里改过、却不会随判无效保存的实验室补填项：弹窗里明说（FIX V02b，不静默丢）。
  // 没有快照（详情没读成功）就按「打开时全空」比，填了的一律算改过 —— 宁可多提示，不许漏
  invalidDialog.dropped = labChanges(fillSnapshot.value ?? {}, fillPayload()).map(labLabel);
  invalidDialog.visible = true;
};

const submitInvalid = () => {
  invalidRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    submitting.value = true;
    try {
      await preSaveIfEditable();
      // ★ 与判有效同一条路：直接调 /verify，原因随这次请求一起送（不再先做必被拒的普通保存）；
      //   样本类型、类器官来源类型的更正作为 fill 一并保存（FIX V02b），实验室补填项判无效不收
      await verifyEmbed(form.value.id as string | number, {
        action: 'invalid',
        reason: invalidDialog.reason,
        fill: invalidFill(fillPayload())
      });
      proxy?.$modal.msgSuccess(t('lqg.embed.drawer.verifiedInvalid'));
      invalidDialog.visible = false;
      visible.value = false;
      emit('saved');
    } catch (e) {
      // ★ 判无效失败：原因弹窗与抽屉都留着，让人能改完再点；错误必须看得见
      proxy?.$modal.msgError(failText(e, t('lqg.embed.msg.verifyFailed')));
    } finally {
      submitting.value = false;
    }
  });
};

defineExpose({ openAdd, open });
</script>

<style scoped lang="scss">
/* ★ 2026-09-30：原来整块嵌在 `.lqg-embed-drawer { … }` 里 —— 抽屉 append-to-body 被挪到 <body> 下，
   .lqg-embed-drawer 不再是抽屉内容的祖先，嵌套选择器一条都命不中。
   scoped 的 data-v 属性仍在插槽内容上，所以去掉外层包裹、直接写类名即可。 */
.lqg-embed-drawer__section {
  margin: 8px 0 12px;
  padding-left: 8px;
  font-weight: 600;
  color: var(--lqg-ink);
  border-left: 3px solid var(--lqg-primary);
}
.lqg-embed-drawer__lastmod {
  margin-bottom: 14px;
  font-size: 12px;
  color: var(--lqg-ink-3);
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
</style>
