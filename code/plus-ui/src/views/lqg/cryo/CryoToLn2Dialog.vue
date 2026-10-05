<template>
  <el-dialog v-model="visible" :title="t('lqg.cryo.toLn2.title')" width="480px" class="lqg-dialog-el" append-to-body :close-on-click-modal="false">
    <el-alert type="info" :closable="false" show-icon class="mb8" :title="t('lqg.cryo.toLn2.tip')" />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
      <el-form-item :label="t('lqg.cryo.toLn2.time')" prop="toLn2Time">
        <el-date-picker v-model="form.toLn2Time" type="date" value-format="YYYY-MM-DD" class="lqg-cryo-toln2__control" clearable />
      </el-form-item>
      <el-form-item :label="t('lqg.cryo.toLn2.location')" prop="ln2Location">
        <el-input v-model="form.ln2Location" maxlength="100" clearable />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="submitting" @click="submit">{{ t('lqg.cryo.toLn2.save') }}</el-button>
      <el-button @click="visible = false">{{ t('lqg.cryo.toLn2.cancel') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup name="LqgCryoToLn2Dialog" lang="ts">
import { toLn2 } from '@/api/lqg/cryo';
import type { CryoBatchVO } from '@/api/lqg/cryo';
import { failText } from './flow';
import { useI18n } from 'vue-i18n';

/**
 * 登记转液氮（`PUT /lqg/cryo/batch/{id}/to-ln2`，FLOW:F-CRYO-01.step4）。
 *
 * ★ 保存后批次位置当场变液氮、超期标记与页签数字当场刷新（超期是读时算的，不落标志位）。
 * ★ 时间不得早于冻存时间（后端 400）；位置必填 —— 东西进了液氮罐却没人知道在哪，
 *   比没登记更糟。
 */
const emit = defineEmits<{ (e: 'saved'): void }>();

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { t } = useI18n();

const visible = ref(false);
const submitting = ref(false);
const batch = ref<CryoBatchVO | null>(null);
const formRef = ref<ElFormInstance>();
const form = reactive<{ toLn2Time: string | null; ln2Location: string | null }>({ toLn2Time: null, ln2Location: null });

const rules = computed<ElFormRules>(() => ({
  toLn2Time: [
    { required: true, message: t('lqg.cryo.toLn2.timeRequired'), trigger: 'change' },
    {
      validator: (_rule: unknown, value: string | null, callback: (error?: Error) => void) => {
        // 与后端 requireLn2NotBeforeFreeze 同判据：yyyy-MM-dd 的字符串可以直接比大小
        const freeze = batch.value?.freezeTime ?? null;
        if (value && freeze && value < freeze) {
          callback(new Error(t('lqg.cryo.toLn2.timeRequired')));
          return;
        }
        callback();
      },
      trigger: 'change'
    }
  ],
  ln2Location: [{ required: true, message: t('lqg.cryo.toLn2.locationRequired'), trigger: 'blur' }]
}));

const open = (row: CryoBatchVO) => {
  batch.value = row;
  form.toLn2Time = new Date().toISOString().slice(0, 10);
  form.ln2Location = row?.ln2Location ?? null;
  visible.value = true;
};

const submit = async () => {
  if (!(await formRef.value?.validate().then(() => true).catch(() => false))) {
    return;
  }
  submitting.value = true;
  try {
    await toLn2(batch.value?.id as string | number, {
      toLn2Time: form.toLn2Time as string,
      ln2Location: form.ln2Location as string
    });
    proxy?.$modal.msgSuccess(t('lqg.cryo.toLn2.saved'));
    visible.value = false;
    emit('saved');
  } catch (e) {
    proxy?.$modal.msgError(failText(e, t('lqg.cryo.msg.saveFailed')));
  } finally {
    submitting.value = false;
  }
};

defineExpose({ open });
</script>

<style scoped lang="scss">
.lqg-cryo-toln2__control {
  width: 100%;
}
</style>
