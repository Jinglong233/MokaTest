<template>
  <div class="protocol-cards-container">
    <div
      v-for="card in protocolCards"
      :key="card.id"
      class="protocol-card"
      @click="handleCardClick(card)"
    >
      <div class="icon-wrapper" :style="{ backgroundColor: card.color }">
        <component :is="card.icon" class="icon" />
      </div>
      <div class="card-title">{{ card.title }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, h } from 'vue'
import { useI18n } from 'vue-i18n';
import { IconLink, IconStorage, IconWifi, IconMessage, IconCloud } from '@arco-design/web-vue/es/icon'

interface ProtocolCard {
  id: string
  title: string
  color: string
  icon: any
}

const { t } = useI18n();

const protocolCards = computed<ProtocolCard[]>(() => [
  {
    id: 'http',
    title: t('scene.protocol.newHttp'),
    color: 'rgba(255, 145, 77, 0.15)',
    icon: h(IconLink, { style: { color: '#FF914D', fontSize: '24px' } })
  },
  {
    id: 'sql',
    title: t('scene.protocol.newSql'),
    color: 'rgba(22, 193, 243, 0.15)',
    icon: h(IconStorage, { style: { color: '#16C1F3', fontSize: '24px' } })
  },
  {
    id: 'tcp',
    title: t('scene.protocol.newTcp'),
    color: 'rgba(168, 85, 247, 0.15)',
    icon: h('span', { style: { color: '#A855F7', fontSize: '14px', fontWeight: '600' } }, 'TCP')
  },
  {
    id: 'websocket',
    title: t('scene.protocol.newWebsocket'),
    color: 'rgba(250, 204, 21, 0.15)',
    icon: h('span', { style: { color: '#FACC15', fontSize: '14px', fontWeight: '600' } }, 'WS')
  },
  {
    id: 'dubbo',
    title: t('scene.protocol.newDubbo'),
    color: 'rgba(96, 165, 250, 0.15)',
    icon: h(IconCloud, { style: { color: '#60A5FA', fontSize: '24px' } })
  }
])

const emit = defineEmits<{
  (e: 'select', card: ProtocolCard): void
}>();

const handleCardClick = (card: ProtocolCard) => {
  emit('select', card)
}
</script>

<style scoped>
.protocol-cards-container {
  display: flex;
  gap: 16px;
  padding: 40px;
  background: linear-gradient(to bottom, #f8f9fa, #ffffff);
  min-height: 100vh;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
}

.protocol-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 140px;
  height: 160px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: all 0.3s ease;
  padding: 20px;
}

.protocol-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.12);
}

.icon-wrapper {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  transition: transform 0.3s ease;
}

.protocol-card:hover .icon-wrapper {
  transform: scale(1.1);
}

.icon {
  font-size: 24px;
}

.card-title {
  font-size: 14px;
  color: #4e5969;
  font-weight: 500;
  text-align: center;
  line-height: 1.5;
}
</style>