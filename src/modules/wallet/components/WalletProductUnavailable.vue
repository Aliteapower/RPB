<script setup lang="ts">
import {useI18n} from 'vue-i18n'
defineProps<{historyTo?:string}>()
const {t}=useI18n()
</script>
<template>
 <section class="wallet-card" role="status">
  <h2>{{t('wallet.entryUnavailable')}}</h2>
  <p>{{t('wallet.entryUnavailableHint')}}</p>
  <RouterLink v-if="historyTo" :to="historyTo">{{t('wallet.customerHistoryOnly')}}</RouterLink>
 </section>
</template>
