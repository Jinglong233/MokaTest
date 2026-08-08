<template>
  <a-spin v-if="loading" :size="20"/>
  <a-image
      v-else-if="previewUrl"
      :width="width"
      :height="height"
      :src="previewUrl"
      :style="imageStyle"
      @click.stop
  >
    <template #error>
      <a-empty description="图片加载失败"/>
    </template>
  </a-image>
  <a-empty v-else description="图片加载失败"/>
</template>

<script setup lang="ts">
import {ref, watch} from 'vue';
import {getFilePreviewUrl} from "@/api/MyApi/fileStorage";

interface Props {
  fileId?: string;
  width?: string | number;
  height?: string | number;
  imageStyle?: Record<string, any>;
}

const props = withDefaults(defineProps<Props>(), {
  width: '100%',
  height: undefined,
  imageStyle: () => ({}),
});

const loading = ref(false);
const previewUrl = ref('');

const loadPreviewUrl = async () => {
  if (!props.fileId) {
    previewUrl.value = '';
    return;
  }
  loading.value = true;
  try {
    previewUrl.value = await getFilePreviewUrl(props.fileId);
  } catch (e) {
    console.error('获取截图预览地址失败', e);
    previewUrl.value = '';
  } finally {
    loading.value = false;
  }
};

watch(() => props.fileId, () => {
  loadPreviewUrl();
}, {immediate: true});
</script>
