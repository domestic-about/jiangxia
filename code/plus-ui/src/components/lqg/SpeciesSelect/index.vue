<template>
  <!--
    SpeciesSelect · 种属选择（CR-20261009-18）

    甲方：「比如说人啊、鼠兔、移植猪、鸡。但是呢，它还可以添加其他的。」
      * 下拉列字典 lqg_species 的常用值（管理员在「系统管理 → 字典管理」里加了，下拉里就有）；
      * 列表里没有的直接输入、回车就是它（allow-create），存的是去首尾空白后的文字；
      * filter 模式（列表筛选）多一个「未填」：本需求之前录的样本没有种属，按它筛出来补。
  -->
  <el-select
    :model-value="modelValue ?? undefined"
    filterable
    :allow-create="!filter || allowCreateInFilter"
    default-first-option
    clearable
    :disabled="disabled"
    :placeholder="placeholder ?? (filter ? t('lqg.species.filterPlaceholder') : t('lqg.species.placeholder'))"
    class="lqg-species-select"
    @update:model-value="onChange"
  >
    <el-option v-if="filter" :label="t('lqg.species.none')" :value="SPECIES_NONE" />
    <el-option v-for="label in options" :key="label" :label="label" :value="label" />
  </el-select>
</template>

<script setup name="LqgSpeciesSelect" lang="ts">
import { useI18n } from 'vue-i18n';
import { SPECIES_MAX, SPECIES_NONE, normalizeSpecies, speciesOptions } from './species';

const props = withDefaults(
  defineProps<{
    modelValue?: string | null;
    /** 列表筛选用：多一个「未填」，清空 = 全部 */
    filter?: boolean;
    /** 筛选时也允许输入字典外的值（默认允许：手填过的种属也要能筛） */
    allowCreateInFilter?: boolean;
    disabled?: boolean;
    placeholder?: string;
  }>(),
  { modelValue: null, filter: false, allowCreateInFilter: true, disabled: false, placeholder: undefined }
);

const emit = defineEmits<{ (e: 'update:modelValue', value: string | null): void }>();

const { t } = useI18n();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { lqg_species } = toRefs<any>(proxy?.useDict('lqg_species'));

const options = computed(() => speciesOptions(lqg_species.value, props.modelValue));

const onChange = (value: unknown) => {
  const text = normalizeSpecies(value);
  // 超长的手填值截到 50 字（后端同一上限；表单校验另给提示）
  emit('update:modelValue', text && text !== SPECIES_NONE ? Array.from(text).slice(0, SPECIES_MAX).join('') : text);
};
</script>

<style scoped lang="scss">
.lqg-species-select {
  width: 100%;
}
</style>
