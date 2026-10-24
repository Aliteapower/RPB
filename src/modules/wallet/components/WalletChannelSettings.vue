<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { walletApi } from '../api'
import { useWalletTask } from '../useWalletTask'
import type { Context, Channel } from '../types'
const props = defineProps<{ store: string; context: Context }>()
const emit = defineEmits<{ changed: [] }>()
const { t } = useI18n(), { busy, error, run } = useWalletTask()
const channels = ref<Channel[]>([...(props.context.program?.channels ?? ['cash', 'paynow', 'credit_card'])])
watch(() => props.context.program?.version, () => { channels.value = [...(props.context.program?.channels ?? ['cash', 'paynow', 'credit_card'])] })
async function save() {
  if (busy.value || !channels.value.length || props.context.denialReason || !props.context.store.operatingEntityId) return
  await run(async () => {
    await walletApi(props.store).command('/program', { version: props.context.program?.version ?? null, channels: channels.value }, 'PUT')
    emit('changed')
  })
}
</script>
<template>
  <section class="wallet-card">
    <p>{{ t('wallet.channelsSetting') }}</p>
    <p class="wallet-muted">{{ t('wallet.issuer') }}：{{ context.store.operatingEntityName || '-' }}</p>
    <p v-if="error" class="wallet-error" role="alert">{{ error }}</p>
    <form class="wallet-form" @submit.prevent="save">
      <label v-for="c in (['cash', 'paynow', 'credit_card'] as Channel[])" :key="c" class="wallet-check"><input v-model="channels" :value="c" type="checkbox" :disabled="busy">{{ t(`wallet.channels.${c}`) }}</label>
      <button class="wallet-primary" :disabled="busy || !channels.length || !!context.denialReason || !context.store.operatingEntityId">{{ context.program ? t('wallet.save') : t('wallet.configure') }}</button>
    </form>
  </section>
</template>
