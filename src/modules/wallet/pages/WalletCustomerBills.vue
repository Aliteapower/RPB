<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useCustomerWallet } from '../useCustomerWallet'
import { useWalletHistory } from '../useWalletHistory'
import { billStage, customerWalletPath } from '../navigation'
import type { Bill } from '../types'
import WalletRecordRow from '../components/WalletRecordRow.vue'
const {t}=useI18n(),route=useRoute(),{store,home}=useCustomerWallet()
const resource=computed(()=>'/bills'),status=computed(()=>['pending','completed','closed'].includes(String(route.query.status))?String(route.query.status):'all')
const{rows,more,loaded,busy,error,refresh,loadMore}=useWalletHistory<Bill>(store,resource,status,true)
</script>
<template><div class="wallet-page-heading"><h2>{{ t('wallet.bills') }}</h2><button class="wallet-link-button" :disabled="busy" @click="refresh">{{ t('wallet.refresh') }}</button></div><nav class="wallet-filter-tabs" :aria-label="t('wallet.ui.filterStatus')"><RouterLink v-for="s in ['all','pending','completed','closed']" :key="s" :to="{path:customerWalletPath(store,'bills'),query:{status:s}}" :class="{'is-current':s===status}">{{ t(`wallet.ui.filters.${s}`) }}</RouterLink></nav>
<p v-if="error" class="wallet-error" role="alert">{{ error }}</p><p v-if="!loaded && busy" class="wallet-empty" role="status">{{ t('wallet.loading') }}</p>
<section class="wallet-list"><WalletRecordRow v-for="bill in rows" :key="bill.id" :to="customerWalletPath(store,`bills/${bill.id}`)" :title="home?.context.store.storeName??t('wallet.ui.walletCollection')" :cents="bill.amountCents" :status="billStage(bill)" :time="bill.createdAt" :timezone="home?.context.store.timezone??'Asia/Singapore'" /></section>
<p v-if="loaded && !rows.length" class="wallet-empty">{{ t('wallet.ui.noMatchingRecords') }}</p><button v-if="more" class="wallet-block" :disabled="busy" @click="loadMore">{{ t('wallet.loadMore') }}</button></template>
