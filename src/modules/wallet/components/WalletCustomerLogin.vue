<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { loginCustomerWithEmail, requestCustomerEmailCode } from '../../../api/publicBookingApi'
import { useWalletTask } from '../useWalletTask'
const props = defineProps<{ store: string }>()
const emit = defineEmits<{ authenticated: [] }>()
const { t } = useI18n(), { busy, error, run } = useWalletTask()
const email = ref(''), code = ref(''), name = ref(''), sent = ref(false)
async function send() { await run(async () => { await requestCustomerEmailCode(props.store, email.value); sent.value = true }) }
async function login() { await run(async () => { await loginCustomerWithEmail(props.store, email.value, code.value, name.value); emit('authenticated') }) }
</script>
<template><section class="wallet-card"><h2>{{ t('wallet.login') }}</h2><p>{{ t('wallet.loginHint') }}</p><form class="wallet-form" @submit.prevent="login"><label>{{ t('wallet.email') }}<input v-model.trim="email" type="email" required autocomplete="email"></label><button type="button" :disabled="busy || !email" @click="send">{{ t('wallet.sendCode') }}</button><p v-if="sent" role="status">{{ t('wallet.sent') }}</p><label>{{ t('wallet.code') }}<input v-model.trim="code" required inputmode="numeric" autocomplete="one-time-code"></label><label>{{ t('wallet.displayName') }}<input v-model.trim="name" autocomplete="name" maxlength="100"></label><button :disabled="busy">{{ t('wallet.login') }}</button></form><p v-if="error" class="wallet-error" role="alert">{{ error }}</p></section></template>
