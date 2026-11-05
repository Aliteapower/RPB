<script setup lang="ts">
import { onUnmounted, ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import QRCode from 'qrcode'
const props = defineProps<{ value: string; expiresAt?: string }>()
const { t } = useI18n()
const source = ref(''), now = ref(Date.now())
const expired = computed(() => props.expiresAt ? Date.parse(props.expiresAt) <= now.value : false)
let generation = 0
watch(() => props.value, async value => {
  const current = ++generation
  source.value = ''
  if (value) { const result = await QRCode.toDataURL(value, { width: 280, margin: 4, errorCorrectionLevel: 'M' }); if (current === generation) source.value = result }
}, { immediate: true })
const timer = window.setInterval(() => { now.value = Date.now() }, 1000)
onUnmounted(() => { window.clearInterval(timer); generation++ })
</script>
<template>
  <figure class="wallet-qr" aria-live="polite">
    <img v-if="source && !expired" :src="source" :alt="t('wallet.code')" width="280" height="280">
    <p v-if="expired" role="status">{{ t('wallet.codeExpired') }}</p>
    <figcaption v-else-if="expiresAt">{{ t('wallet.codeExpires') }} {{ new Date(expiresAt).toLocaleTimeString() }}</figcaption>
  </figure>
</template>
