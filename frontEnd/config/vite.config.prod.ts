import { mergeConfig } from 'vite';
import baseConfig from './vite.config.base';
import configVisualizerPlugin from './plugin/visualizer';
import configArcoResolverPlugin from './plugin/arcoResolver';

export default mergeConfig(
  {
    mode: 'production',
    // 已移除图压缩（vite-plugin-imagemin）和产物 gzip（vite-plugin-compression）：
    // 两者都要把成品资源再读一遍进内存，是构建容器 OOM 的主要来源；
    // 而且 nginx 侧已开动态 gzip，.gz 根本没被用到
    plugins: [configVisualizerPlugin(), configArcoResolverPlugin()],
    build: {
      rollupOptions: {
        output: {
          manualChunks: {
            arco: ['@arco-design/web-vue'],
            chart: ['echarts', 'vue-echarts'],
            vue: ['vue', 'vue-router', 'pinia', '@vueuse/core', 'vue-i18n'],
          },
        },
      },
      chunkSizeWarningLimit: 2000,
      // 只为控制台打印 gzip 体积，构建期白吃一份内存
      reportCompressedSize: false,
    },
  },
  baseConfig
);
