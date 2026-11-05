<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import WalletPersonalCode from '../components/WalletPersonalCode.vue'
const {t}=useI18n()
</script>
<template><section class="wallet-page-heading"><h2>{{t('wallet.showMyCode')}}</h2><p>{{t('wallet.personal.hint')}}</p></section><WalletPersonalCode /></template>
