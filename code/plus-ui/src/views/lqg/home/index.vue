<template>
  <div class="p-2 lqg-home">
    <el-card shadow="never" class="lqg-home__banner">
      <div class="lqg-home__head">
        <div>
          <div class="lqg-home__title">{{ t('lqg.home.title') }}</div>
          <div class="lqg-home__subtitle">{{ t('lqg.home.subtitle') }}</div>
        </div>
        <el-button :loading="todoStore.loading" @click="reload">{{ t('lqg.home.refresh') }}</el-button>
      </div>
      <el-alert
        v-if="todoStore.error"
        type="warning"
        :closable="false"
        show-icon
        class="mt-2"
        :title="t('lqg.home.loadFailed', { msg: todoStore.error })"
      />
    </el-card>

    <!-- 五张待办卡片（UI:admin.home）：数字全部来自 todoStore 的那一次 /lqg/home/todo，
         侧边菜单角标读的是同一份结果。★ 每张卡片都写出来（不用 v-for）——
         卡片文案、跳转目标、为 0 时的说明各不相同，摊开比一张配置表好读。 -->
    <div class="lqg-home__cards">
      <TodoCard
        :title="t('lqg.home.card.pendingSamples.title')"
        :value="todo.pendingSamples"
        :hint="t('lqg.home.card.pendingSamples.hint')"
        :zero-hint="t('lqg.home.card.pendingSamples.zero')"
        :go-text="t('lqg.home.card.pendingSamples.go')"
        :loading="todoStore.loading"
        :to="{ path: '/sample', query: { verifyStatus: 'pending' } }"
      />
      <TodoCard
        :title="t('lqg.home.card.pendingEmbeds.title')"
        :value="todo.pendingEmbeds"
        :hint="t('lqg.home.card.pendingEmbeds.hint')"
        :zero-hint="t('lqg.home.card.pendingEmbeds.zero')"
        :go-text="t('lqg.home.card.pendingEmbeds.go')"
        :loading="todoStore.loading"
        :to="{ path: '/embed', query: { verifyStatus: 'pending' } }"
      />
      <TodoCard
        :title="t('lqg.home.card.cryoOverdue.title')"
        :value="todo.cryoOverdue"
        :hint="t('lqg.home.card.cryoOverdue.hint')"
        :zero-hint="t('lqg.home.card.cryoOverdue.zero')"
        :go-text="t('lqg.home.card.cryoOverdue.go')"
        :loading="todoStore.loading"
        :to="{ path: '/cryo', query: { overdueOnly: 'true' } }"
      />
      <TodoCard
        :title="t('lqg.home.card.pendingExtUsers.title')"
        :value="todo.pendingExtUsers"
        :hint="t('lqg.home.card.pendingExtUsers.hint')"
        :zero-hint="t('lqg.home.card.pendingExtUsers.zero')"
        :go-text="t('lqg.home.card.pendingExtUsers.go')"
        :loading="todoStore.loading"
        :to="{ path: '/auth/extuser', query: { bindStatus: 'pending' } }"
      />
      <TodoCard
        :title="t('lqg.home.card.renderFailed.title')"
        :value="todo.renderFailed"
        :hint="t('lqg.home.card.renderFailed.hint')"
        :zero-hint="t('lqg.home.card.renderFailed.zero')"
        :go-text="t('lqg.home.card.renderFailed.go')"
        :loading="todoStore.loading"
        :to="{ path: '/qc-console/doc-console' }"
      />
    </div>

    <el-card shadow="never" class="mt-3">
      <template #header>
        <div class="lqg-home__recent-head">
          <span class="lqg-home__recent-title">{{ t('lqg.home.recent.title') }}</span>
          <span class="lqg-home__recent-sub">{{ t('lqg.home.recent.subtitle') }}</span>
        </div>
      </template>

      <el-table v-loading="recentLoading" :data="recent" size="small" border>
        <el-table-column prop="submitTime" :label="t('lqg.home.recent.colSubmitTime')" width="180" />
        <el-table-column prop="submitNo" :label="t('lqg.home.recent.colSubmitNo')" width="140" />
        <el-table-column prop="sourceUnitName" :label="t('lqg.home.recent.colSourceUnit')" min-width="160" show-overflow-tooltip />
        <el-table-column :label="t('lqg.home.recent.colSubmitSource')" width="110">
          <template #default="{ row }">{{ sourceLabel(row.submitSource) }}</template>
        </el-table-column>
        <el-table-column :label="t('lqg.home.recent.colVerifyStatus')" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.verifyStatus)" size="small" effect="light">{{ statusLabel(row.verifyStatus) }}</el-tag>
          </template>
        </el-table-column>
        <template #empty>{{ recentError || t('lqg.home.recent.empty') }}</template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="LqgHome">
import TodoCard from './components/TodoCard.vue';
import { storeToRefs } from 'pinia';
import { useI18n } from 'vue-i18n';
import { getHomeRecent, HomeRecentVO } from '@/api/lqg/home';
import { useLqgTodoStore } from '@/store/modules/lqgTodo';

// ============================================================================
// 工作台首页（SYS-HOME-001 · UI:admin.home）
//
// ★ 五个数字**只从 store 读**（`store/modules/lqgTodo.ts`）：本页面不发 `/lqg/home/todo`
//   请求，侧边菜单角标也读同一个 store —— 两处的数字必然一致（ticket §0.1 硬要求 ③）。
//   accept 2 最后两段断的就是「Sidebar 里出现 lqgTodo、不许出现 home/todo」。
//
// ★ 这里没有图表、没有趋势、没有统计卡（ticket §3 边界：甲方没提，做了就得维护）。
// ============================================================================

const { t } = useI18n();
const todoStore = useLqgTodoStore();
const { todo } = storeToRefs(todoStore);

const recent = ref<HomeRecentVO[]>([]);
const recentLoading = ref(false);
const recentError = ref('');

const loadRecent = async () => {
  recentLoading.value = true;
  try {
    const res = await getHomeRecent();
    recent.value = res.data || [];
    recentError.value = '';
  } catch (e: any) {
    // 拉不到就说清楚（留空表 + 一行原因），不要静默当成「没有提交记录」
    recentError.value = e?.message || String(e);
  } finally {
    recentLoading.value = false;
  }
};

/** 刷新按钮：数字与最近提交一起重来（数字仍然是**同一个 store**，角标跟着一起变） */
const reload = async () => {
  await Promise.all([todoStore.refresh(), loadRecent()]);
};

const sourceLabel = (value?: string) => {
  if (value === 'internal') return t('lqg.home.source.internal');
  if (value === 'external') return t('lqg.home.source.external');
  return value || '—';
};

const statusLabel = (value?: string) => {
  if (value === 'pending') return t('lqg.home.status.pending');
  if (value === 'valid') return t('lqg.home.status.valid');
  if (value === 'invalid') return t('lqg.home.status.invalid');
  return value || '—';
};

const statusTagType = (value?: string) => {
  if (value === 'valid') return 'success';
  if (value === 'invalid') return 'danger';
  if (value === 'pending') return 'warning';
  return 'info';
};

onMounted(async () => {
  // ★ 卡片要的是「现在」的数：进首页就重新拉一次（后端本来就是读时计算）。
  //   侧边栏的 ensureLoaded() 与这里的 refresh() 同时发生时会命中同一个 in-flight Promise，
  //   所以登录后首页 + 侧边栏不会打两个请求。
  await reload();
});

// ★ 首页被 keep-alive 缓存时（回到首页不重新 mount），从别的页签回来也要重算一遍 ——
//   否则「刚核验掉一条，回首页还是旧数字」。首次激活跟着 onMounted 走，跳过，避免两次请求。
let firstActivation = true;
onActivated(() => {
  if (firstActivation) {
    firstActivation = false;
    return;
  }
  reload();
});
</script>

<style scoped lang="scss">
.lqg-home {
  &__banner {
    border: 1px solid var(--lqg-line);
    border-radius: 8px;
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  &__title {
    font-size: 20px;
    font-weight: 600;
    color: var(--lqg-ink);
  }

  &__subtitle {
    margin-top: 4px;
    font-size: 13px;
    color: var(--lqg-ink-3);
  }

  &__cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 12px;
    margin-top: 12px;
  }

  &__recent-head {
    display: flex;
    align-items: baseline;
    gap: 10px;
  }

  &__recent-title {
    font-size: 16px;
    font-weight: 600;
    color: var(--lqg-ink);
  }

  &__recent-sub {
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
}
</style>
