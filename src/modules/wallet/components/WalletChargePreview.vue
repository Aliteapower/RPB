<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { money } from '../format'
defineProps<{ debitCents: number; dueCents: number }>()
const { t } = useI18n()
</script>
<template><dl class="wallet-detail-fields"><dt>{{ t('wallet.debit') }}</dt><dd>{{ money(debitCents) }}</dd><dt>{{ t('wallet.stillDue') }}</dt><dd :class="{'wallet-due':dueCents>0}">{{ money(dueCents) }}</dd></dl><p v-if="dueCents>0" class="wallet-warning">{{ t('wallet.ui.supplementHint',{amount:money(dueCents)}) }}</p><small>{{ t('wallet.scanCharge.staffConsent') }}</small></template>
