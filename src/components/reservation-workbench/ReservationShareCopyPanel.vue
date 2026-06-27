<script setup lang="ts">
import type { ReservationShareInfo } from '../../types/reservationShareInfo'

const props = withDefaults(
  defineProps<{
    shareInfo: ReservationShareInfo | null
    loading?: boolean
    copied?: boolean
    errorText?: string
    fallbackText?: string
    buttonText?: string
    disabled?: boolean
  }>(),
  {
    loading: false,
    copied: false,
    errorText: '',
    fallbackText: '',
    buttonText: '复制订位信息',
    disabled: false
  }
)

const emit = defineEmits<{
  'copy-requested': []
}>()

function requestCopy(): void {
  if (!props.loading && !props.disabled) {
    emit('copy-requested')
  }
}
</script>

<template>
  <section class="reservation-share-copy" aria-label="订位分享复制">
    <button
      class="reservation-share-copy__button"
      type="button"
      :disabled="loading || disabled"
      @click="requestCopy"
    >
      {{ loading ? '读取中' : buttonText }}
    </button>

    <p v-if="copied" class="reservation-share-copy__status" role="status">已复制</p>
    <p v-else-if="errorText" class="reservation-share-copy__error" role="alert">{{ errorText }}</p>

    <textarea
      v-if="fallbackText"
      class="reservation-share-copy__fallback"
      readonly
      :value="fallbackText"
      aria-label="订位分享文本"
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
