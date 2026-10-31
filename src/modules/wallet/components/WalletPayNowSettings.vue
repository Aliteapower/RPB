<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { walletApi } from '../api'
import { useWalletTask } from '../useWalletTask'
const props = defineProps<{store: string; enabled: boolean}>()
const emit = defineEmits<{changed: []}>()
interface Settings { version: number | null; inherited: boolean; status: string; paynowType: string; paynowMobile: string | null; paynowUen: string | null; merchantName: string | null }
const {t} = useI18n(), {busy,error,run} = useWalletTask(), profile = ref<Settings | null>(null), verified = ref(false)
async function load() { profile.value = await walletApi(props.store).get<Settings>('/collection-profile'); verified.value = false }
async function save() { if(!profile.value) return; await run(async()=>{const {inherited: _, ...fields} = profile.value!; profile.value = await walletApi(props.store).command<Settings>('/collection-profile',{...fields,issuerVerified:verified.value},'PUT'); verified.value = false; emit('changed')}) }
onMounted(()=>{void run(load)})
</script>
<template><section class="wallet-card"><h3>{{ t('wallet.paynowSettings') }}</h3><p>{{ t('wallet.sharedCollectionWarning') }}</p><p v-if="error" class="wallet-error" role="alert">{{ error }}</p>
  <form v-if="profile" class="wallet-form" @submit.prevent="save"><p v-if="profile.inherited" class="wallet-warning">{{ t('wallet.inheritedPaynow') }}</p>
    <label>{{ t('wallet.paynowState') }}<select v-model="profile.status"><option value="disabled">{{ t('wallet.disabled') }}</option><option value="active">{{ t('wallet.states.active') }}</option></select></label>
    <label>{{ t('wallet.proxyType') }}<select v-model="profile.paynowType"><option value="uen">UEN</option><option value="mobile">{{ t('wallet.mobileProxy') }}</option></select></label>
    <label v-if="profile.paynowType === 'uen'">UEN<input v-model.trim="profile.paynowUen" :required="profile.status === 'active'" maxlength="32"></label>
    <label v-else>{{ t('wallet.mobileProxy') }}<input v-model.trim="profile.paynowMobile" :required="profile.status === 'active'" inputmode="tel" maxlength="32"></label>
    <label>{{ t('wallet.merchantName') }}<input v-model.trim="profile.merchantName" :required="profile.status === 'active'" maxlength="100"></label>
    <label class="wallet-check"><input v-model="verified" type="checkbox" :required="profile.status === 'active'">{{ t('wallet.issuerVerified') }}</label>
    <button :disabled="busy || !enabled || (profile.status === 'active' && !verified)">{{ t('wallet.save') }}</button><button type="button" :disabled="busy" @click="run(load)">{{ t('wallet.refresh') }}</button>
  </form>
</section></template>
