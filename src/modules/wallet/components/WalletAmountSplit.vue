<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { money } from '../format'
defineProps<{principal:number;bonus:number;total?:number}>()
const {t}=useI18n()
</script>
<template><dl class="wallet-component-amounts"><div><dt>{{t('wallet.marketing.principal')}}</dt><dd>{{money(principal)}}</dd></div><div><dt>{{t('wallet.marketing.bonus')}}</dt><dd>{{money(bonus)}}</dd></div><div v-if="total!==undefined"><dt>{{t('wallet.marketing.total')}}</dt><dd>{{money(total)}}</dd></div></dl></template>
