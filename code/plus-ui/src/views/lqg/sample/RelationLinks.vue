<template>
  <!--
    样本两页的「石蜡包埋 / 冻存」一列（Kevin 2026-09-24 本机验收「四种表之间的关系看着有点乱」）。

    ★ 以前是「操作」列里的「石蜡包埋」「冻存」两个按钮：点过去是整张列表，看不出筛过、也不知道有几条。
      现在这一列只写这个样本名下有什么、有几条，数字可点（带 sampleId 过去，那边顶部有提示条）：
        蜡块 2  待核验 1      ← 蜡块 = 已核验有效的石蜡块（与切片染色提示同一个数）；
        冻存 1 批               待核验 = 合作单位送来、还没核验的石蜡包埋送样，点了直接筛到它们
      数量为 0 显示「—」；样本已核验有效、有新增权限时再给「新增」，跳过去直接开新增抽屉、样本已选好。
    ★ 怎么画全在 relation.ts 的 relationOf（纯函数，spec 覆盖），这里只管摆。
  -->
  <div class="lqg-rel">
    <div class="lqg-rel__line">
      <el-link
        v-if="cell.blocks.to"
        type="primary"
        underline="never"
        class="lqg-rel__link"
        :title="t('lqg.sample.relation.openBlocks')"
        @click="go(cell.blocks.to)"
      >
        {{ t('lqg.sample.relation.blocks', { n: cell.blocks.count }) }}
      </el-link>
      <span v-else class="lqg-rel__none">
        {{ t('lqg.sample.relation.blocksLabel') }} {{ cell.blocks.count > 0 ? cell.blocks.count : t('lqg.sample.relation.none') }}
      </span>
      <el-link
        v-if="cell.blocks.addTo"
        type="primary"
        underline="never"
        class="lqg-rel__add"
        :title="t('lqg.sample.relation.addBlocks')"
        @click="go(cell.blocks.addTo)"
      >
        {{ t('lqg.sample.relation.add') }}
      </el-link>
      <el-link
        v-if="cell.pending.to"
        type="warning"
        underline="never"
        class="lqg-rel__pending"
        :title="t('lqg.sample.relation.openPending')"
        @click="go(cell.pending.to)"
      >
        {{ t('lqg.sample.relation.pending', { n: cell.pending.count }) }}
      </el-link>
      <span v-else-if="cell.pending.count > 0" class="lqg-rel__pending-text">
        {{ t('lqg.sample.relation.pending', { n: cell.pending.count }) }}
      </span>
    </div>
    <div class="lqg-rel__line">
      <el-link
        v-if="cell.cryo.to"
        type="primary"
        underline="never"
        class="lqg-rel__link"
        :title="t('lqg.sample.relation.openCryo')"
        @click="go(cell.cryo.to)"
      >
        {{ t('lqg.sample.relation.cryo', { n: cell.cryo.count }) }}
      </el-link>
      <span v-else class="lqg-rel__none">
        {{ t('lqg.sample.relation.cryoLabel') }}
        {{ cell.cryo.count > 0 ? t('lqg.sample.relation.cryoCount', { n: cell.cryo.count }) : t('lqg.sample.relation.none') }}
      </span>
      <el-link
        v-if="cell.cryo.addTo"
        type="primary"
        underline="never"
        class="lqg-rel__add"
        :title="t('lqg.sample.relation.addCryo')"
        @click="go(cell.cryo.addTo)"
      >
        {{ t('lqg.sample.relation.add') }}
      </el-link>
    </div>
  </div>
</template>

<script setup name="LqgSampleRelationLinks" lang="ts">
import { checkPermi } from '@/utils/permission';
import type { SampleVO } from '@/api/lqg/sample';
import { relationOf } from './relation';
import type { RouteTarget } from './relation';
import { useI18n } from 'vue-i18n';

const props = defineProps<{
  /** 样本行（用它的 id / verifyStatus / hint.blockCount / relation） */
  row: SampleVO;
}>();

const { t } = useI18n();
const router = useRouter();

// 权限在一次登录里不变：算一次，别每行每次渲染都扫一遍权限表
const perms = {
  embedList: checkPermi(['lqg:embed:list']),
  embedAdd: checkPermi(['lqg:embed:add']),
  cryoList: checkPermi(['lqg:cryo:list']),
  cryoAdd: checkPermi(['lqg:cryo:add'])
};

const cell = computed(() => relationOf(props.row, perms));

/** 用 push（不是 replace）：浏览器后退、标签页都能回到这张样本表 */
const go = (target: RouteTarget) => {
  router.push(target);
};
</script>

<style scoped lang="scss">
.lqg-rel {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 20px;

  .lqg-rel__line {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
  }

  .lqg-rel__link {
    font-size: 13px;
  }

  .lqg-rel__none {
    font-size: 13px;
    color: var(--lqg-ink-3);
  }

  .lqg-rel__add {
    font-size: 12px;
  }

  // 待核验：琥珀色小标签（与待核验行的浅黄底同一组语义色）
  .lqg-rel__pending,
  .lqg-rel__pending-text {
    padding: 0 6px;
    font-size: 12px;
    line-height: 18px;
    border-radius: 9px;
    background: var(--lqg-warn-soft);
    color: var(--lqg-warn);
  }
}
</style>
