<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { walletApi } from '../api'
import { useWalletTask } from '../useWalletTask'
import type { Context, Channel } from '../types'
import WalletPayNowSettings from './WalletPayNowSettings.vue'
const props = defineProps<{ store: string; context: Context; allowed: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const { t } = useI18n(), { busy, error, run } = useWalletTask()
const channels = ref<Channel[]>([...(props.context.program?.channels ?? ['cash', 'paynow', 'credit_card'])])
watch(() => props.context.program?.version, () => { channels.value = [...(props.context.program?.channels ?? ['cash', 'paynow', 'credit_card'])] })
async function save() { await run(async () => { await walletApi(props.store).command('/program', { version: props.context.program?.version ?? null, channels: channels.value }, 'PUT'); emit('changed') }) }
</script>
<template>
  <section class="wallet-card"><h2>{{ t('wallet.settings') }}</h2><p>{{ t('wallet.shared') }}</p><p>{{ t('wallet.issuer') }}：{{ context.store.operatingEntityName || '-' }}</p>
    <RouterLink :to="`/stores/${store}/admin/profile`">{{ t('wallet.publicProfile') }}</RouterLink><p v-if="error" class="wallet-error" role="alert">{{ error }}</p>
    <form v-if="allowed" class="wallet-form" @submit.prevent="save"><p>{{ t('wallet.channelsSetting') }}</p><label v-for="c in (['cash', 'paynow', 'credit_card'] as Channel[])" :key="c" class="wallet-check"><input v-model="channels" :value="c" type="checkbox">{{ t(`wallet.channels.${c}`) }}</label><button :disabled="busy || !channels.length || !!context.denialReason || !context.store.operatingEntityId">{{ context.program ? t('wallet.save') : t('wallet.configure') }}</button></form>
    <WalletPayNowSettings v-if="allowed" :store="store" :enabled="!context.denialReason" @changed="emit('changed')" />
  </section>
</template>
