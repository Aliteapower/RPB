<script setup lang="ts">
import type { ReservationShareInfo } from '../../types/reservationShareInfo'

const props = withDefaults(
  defineProps<{
    shareInfo: ReservationShareInfo | null
    loading?: boolean
    shared?: boolean
    errorText?: string
    fallbackText?: string
    buttonText?: string
    statusText?: string
    disabled?: boolean
  }>(),
  {
    loading: false,
    shared: false,
    errorText: '',
    fallbackText: '',
    buttonText: '转发订位链接',
    statusText: '已准备链接',
    disabled: false
  }
)

const emit = defineEmits<{
  'share-requested': []
}>()

function requestShare(): void {
  if (!props.loading && !props.disabled) {
    emit('share-requested')
  }
}
</script>

<template>
  <section class="reservation-share-copy" aria-label="订位链接转发">
    <button
      class="reservation-share-copy__button"
      type="button"
      :disabled="loading || disabled"
      @click="requestShare"
    >
      {{ loading ? '读取中' : buttonText }}
    </button>

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

.reservation-share-copy__button {
  background: #0f766e;
  border: 1px solid #0f766e;
  border-radius: 6px;
  color: #ffffff;
  font: inherit;
  font-size: 0.82rem;
  font-weight: 900;
  min-height: 34px;
  padding: 0 12px;
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
