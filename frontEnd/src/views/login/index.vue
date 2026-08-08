<template>
  <div class="container">
    <div class="logo">
      <img alt="logo" src="@/assets/logo.png" :style="{ height: '36px' }" />
      <div class="logo-text">MokaTest</div>
    </div>
    <div class="hero" aria-hidden="true" />
    <div class="content">
      <div class="content-inner">
        <LoginForm />
      </div>
    </div>
    <div class="footer">
      <Footer />
    </div>
  </div>
</template>

<script lang="ts" setup>
  import Footer from '@/components/footer/index.vue';
  import LoginForm from './components/login-form.vue';
</script>

<style lang="less" scoped>
  .container {
    position: relative;
    display: flex;
    height: 100vh;
    // 整页浅蓝底（与图同色系），作为左右过渡的"溶剂"
    background: #edf3fe;
  }

  .content {
    display: flex;
    flex: 1;
    align-items: center;
    justify-content: center;
    min-width: 360px;
  }

  // 左侧 2/3 背景图：宽度贴满、高度按原图比例，右缘 mask 渐隐融入页面底色
  .hero {
    width: 66.66%;
    overflow: hidden;
    background: url('@/assets/images/loginHero.png') 78% center / 100% auto
      no-repeat;
    mask-image: linear-gradient(to right, #000 72%, transparent 99%);
    -webkit-mask-image: linear-gradient(to right, #000 72%, transparent 99%);
  }

  .logo {
    position: fixed;
    top: 24px;
    left: 22px;
    z-index: 1;
    display: inline-flex;
    align-items: center;

    &-text {
      margin-right: 4px;
      margin-left: 6px;
      // 浅底背景，字标用深色
      color: var(--color-text-1);
      font-weight: 600;
      font-size: 20px;
    }
  }

  .footer {
    position: absolute;
    right: 0;
    bottom: 0;
    z-index: 1;
    width: 100%;
  }

  // responsive
  @media (max-width: @screen-lg) {
    .hero {
      width: 55%;
    }
  }

  @media (max-width: @screen-md) {
    .hero {
      display: none;
    }
  }
</style>
