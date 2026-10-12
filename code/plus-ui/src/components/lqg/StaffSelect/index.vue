<template>
  <!--
    StaffSelect · 选人（CR-20261012-22）

    操作人 / 包埋人 / 冻存人从内部人员名单里选（甲方要按人统计工作量，手填的名字「刘佳」「刘 佳」会被算成两个人）。
      * 表单用法：v-model:id + v-model:name —— 账号 id 与姓名是一对，选了谁两个一起变；
        名单里没有的人选不了：请管理员到「人员与单位 → 内部人员授权」里先加上。
        历史记录上手填的姓名（没关联账号）、已撤销授权的人，下拉里看得见（带标注），不改就原样留着。
      * 筛选用法（filter）：v-model 是「按人筛」的 key（u:<账号 id> / n:<姓名> / none），多一个「未填」；
        可以直接输入名单外的名字 —— 用来把历史手填的记录筛出来补关联。
  -->
  <el-select
    :model-value="current ?? undefined"
    filterable
    clearable
    :allow-create="filter"
    default-first-option
    :disabled="disabled"
    :placeholder="placeholder ?? (filter ? t('lqg.person.filterPlaceholder') : t('lqg.person.placeholder'))"
    :no-data-text="t('lqg.person.empty')"
    class="lqg-staff-select"
    @update:model-value="onChange"
    @visible-change="onVisible"
  >
    <el-option v-if="filter" :label="t('lqg.person.none')" :value="PERSON_NONE" />
    <el-option v-for="choice in choices" :key="choice.value" :label="choice.label" :value="choice.value" />
  </el-select>
</template>

<script setup name="LqgStaffSelect" lang="ts">
import { useI18n } from 'vue-i18n';
import { PERSON_NONE, filterChoices, filterKey, personValue, pickPerson, staffChoices } from './staff';
import { useStaff } from './useStaff';

const props = withDefaults(
  defineProps<{
    /** 筛选用法的值：按人筛的 key */
    modelValue?: string | null;
    /** 表单用法：账号 id */
    id?: string | number | null;
    /** 表单用法：姓名；筛选用法：现值不在名单里时显示用的姓名 */
    name?: string | null;
    /** 列表筛选用：多一个「未填」，可输入名单外的名字，清空 = 全部 */
    filter?: boolean;
    disabled?: boolean;
    placeholder?: string;
  }>(),
  { modelValue: null, id: null, name: null, filter: false, disabled: false, placeholder: undefined }
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | null): void;
  (e: 'update:id', value: string | null): void;
  (e: 'update:name', value: string | null): void;
  /** 表单用法：选的人变了（两个 v-model 都更新之后） */
  (e: 'change', value: { id: string | null; name: string | null }): void;
}>();

const { t } = useI18n();
const { staff, load } = useStaff();

const labels = computed(() => ({ unlinked: t('lqg.person.unlinked'), gone: t('lqg.person.gone') }));

const current = computed(() => (props.filter ? (props.modelValue ?? null) : personValue(props.id, props.name)));

const choices = computed(() =>
  props.filter ? filterChoices(staff.value, props.modelValue, labels.value, props.name) : staffChoices(staff.value, props.id, props.name, labels.value)
);

const onChange = (value: unknown) => {
  if (props.filter) {
    emit('update:modelValue', filterKey(value, staff.value));
    return;
  }
  const picked = pickPerson(value, staff.value, { id: props.id == null ? null : String(props.id), name: props.name ?? null });
  emit('update:id', picked.id);
  emit('update:name', picked.name);
  emit('change', picked);
};

/** 每次点开下拉都看一眼名单要不要重取（管理员刚加的人马上选得到） */
const onVisible = (visible: boolean) => {
  if (visible) {
    load();
  }
};

onMounted(() => {
  load();
});
</script>

<style scoped lang="scss">
.lqg-staff-select {
  width: 100%;
}
</style>
