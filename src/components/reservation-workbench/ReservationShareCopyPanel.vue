<script setup lang="ts">
import { computed } from 'vue'
import type { ReservationShareInfo } from '../../types/reservationShareInfo'

const props = withDefaults(
  defineProps<{
    shareInfo: ReservationShareInfo | null
    loading?: boolean
    shared?: boolean
    errorText?: string
    fallbackText?: string
    statusText?: string
    disabled?: boolean
  }>(),
  {
    loading: false,
    shared: false,
    errorText: '',
    fallbackText: '',
    statusText: '已准备链接',
    disabled: false
  }
)

const emit = defineEmits<{
  'whatsapp-requested': []
  'wechat-requested': []
  'system-share-requested': []
  'copy-requested': []
}>()

const whatsappUnavailable = computed(() => {
  return !!props.shareInfo && (!props.shareInfo.canOpenWhatsAppLink || !props.shareInfo.whatsappLink)
})

function request(action: 'whatsapp-requested' | 'wechat-requested' | 'system-share-requested' | 'copy-requested'): void {
  if (props.loading || props.disabled) {
    return
  }

  if (action === 'whatsapp-requested') {
    emit('whatsapp-requested')
    return
  }
  if (action === 'wechat-requested') {
    emit('wechat-requested')
    return
  }
  if (action === 'system-share-requested') {
    emit('system-share-requested')
    return
  }
  emit('copy-requested')
}
</script>

<template>
  <section class="reservation-share-copy" aria-label="订位链接转发">
    <div class="reservation-share-copy__actions">
      <button
        class="reservation-share-copy__button reservation-share-copy__button--whatsapp"
        type="button"
        :disabled="loading || disabled || whatsappUnavailable"
        @click="request('whatsapp-requested')"
      >
        {{ loading ? '读取中' : 'WhatsApp发送' }}
      </button>
      <button
        class="reservation-share-copy__button reservation-share-copy__button--wechat"
        type="button"
        :disabled="loading || disabled"
        @click="request('wechat-requested')"
      >
        微信发送
      </button>
      <button
        class="reservation-share-copy__button"
        type="button"
        :disabled="loading || disabled"
        @click="request('system-share-requested')"
      >
        系统转发
      </button>
      <button
        class="reservation-share-copy__button reservation-share-copy__button--secondary"
        type="button"
        :disabled="loading || disabled"
        @click="request('copy-requested')"
      >
        复制链接
      </button>
    </div>

    <p v-if="shared" class="reservation-share-copy__status" role="status">{{ statusText }}</p>
    <p v-else-if="errorText" class="reservation-share-copy__error" role="alert">{{ errorText }}</p>

    <textarea
      v-if="fallbackText"
      class="reservation-share-copy__fallback"
      readonly
      :value="fallbackText"
      aria-label="订位分享链接"
    ></textarea>
  </section>
</template>

<style scoped>
.reservation-share-copy {
  display: grid;
  gap: 8px;
}

.reservation-share-copy__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.reservation-share-copy__button {
  background: #315f91;
  border: 1px solid #315f91;
  border-radius: 6px;
  color: #ffffff;
  font: inherit;
  font-size: 0.82rem;
  font-weight: 900;
  min-height: 34px;
  padding: 0 12px;
}

.reservation-share-copy__button--whatsapp {
  background: #137d4f;
  border-color: #137d4f;
}

.reservation-share-copy__button--wechat {
  background: #16803c;
  border-color: #16803c;
}

.reservation-share-copy__button--secondary {
  background: #ffffff;
  border-color: #94a3b8;
  color: #315f91;
}

.reservation-share-copy__button:disabled {
  background: #cbd5e1;
  border-color: #cbd5e1;
  color: #64748b;
  cursor: default;
}

.reservation-share-copy__status,
.reservation-share-copy__error {
  font-size: 0.78rem;
  font-weight: 850;
  margin: 0;
}

.reservation-share-copy__status {
  color: #166534;
}

.reservation-share-copy__error {
  color: #b42318;
  overflow-wrap: anywhere;
}

.reservation-share-copy__fallback {
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #0f172a;
  font: inherit;
  min-height: 120px;
  padding: 10px;
  resize: vertical;
  width: 100%;
}
</style>
