<script setup lang="ts">
import WalletTransactionEvidence from '../transactions/WalletTransactionEvidence.vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useCustomerWallet } from '../useCustomerWallet'
import { useWalletRecord } from '../useWalletRecord'
import { dateTime } from '../format'
import type { Bill } from '../types'
import WalletCustomerBill from '../components/WalletCustomerBill.vue'
const{t}=useI18n(),route=useRoute(),workspace=useCustomerWallet(),{store,home}=workspace
const path=computed(()=>`/bills/${encodeURIComponent(String(route.params.billId))}`)
const {record:bill,ready,busy,error,refresh}=useWalletRecord<Bill>(store,path,true)
async function changed(value:Bill){bill.value=value;await workspace.refresh();await refresh()}
</script>
<template><p v-if="error" class="wallet-error" role="alert">{{ error }}</p><p v-if="!ready && busy" class="wallet-empty" role="status">{{ t('wallet.loading') }}</p><template v-if="bill && home"><WalletCustomerBill :key="bill.id" :store="store" :bill="bill" :context="home.context" @changed="changed" /><WalletTransactionEvidence :store="store" source-type="bill" :id="bill.id" customer /><details class="wallet-reference"><summary>{{ t('wallet.ui.transactionInfo') }}</summary><p>{{ t('wallet.reference') }} {{ bill.id }}</p><p>{{ dateTime(bill.createdAt,home.context.store.timezone) }}</p></details></template></template>
