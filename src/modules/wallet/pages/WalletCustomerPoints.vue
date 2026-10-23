<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePoints } from '../usePoints'
import { useCustomerWallet } from '../useCustomerWallet'
import { dateTime } from '../format'
const {t}=useI18n(),{store,home}=useCustomerWallet(),points=usePoints(store,true,ref(undefined)),{account,entries,ready,busy,error,more}=points
</script>
<template><section class="wallet-page-heading"><h2>{{t('wallet.marketing.points')}}</h2><button :disabled="busy" @click="points.refresh()">{{t('wallet.refresh')}}</button></section><p class="wallet-warning">{{t('wallet.marketing.pointsHint')}}</p><p v-if="error" class="wallet-error" role="alert">{{error}}</p><p v-if="busy&&!ready" role="status">{{t('wallet.loading')}}</p><section v-if="account" class="wallet-card"><h3>{{t('wallet.marketing.balance')}}: {{account.balance}}</h3></section><p v-if="ready&&!entries.length" class="wallet-empty">{{t('wallet.empty')}}</p><article v-for="entry in entries" :key="entry.id" class="wallet-record"><strong>{{t(`wallet.marketing.${entry.kind}`)}} · {{entry.quantity}}</strong><span>{{t('wallet.marketing.balance')}} {{entry.afterBalance}}</span><p>{{entry.reason}} {{entry.redemptionDescription}} {{entry.note}}</p><small>{{dateTime(entry.createdAt,home?.context.store.timezone??'Asia/Singapore')}}</small></article><button v-if="more" :disabled="busy" @click="points.refresh(true)">{{t('wallet.marketing.next')}}</button></template>
