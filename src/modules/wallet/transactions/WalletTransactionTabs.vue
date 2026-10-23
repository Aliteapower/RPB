<script setup lang="ts">
import {useI18n} from 'vue-i18n'
defineProps<{categories:string[];category:string;direction:string;busy:boolean;customer:boolean}>()
defineEmits<{category:[value:string];direction:[value:string]}>()
const {t}=useI18n()
</script>
<template>
 <nav class="wallet-transaction-folders" :aria-label="t('wallet.transactions.folders')">
  <button v-for="folder in categories" :key="folder" type="button" :aria-pressed="category===folder" :class="{'is-active':category===folder}" :disabled="busy" @click="$emit('category',folder)">{{t(`wallet.transactions.folder_${folder}`)}}</button>
 </nav>
 <div class="wallet-transaction-flowbar">
  <small>{{t(category==='points'?'wallet.transactions.pointsFlow':customer?'wallet.transactions.customerFlow':'wallet.transactions.merchantFlow')}}</small>
  <div class="wallet-transaction-flow" role="group" :aria-label="t('wallet.transactions.flow')"><button v-for="flow in ['all','in','out']" :key="flow" type="button" :aria-pressed="direction===flow" :class="{'is-active':direction===flow}" :disabled="busy" @click="$emit('direction',flow)">{{t(`wallet.transactions.flow_${flow}`)}}</button></div>
 </div>
</template>
