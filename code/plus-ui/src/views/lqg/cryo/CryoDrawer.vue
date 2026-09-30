<template>
  <el-drawer v-model="visible" :title="title" size="700px" class="lqg-drawer-el" append-to-body :close-on-click-modal="true" @closed="handleClosed">
    <div v-if="loading" class="lqg-cryo-drawer__muted">{{ t('lqg.cryo.loading') }}</div>

    <template v-else>
      <!-- 顶部小字「最后修改：某某 · 时间」—— updateTime 为 null = 从没改过（跨票语义） -->
      <div v-if="mode === 'edit'" class="lqg-cryo-drawer__lastmod">
        <span v-if="neverModified(form)">{{ t('lqg.cryo.drawer.lastModifiedNever') }}</span>
        <span v-else>{{ t('lqg.cryo.drawer.lastModified', { name: form.updateByName || '—', time: form.updateTime }) }}</span>
      </div>

      <!-- ★ 字段先后逐字照甲方 -80 冻存模板（2026-09-24「请参照我发你的模板，理解先后顺序」）：
           冻存时间、冻存样品、冻存数量/支、冻存密度、暂存-80、冻存人、转移至液氮时间、液氮储存位置、备注；
           模板没有的「代数」放在最后。「选择样本」是挂样本用的，放在最前。
           ★ 一个 el-form 包全部字段：以前「存放位置」那一段是第二个没挂 rules 的 el-form，
             液氮储存位置的必填校验根本没跑（只靠后端 400 兜底）。 -->
      <el-form ref="formRef" :model="form" :rules="rules" label-width="170px">
        <div class="lqg-cryo-drawer__section">{{ t('lqg.cryo.drawer.sectionBatch') }}</div>
        <el-row :gutter="12">
          <el-col :span="24">
            <el-form-item :label="t('lqg.cryo.drawer.sample')" prop="sampleId">
              <!-- 新增时远程搜索（只列已核验有效的样本）；编辑时锁定 -->
              <el-select
                v-if="mode === 'create'"
                v-model="form.sampleId"
                remote
                filterable
                reserve-keyword
                :remote-method="searchSamples"
                :loading="sampleSearching"
                :placeholder="t('lqg.cryo.drawer.samplePlaceholder')"
                class="lqg-cryo-drawer__control"
              >
                <el-option v-for="s in sampleOptions" :key="String(s.id)" :label="sampleLabel(s)" :value="s.id" />
              </el-select>
              <span v-else>
                <span class="lqg-cryo-drawer__mono">{{ form.internalNo || '—' }}</span>
                <span class="lqg-cryo-drawer__muted"> · {{ form.submitNo || '—' }}</span>
                <span class="lqg-cryo-drawer__muted"> · {{ t('lqg.cryo.drawer.sampleLocked') }}</span>
              </span>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item :label="t('lqg.cryo.drawer.freezeTime')" prop="freezeTime">
              <el-date-picker v-model="form.freezeTime" type="date" value-format="YYYY-MM-DD" class="lqg-cryo-drawer__control" clearable />
            </el-form-item>
          </el-col>

          <el-col :span="24">
            <el-form-item :label="t('lqg.cryo.drawer.cryoName')" prop="cryoName">
              <el-input
                v-model="form.cryoName"
                :placeholder="t('lqg.cryo.drawer.cryoNamePlaceholder')"
                maxlength="100"
                clearable
                class="lqg-cryo-drawer__mono-input"
              />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item :label="t('lqg.cryo.drawer.initQty')" prop="initQty">
              <div class="lqg-cryo-drawer__stack">
                <el-input-number v-model="form.initQty" :min="1" :precision="0" :step="1" controls-position="right" class="lqg-cryo-drawer__control" />
                <!-- ★ 初始支数可改（CR-20260917-04）；改小到透支后端会 400 并把差额说清楚 -->
                <span class="lqg-cryo-drawer__tip">{{ t('lqg.cryo.drawer.initQtyTip') }}</span>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('lqg.cryo.drawer.density')" prop="density">
              <el-input v-model="form.density" :placeholder="t('lqg.cryo.drawer.densityPlaceholder')" maxlength="50" clearable />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="lqg-cryo-drawer__section">{{ t('lqg.cryo.drawer.sectionStore') }}</div>
        <el-row :gutter="12">
          <el-col :span="24">
            <el-form-item :label="t('lqg.cryo.drawer.inMinus80')" prop="inMinus80">
              <!-- ★ 是 / 否 两个按钮，不是开关（模板写的就是「是 否（按钮）」） -->
              <SegButtons v-model="form.inMinus80" :options="flagOptions" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('lqg.cryo.drawer.frozenBy')">
              <el-input v-model="form.frozenBy" maxlength="50" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('lqg.cryo.drawer.toLn2Time')" prop="toLn2Time">
              <el-date-picker v-model="form.toLn2Time" type="date" value-format="YYYY-MM-DD" class="lqg-cryo-drawer__control" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('lqg.cryo.drawer.ln2Location')" prop="ln2Location">
              <el-input
                v-model="form.ln2Location"
                :placeholder="t('lqg.cryo.drawer.ln2LocationPlaceholder')"
                maxlength="100"
                clearable
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('lqg.cryo.drawer.remark')">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="lqg-cryo-drawer__section">{{ t('lqg.cryo.drawer.sectionExtra') }}</div>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="t('lqg.cryo.drawer.passage')" prop="passage">
              <el-input v-model="form.passage" :placeholder="t('lqg.cryo.drawer.passagePlaceholder')" maxlength="10" clearable />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </template>

    <template #footer>
      <el-button type="primary" :loading="submitting" @click="submit">{{ t('lqg.cryo.drawer.save') }}</el-button>
      <el-button @click="visible = false">{{ t('lqg.cryo.drawer.cancel') }}</el-button>
    </template>
  </el-drawer>
</template>

<script setup name="LqgCryoDrawer" lang="ts">
import SegButtons from '@/components/lqg/SegButtons/index.vue';
import { addBatch, getBatch, neverModified, updateBatch } from '@/api/lqg/cryo';
import type { CryoBatchForm, CryoBatchVO } from '@/api/lqg/cryo';
import { listSamples } from '@/api/lqg/sample';
import type { SampleVO } from '@/api/lqg/sample';
import { failText } from './flow';
import { useI18n } from 'vue-i18n';

/**
 * 冻存批次的新增 / 编辑抽屉（UI:admin.cryo.list：「新增 / 编辑用抽屉；初始支数可改」）。
 *
 * ★ 「暂存-80度超低温冰箱」用 {@link SegButtons}（是 / 否 两个按钮），**不是**开关控件：
 *   模板那一格写的就是「是 否（按钮）」，accept 2 的最后一段会扫 `views/lqg/cryo/*.vue`
 *   里不许出现 Element 的开关标签（SAMPLE-WEB-001 的四个「有无」字段同一条口径——
 *   ★ 连注释里都不能写出那个标签字面量，否则那一段 grep 会命中本文件）。
 * ★ 初始支数可改（CR-20260917-04）：改了之后后端锁批次行、按 flow_time 逐笔重算，
 *   任一步 < 0 就 400 且库里不变；被拒时把后端那句**原话**（如「已取走 N 支…」）弹出来。
 */
const emit = defineEmits<{ (e: 'saved'): void }>();

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { t } = useI18n();

type Mode = 'create' | 'edit';

const visible = ref(false);
const loading = ref(false);
const submitting = ref(false);
const mode = ref<Mode>('create');
const sampleSearching = ref(false);
const sampleOptions = ref<SampleVO[]>([]);
const formRef = ref<ElFormInstance>();

const emptyForm = () => ({
  id: null as string | number | null,
  sampleId: null as string | number | null,
  internalNo: null as string | null,
  submitNo: null as string | null,
  cryoName: null as string | null,
  passage: null as string | null,
  freezeTime: null as string | null,
  initQty: null as number | null,
  density: null as string | null,
  inMinus80: null as string | null,
  frozenBy: null as string | null,
  toLn2Time: null as string | null,
  ln2Location: null as string | null,
  remark: null as string | null,
  updateByName: null as string | null,
  updateTime: null as string | null
});

const form = ref<ReturnType<typeof emptyForm>>(emptyForm());

const title = computed(() => (mode.value === 'create' ? t('lqg.cryo.drawer.addTitle') : t('lqg.cryo.drawer.editTitle')));

/** 「是 / 否」两个按钮：值是落库的 Y / N，标签是中文 */
const flagOptions = computed(() => [
  { label: t('lqg.cryo.flag.yes'), value: 'Y' },
  { label: t('lqg.cryo.flag.no'), value: 'N' }
]);

/** 代数形如 P2（与后端 CryoBalanceChecker.requirePassage 同一条正则） */
const PASSAGE_PATTERN = /^P\d{1,3}$/;

const rules = computed<ElFormRules>(() => ({
  sampleId: mode.value === 'create' ? [{ required: true, message: t('lqg.cryo.drawer.sampleRequired'), trigger: 'change' }] : [],
  cryoName: [{ required: true, message: t('lqg.cryo.drawer.cryoNameRequired'), trigger: 'blur' }],
  passage: [
    { required: true, message: t('lqg.cryo.drawer.passageRequired'), trigger: 'blur' },
    { pattern: PASSAGE_PATTERN, message: t('lqg.cryo.drawer.passageRequired'), trigger: 'blur' }
  ],
  freezeTime: [{ required: true, message: t('lqg.cryo.drawer.freezeTimeRequired'), trigger: 'change' }],
  initQty: [{ required: true, message: t('lqg.cryo.drawer.initQtyRequired'), trigger: 'change' }],
  inMinus80: [{ required: true, message: t('lqg.cryo.drawer.inMinus80Required'), trigger: 'change' }],
  // 直接进液氮或登记了转液氮时间 → 必须有位置（后端 CryoBalanceChecker.requireLn2Location 同判据）
  ln2Location: [
    {
      validator: (_rule: unknown, value: string | null, callback: (error?: Error) => void) => {
        const needsLocation = form.value.inMinus80 === 'N' || !!form.value.toLn2Time;
        if (needsLocation && !(value ?? '').trim()) {
          callback(new Error(t('lqg.cryo.drawer.ln2LocationRequired')));
          return;
        }
        callback();
      },
      trigger: 'blur'
    }
  ]
}));

const openAdd = (presetSample?: SampleVO | null) => {
  mode.value = 'create';
  form.value = emptyForm();
  sampleOptions.value = presetSample ? [presetSample] : [];
  if (presetSample) {
    form.value.sampleId = presetSample.id;
    form.value.internalNo = presetSample.internalNo ?? null;
  }
  visible.value = true;
};

const open = async (row: CryoBatchVO) => {
  mode.value = 'edit';
  visible.value = true;
  loading.value = true;
  try {
    const res = await getBatch(row.id);
    const detail = (res.data ?? row) as CryoBatchVO;
    form.value = {
      ...emptyForm(),
      id: detail.id,
      sampleId: detail.sampleId,
      internalNo: detail.internalNo ?? null,
      submitNo: detail.submitNo ?? null,
      cryoName: detail.cryoName ?? null,
      passage: detail.passage ?? null,
      freezeTime: detail.freezeTime ?? null,
      initQty: detail.initQty ?? null,
      density: detail.density ?? null,
      inMinus80: detail.inMinus80 ?? null,
      frozenBy: detail.frozenBy ?? null,
      toLn2Time: detail.toLn2Time ?? null,
      ln2Location: detail.ln2Location ?? null,
      remark: detail.remark ?? null,
      updateByName: detail.updateByName ?? null,
      updateTime: detail.updateTime ?? null
    };
  } catch (e) {
    proxy?.$modal.msgError(failText(e, t('lqg.cryo.drawer.loadFailed')));
  } finally {
    loading.value = false;
  }
};

const handleClosed = () => {
  form.value = emptyForm();
};

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

const payload = (): CryoBatchForm => ({
  id: form.value.id,
  sampleId: form.value.sampleId,
  cryoName: form.value.cryoName,
  passage: form.value.passage,
  freezeTime: form.value.freezeTime,
  initQty: form.value.initQty,
  density: form.value.density,
  inMinus80: form.value.inMinus80,
  frozenBy: form.value.frozenBy,
  toLn2Time: form.value.toLn2Time,
  ln2Location: form.value.ln2Location,
  remark: form.value.remark
});

const submit = async () => {
  if (!(await formRef.value?.validate().then(() => true).catch(() => false))) {
    return;
  }
  submitting.value = true;
  try {
    if (mode.value === 'create') {
      await addBatch(payload());
    } else {
      await updateBatch(payload());
    }
    proxy?.$modal.msgSuccess(t('lqg.cryo.drawer.saved'));
    visible.value = false;
    emit('saved');
  } catch (e) {
    // ★ 被拒（例如初始支数改到透支）：把后端原话弹出来、不关抽屉、不静默
    proxy?.$modal.msgError(failText(e, t('lqg.cryo.msg.saveFailed')));
  } finally {
    submitting.value = false;
  }
};

defineExpose({ openAdd, open });
</script>

<style scoped lang="scss">
.lqg-cryo-drawer__section {
  margin: 8px 0 12px;
  padding-left: 8px;
  font-weight: 600;
  color: var(--lqg-ink);
  border-left: 3px solid var(--lqg-primary);
}
.lqg-cryo-drawer__lastmod {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--lqg-ink-2);
}
.lqg-cryo-drawer__control {
  width: 100%;
}
.lqg-cryo-drawer__stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
}
.lqg-cryo-drawer__mono {
  font-family: var(--lqg-font-mono);
}
.lqg-cryo-drawer__mono-input :deep(.el-input__inner) {
  font-family: var(--lqg-font-mono);
}
.lqg-cryo-drawer__muted {
  font-size: 12px;
  color: var(--lqg-ink-3);
}
/* 「冻存数量/支」下面那句说明：表单内容区的行高是 32px，原来每折一行就空出一大截（飞书工作台 row16 截图），
   这里按正文行高排，读起来是一段话 */
.lqg-cryo-drawer__tip {
  font-size: 12px;
  line-height: 18px;
  color: var(--lqg-ink-3);
}
</style>
