<template>
  <!--
    SYS-WEB-001：上游的演示首页（RuoYi-Vue-Plus / RuoYi-Cloud-Plus 两栏介绍 + 外链按钮）整段去掉。
    首页待办五张卡片的内容属 SYS-HOME-001（UI:admin.home），本票只放一句占位。
  -->
  <div class="app-container home">
    <el-card shadow="never" class="lqg-home-placeholder">
      <h2 class="lqg-home-title">{{ $t('lqg.sys.brand') }}</h2>
      <p class="lqg-home-line">{{ $t('lqg.sys.homePlaceholder') }}</p>
      <p class="lqg-home-hint">{{ $t('lqg.sys.homePlaceholderHint') }}</p>
    </el-card>
  </div>
</template>

<script setup lang="ts"></script>

<style lang="scss" scoped>
.home {
  .lqg-home-placeholder {
    border: 1px solid var(--lqg-line);
    background: var(--lqg-card);
    border-radius: 8px;
  }

  .lqg-home-title {
    margin: 0 0 12px 0;
    font-size: 22px;
    font-weight: 600;
    color: var(--lqg-ink);
  }

  .lqg-home-line {
    margin: 6px 0;
    color: var(--lqg-ink-2);
  }

  .lqg-home-hint {
    margin: 6px 0 0 0;
    font-size: 13px;
    color: var(--lqg-ink-3);
  }
}
</style>
