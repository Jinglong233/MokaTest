<template>
  <a-layout-footer class="footer">
    <span class="footer-copyright">Copyright © {{ currentYear }} MokaTest</span>
    <a-divider direction="vertical" />
    <a
      class="footer-icp"
      href="https://beian.miit.gov.cn/"
      target="_blank"
      rel="noopener noreferrer"
    >
      陕ICP备2026020620号-1
    </a>
  </a-layout-footer>
</template>

<script lang="ts" setup>
  import { computed } from 'vue';

  const currentYear = computed(() => new Date().getFullYear());
</script>

<style lang="less" scoped>
  .footer {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 20px;
    color: var(--color-text-2);
    text-align: center;
    padding: 50px;
    font-size: 15px;
    margin-bottom: 10px;

    .footer-copyright {
      color: var(--color-text-3);
    }

    .footer-icp {
      color: var(--color-text-2);
      text-decoration: none;

      &:hover {
        color: rgb(var(--primary-6));
      }
    }

    :deep(.arco-divider-vertical) {
      margin: 0 6px;
      border-color: var(--color-text-4);
    }
  }
</style>
