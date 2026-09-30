<template>
  <el-drawer
    :model-value="modelValue"
    :title="t('lqg.home.issues.title')"
    size="1000px"
    direction="rtl"
    append-to-body
    class="lqg-issues lqg-drawer-el"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <div class="lqg-issues__sub">{{ t('lqg.home.issues.subtitle') }}</div>
    <el-alert
      v-if="loadError"
      type="warning"
      :closable="false"
      show-icon
      class="mb-2"
      :title="t('lqg.home.issues.loadFailed', { msg: loadError })"
    />
    <el-table v-loading="loading" :data="rows" size="small" border row-key="key" class="lqg-issues__table" @row-click="openQc">
      <el-table-column :label="t('lqg.home.issues.colSample')" width="150">
        <template #default="{ row }">
          <div class="lqg-issues__mono">{{ row.internalNo || row.submitNo || row.sampleId }}</div>
          <div class="lqg-issues__minor">{{ row.sourceUnitName || '' }}</div>
        </template>
      </el-table-column>
      <el-table-column :label="t('lqg.home.issues.colDoc')" width="130">
        <template #default="{ row }">{{ docLabel(row.docKind) }}</template>
      </el-table-column>
      <el-table-column :label="t('lqg.home.issues.colAudience')" width="80">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" :type="row.audience === 'external' ? 'warning' : 'info'">
            {{ row.audience === 'external' ? t('lqg.home.issues.external') : t('lqg.home.issues.internal') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('lqg.home.issues.colIssue')" min-width="260">
        <template #default="{ row }">
          <el-tag size="small" :type="row.issue === 'failed' ? 'danger' : 'warning'" effect="light">
            {{ row.issue === 'failed' ? t('lqg.home.issues.failed') : t('lqg.home.issues.missing', { n: row.missingImageCount || 0 }) }}
          </el-tag>
          <div class="lqg-issues__reason">{{ row.issue === 'failed' ? row.errorMsg : row.missingImages }}</div>
        </template>
      </el-table-column>
      <el-table-column :label="t('lqg.home.issues.colTime')" prop="time" width="165" />
      <el-table-column :label="t('lqg.home.issues.colAction')" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click.stop="openQc(row)">{{ t('lqg.home.issues.openQc') }}</el-button>
          <el-button link type="primary" size="small" :loading="busyKey === row.key" @click.stop="regenerate(row)">
            {{ t('lqg.home.issues.regenerate') }}
          </el-button>
        </template>
      </el-table-column>
      <template #empty>{{ loadError ? '' : t('lqg.home.issues.empty') }}</template>
    </el-table>
  </el-drawer>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { getRenderIssues, type HomeRenderIssueVO } from '@/api/lqg/home';
import { renderDoc } from '@/api/lqg/doc';

// ============================================================================
// 首页「文档渲染失败 / 缺图」清单（独立验收 V29）
//
// ★ 数据 = GET /lqg/home/render-issues：与卡片上的 renderFailed 同一个口径（渲染失败 + 内部版缺图），
//   所以卡片数字 == 这里的行数。
// ★ 点一行 → 进该样本的质控文档页（带上页签与版本，右栏预览面板直接显示失败原因 / 缺了哪几张）；
//   「重新生成」= render + force（一定重出，不会被缓存命中空转），完成后刷新清单与首页数字。
// ============================================================================

const props = defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'changed'): void }>();

const { t } = useI18n();
const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

type Row = HomeRenderIssueVO & { key: string };

const rows = ref<Row[]>([]);
const loading = ref(false);
const loadError = ref('');
const busyKey = ref('');

const DOC_TAB: Record<string, string> = {
  sample_qc: 'sample-qc',
  organoid_qc: 'organoid-qc',
  organoid_score: 'score',
  merged: 'sample-qc'
};

const docLabel = (kind: string) => {
  if (kind === 'sample_qc') return t('lqg.home.issues.docSampleQc');
  if (kind === 'organoid_qc') return t('lqg.home.issues.docOrganoidQc');
  if (kind === 'organoid_score') return t('lqg.home.issues.docScore');
  if (kind === 'merged') return t('lqg.home.issues.docMerged');
  return kind;
};

const load = async () => {
  loading.value = true;
  try {
    const res = await getRenderIssues();
    rows.value = (res.data || []).map((r) => ({ ...r, key: `${r.sampleId}/${r.docKind}/${r.audience}` }));
    loadError.value = '';
  } catch (e: any) {
    // 拉不到就说清楚，不要静默当成「没有异常」
    loadError.value = e?.message || String(e);
  } finally {
    loading.value = false;
  }
};

/** 进该样本的质控文档页（页签 = 这份文档；预览面板切到出问题的那一版） */
const openQc = (row: Row) => {
  emit('update:modelValue', false);
  router.push({
    path: '/qc-console/qc-editor',
    query: { sampleId: String(row.sampleId), tab: DOC_TAB[row.docKind] || 'sample-qc', audience: row.audience }
  });
};

/** 一键重新生成（force）：成功就从清单里消失；仍失败 / 仍缺图就把新的原因摆出来 */
const regenerate = async (row: Row) => {
  busyKey.value = row.key;
  try {
    const res = await renderDoc(row.sampleId, row.docKind, row.audience, true);
    const data = res.data || {};
    if (data.status === 'failed') {
      proxy?.$modal.msgError(t('lqg.home.issues.stillFailed', { msg: data.errorMsg || '' }));
    } else if ((data.missingImageCount || 0) > 0) {
      proxy?.$modal.msgWarning(t('lqg.home.issues.stillMissing', { n: data.missingImageCount }));
    } else {
      proxy?.$modal.msgSuccess(t('lqg.home.issues.regenerated'));
    }
  } catch (e: any) {
    proxy?.$modal.msgError(e?.message || String(e));
  } finally {
    busyKey.value = '';
    await load();
    emit('changed');
  }
};

watch(
  () => props.modelValue,
  (open) => {
    if (open) load();
  }
);
</script>

<style scoped lang="scss">
.lqg-issues__sub {
  margin: -8px 0 10px;
  font-size: 12px;
  color: var(--lqg-ink-3);
}
.lqg-issues__table :deep(.el-table__row) {
  cursor: pointer;
}
.lqg-issues__mono {
  font-family: var(--lqg-font-mono);
  color: var(--lqg-ink);
}
.lqg-issues__minor {
  font-size: 12px;
  color: var(--lqg-ink-3);
}
.lqg-issues__reason {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--lqg-ink-2);
  word-break: break-all;
}
</style>
