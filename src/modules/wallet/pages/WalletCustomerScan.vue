<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useCustomerWallet } from '../useCustomerWallet'
import { useWalletTask } from '../useWalletTask'
import WalletQrScanner from '../components/WalletQrScanner.vue'
const {t}=useI18n(),{home,scan}=useCustomerWallet(),{busy,error,run}=useWalletTask()
</script>
<template><section class="wallet-page-heading"><h2>{{ t('wallet.ui.scanPay') }}</h2><p>{{ t('wallet.ui.scanPayHint') }}</p></section><p v-if="error" class="wallet-error" role="alert">{{ error }}</p><p v-if="busy" role="status">{{ t('wallet.loading') }}</p><WalletQrScanner v-if="home?.context.transactionsEnabled" purpose="bill" auto-start :disabled="busy" @code="value=>run(()=>scan(value))" /><p v-else class="wallet-empty">{{ t('wallet.expired') }}</p></template>
