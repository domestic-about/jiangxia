<template>
  <!--
    样本总表的「切片染色提示」（SAMPLE-HINT-001 / UI:admin.sample.list.hint / FLOW:F-SAMPLE-02.step4）。

    ★ 三个口径都落在这里：
      1) 数据由后端**读时算**（`/lqg/sample/list` 每行的 `hint`），本组件不自己数块、不落库；
      2) 没有包埋记录显示「—」（`blockCount === 0`，后端给的是零值不是 null）；
      3) 悬停**再查一次**石蜡块明细（编号 + 切片时间）——列表接口不带明细，只有 hint 的汇总；
         点整个徽标组 → 带 `sampleId` 跳到工作台「石蜡包埋」页并按该样本过滤。
  -->
  <span class="lqg-hint">
    <span v-if="!hasBlocks" class="lqg-hint__none">{{ t('lqg.sample.hint.none') }}</span>
    <el-popover
      v-else
      :width="260"
      trigger="hover"
      placement="top-start"
      popper-class="lqg-hint__popper"
      @show="loadBlocks"
    >
      <template #reference>
        <span
          class="lqg-hint__badges"
          role="button"
          tabindex="0"
          :title="t('lqg.sample.hint.open')"
          @click="openEmbed"
        >
          <span class="lqg-hint__badge">{{ t('lqg.sample.hint.block', { n: hint?.blockCount ?? 0 }) }}</span>
          <span v-if="hint?.sectioned" class="lqg-hint__badge lqg-hint__badge--sectioned">
            {{ t('lqg.sample.hint.sectioned') }}
          </span>
          <span v-for="kind in stains" :key="kind" class="lqg-hint__badge lqg-hint__badge--stain">
            {{ stainLabel(kind) }}
          </span>
        </span>
      </template>

      <div class="lqg-hint__detail">
        <div class="lqg-hint__detail-head">
          <span>{{ t('lqg.sample.hint.blockNo') }}</span>
          <span>{{ t('lqg.sample.hint.sectionTime') }}</span>
        </div>
        <div v-if="loading" class="lqg-hint__muted">{{ t('lqg.sample.hint.loading') }}</div>
        <div v-else-if="failed" class="lqg-hint__muted">{{ t('lqg.sample.hint.loadFailed') }}</div>
        <div v-else-if="!blocks.length" class="lqg-hint__muted">{{ t('lqg.sample.hint.empty') }}</div>
        <template v-else>
          <div v-for="block in blocks" :key="String(block.id)" class="lqg-hint__detail-row">
            <span class="lqg-hint__mono">{{ block.paraffinBlockNo || t('lqg.sample.hint.none') }}</span>
            <span class="lqg-hint__mono">{{ block.sectionTime || t('lqg.sample.hint.none') }}</span>
          </div>
        </template>
      </div>
    </el-popover>
  </span>
</template>

<script setup name="LqgSampleHintBadges" lang="ts">
import { listEmbeds } from '@/api/lqg/embed';
import type { EmbedVO } from '@/api/lqg/embed';
import type { SampleHintVO } from '@/api/lqg/sample';
import { useI18n } from 'vue-i18n';

/**
 * 染色 → 徽标缩写（UI:admin.sample.list.hint：「做过的染色缩写（HE / IF / IHC / 其他）」）。
 *
 * ★ 值域 = 字典 `lqg_stain_type`（`HE / IF / IHC / OTHER / NONE`）。`NONE` 由后端
 *   `StainHintRules.union` 在并集里去掉 —— 它到不了这里，所以这张表里也没有它。
 *   字典外的历史脏值按原值带出（不吞掉，便于发现数据问题）。
 */
const STAIN_ABBR: Record<string, string> = { HE: 'HE', IF: 'IF', IHC: 'IHC', OTHER: '其他' };

const props = defineProps<{
  /** 后端读时算出来的提示（没有包埋记录时是零值对象，不是 null） */
  hint?: SampleHintVO | null;
  /** 点徽标跳石蜡包埋页时带的样本 id */
  sampleId?: string | number | null;
}>();

const { t } = useI18n();
const router = useRouter();

const hasBlocks = computed(() => (props.hint?.blockCount ?? 0) > 0);
const stains = computed<string[]>(() => props.hint?.stains ?? []);

const stainLabel = (value: string) => STAIN_ABBR[value] ?? value;

const blocks = ref<EmbedVO[]>([]);
const loading = ref(false);
const failed = ref(false);

/**
 * 悬停时**再查**石蜡块明细（编号 + 切片时间）。
 *
 * ★ 带 `verifyStatus=valid`：与徽标上的块数**同一口径**（软删 / 待核验 / 无效的块不算），
 *   否则会出现「徽标写 2 块、悬停列出 3 行（其中一行没有石蜡块编号）」的对不上。
 * ★ 失败（含没有 `lqg:embed:list` 权限时的 403）只把浮层文案换成「没能加载」，
 *   不让整张总表跟着报错。
 */
const loadBlocks = async () => {
  if (!props.sampleId) {
    return;
  }
  loading.value = true;
  failed.value = false;
  try {
    const res: any = await listEmbeds({ sampleId: props.sampleId, verifyStatus: 'valid', pageSize: 100 });
    blocks.value = (res?.rows ?? []) as EmbedVO[];
  } catch {
    failed.value = true;
    blocks.value = [];
  } finally {
    loading.value = false;
  }
};

/** 点击带 sampleId 跳石蜡包埋页（路径 = 菜单 5310 的 path `embed`，不是 /lqg/embed）。 */
const openEmbed = () => {
  if (!props.sampleId) {
    return;
  }
  router.push({ path: '/embed', query: { sampleId: String(props.sampleId) } });
};

// 换了一页 / 换了筛选之后，明细跟着这一行的样本走：先把上一条的缓存丢掉，免得悬停时看到别人的块
watch(
  () => props.sampleId,
  () => {
    blocks.value = [];
    failed.value = false;
  }
);
</script>

<style scoped lang="scss">
.lqg-hint {
  display: inline-flex;
  align-items: center;

  .lqg-hint__none {
    color: var(--lqg-ink-3);
  }

  .lqg-hint__badges {
    display: inline-flex;
    flex-wrap: wrap;
    gap: 4px;
    align-items: center;
    cursor: pointer;
  }

  .lqg-hint__badge {
    display: inline-block;
    padding: 0 6px;
    border: 1px solid var(--lqg-line);
    border-radius: 9px;
    background: var(--lqg-bg);
    color: var(--lqg-ink-2);
    font-size: 12px;
    line-height: 18px;
    white-space: nowrap;
  }

  // 「已切片」= 已发生的工序 → 用 ok 语义色（与字典值一一对应的那一组）
  .lqg-hint__badge--sectioned {
    border-color: var(--lqg-ok);
    background: var(--lqg-ok-soft);
    color: var(--lqg-ok);
  }

  // 染色缩写 → 主色（不是状态，只是一个标签）
  .lqg-hint__badge--stain {
    border-color: var(--lqg-primary);
    background: var(--lqg-primary-soft);
    color: var(--lqg-primary);
  }
}
</style>

<!--
  浮层被 Element Plus **teleport 到 body**，scoped 属性到不了它，所以这一段不 scoped
  —— 选择器全部挂在 `.lqg-hint__popper` 这个前缀下（popper-class 传进去的），不会外溢。
-->
<style lang="scss">
.lqg-hint__popper {
  .lqg-hint__detail {
    font-size: 12px;
    color: var(--lqg-ink);
  }

  .lqg-hint__detail-head,
  .lqg-hint__detail-row {
    display: flex;
    justify-content: space-between;
    gap: 8px;
  }

  .lqg-hint__detail-head {
    padding-bottom: 4px;
    border-bottom: 1px solid var(--lqg-line);
    color: var(--lqg-ink-3);
  }

  .lqg-hint__detail-row {
    padding-top: 4px;
  }

  .lqg-hint__muted {
    padding-top: 6px;
    color: var(--lqg-ink-3);
  }

  .lqg-hint__mono {
    font-family: var(--lqg-font-mono);
  }
}
</style>
