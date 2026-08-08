<template>
  <a-modal :visible="vis" @cancel="handleCancel" width="auto">
    <a-tabs style="width: 1050px" :justify="true">
      <a-tab-pane key="1" title="截图预览">
        <a-image-preview-group infinite v-if="props.imageList && props.imageList.length > 0">
          <div style="display: flex; flex-wrap: wrap; gap: 8px;">
            <template v-for="(image,index) in props.imageList" :key="image.id">
              <ScreenshotImage
                  v-if="image.result && image.result.screenshotPath"
                  width="200"
                  :file-id="image.result.screenshotPath"
                  :image-style="{flexShrink: 0, border: '1px solid #c2c8d5'}"
              />
              <ScreenshotImage
                  v-if="image.iterations && Object.keys(image.iterations).length > 0"
                  width="200"
                  :file-id="image.iterations[Object.keys(image.iterations).length].screenshotPath"
                  :image-style="{flexShrink: 0, border: '1px solid #c2c8d5'}"
              />
            </template>
          </div>
        </a-image-preview-group>
      </a-tab-pane>
      <a-tab-pane key="2" title="视频预览（待做....）">
      </a-tab-pane>
    </a-tabs>

  </a-modal>
</template>

<script setup lang="ts">

import {ref, watch} from "vue";
import ScreenshotImage from "@/views/scene/component/ScreenshotImage.vue";

const props = defineProps({
  visible: {
    type: Boolean,
    default: () => false,
  },
  imageList: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(['update:visible'])
const imageList = ref([...props.imageList]);
const vis = ref(props.visible);
const handleCancel = () => {
  emit('update:visible', false)
}

watch(() => props.visible, (val) => {
  vis.value = val;
}, {
  deep: true,
  immediate: true
});

const extractNumberFromFilename = (imageName: any) => {
  const match = imageName.match(/_(\d+)\./);
  return match ? match[1] : null;
}


watch(() => props.imageList, (val) => {
  imageList.value = [...val];
}, {
  immediate: true
});
</script>

<style scoped>

</style>