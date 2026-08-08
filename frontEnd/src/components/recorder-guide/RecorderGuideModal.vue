<template>
  <!-- 录制插件获取引导弹窗（关注公众号回复关键词） -->
  <a-modal
      :visible="visible"
      title="获取浏览器录制插件"
      :footer="false"
      :width="420"
      @cancel="handleCancel"
  >
    <div class="recorder-modal">
      <a-alert type="info" :closable="false">
        录制插件可录制浏览器操作并一键生成 UI 自动化场景
      </a-alert>
      <div class="recorder-steps">
        <div class="step">
          <div class="step-num">1</div>
          <div class="step-text">微信扫码关注公众号「{{ OFFICIAL_ACCOUNT_NAME }}」</div>
        </div>
        <div class="qrcode-wrap">
          <!-- TODO: 替换为真实公众号二维码图片（放在 frontEnd/public 下） -->
          <img src="/official-account-qrcode.png" alt="公众号二维码" class="qrcode"/>
        </div>
        <div class="step">
          <div class="step-num">2</div>
          <div class="step-text">
            在公众号后台回复关键词
            <span class="keyword">{{ RECORDER_KEYWORD }}</span>
            获取插件安装包与使用教程
          </div>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script lang="ts" setup>
// 公众号名称与回复关键词（按需修改）
const OFFICIAL_ACCOUNT_NAME = 'MokaTest';
const RECORDER_KEYWORD = 'MokaTest录制插件';

const props = defineProps<{
  visible: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void;
}>();

const handleCancel = () => {
  emit('update:visible', false);
};
</script>

<style scoped lang="less">
.recorder-modal {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .recorder-steps {
    display: flex;
    flex-direction: column;
    gap: 12px;

    .step {
      display: flex;
      align-items: flex-start;
      gap: 10px;

      .step-num {
        flex-shrink: 0;
        width: 20px;
        height: 20px;
        border-radius: 50%;
        background: rgb(var(--primary-6));
        color: #fff;
        font-size: 12px;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-top: 1px;
      }

      .step-text {
        font-size: 13px;
        color: var(--color-text-1);
        line-height: 1.6;

        .keyword {
          display: inline-block;
          padding: 0 6px;
          margin: 0 2px;
          border-radius: 4px;
          background: rgb(var(--orange-1));
          color: rgb(var(--orange-6));
          font-weight: 600;
        }
      }
    }

    .qrcode-wrap {
      display: flex;
      justify-content: center;

      .qrcode {
        width: 160px;
        height: 160px;
        border: 1px solid var(--color-border);
        border-radius: 8px;
        object-fit: cover;
      }
    }
  }
}
</style>
