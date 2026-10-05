<template>
  <!-- 表格空态：区分「真没有数据」和「没加载出来」（工作台 UX 测试 WEB-06：接口出错时原来也显示「没有符合条件的…」，
       用户会以为样本丢了或自己筛错了） -->
  <div class="lqg-table-empty">
    <template v-if="error">
      <span class="lqg-table-empty__error">{{ t('lqg.ux.tableEmpty.failed') }}</span>
      <el-button link type="primary" @click="emit('retry')">{{ t('lqg.ux.tableEmpty.retry') }}</el-button>
    </template>
    <span v-else>{{ text }}</span>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n';

defineProps<{
  /** 最近一次取数是不是失败了 */
  error: boolean;
  /** 真没有数据时的那句话（各页原来的 empty-text） */
  text: string;
}>();

const emit = defineEmits<{ (e: 'retry'): void }>();
const { t } = useI18n();
</script>

<style scoped>
.lqg-table-empty {
  line-height: 1.6;
  white-space: normal;
}

.lqg-table-empty__error {
  margin-right: 8px;
  color: var(--el-color-danger);
}
</style>
