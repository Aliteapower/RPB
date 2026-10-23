<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {useWalletRecord} from '../useWalletRecord'
import {dateTime} from '../format'
import {safeTransactionReturn} from './query'
import type {TransactionDetail,TransactionSource} from './types'
import WalletTransactionRow from './WalletTransactionRow.vue'
const props=defineProps<{store:string;sourceType:TransactionSource;id:string;customer?:boolean}>(),route=useRoute(),{t}=useI18n()
const store=computed(()=>props.store),path=computed(()=>`/transactions/${props.sourceType}/${encodeURIComponent(props.id)}`)
const {record,error,busy,ready}=useWalletRecord<TransactionDetail>(store,path,!!props.customer)
const back=computed(()=>safeTransactionReturn(route.query.returnTo,props.store,!!props.customer))
</script>
<template><section class="wallet-card wallet-transaction-evidence"><h3>{{t('wallet.ui.transactionInfo')}}</h3><p v-if="error" class="wallet-error" role="alert">{{error}}</p><p v-if="!ready&&busy" role="status">{{t('wallet.loading')}}</p><template v-if="record"><p>{{t(`wallet.transactions.${record.transaction.status}`)}}<template v-if="record.transaction.refundStatus&&record.transaction.refundStatus!=='none'"> · {{t('wallet.transactions.refundStatus')}} {{t(`wallet.states.${record.transaction.refundStatus}`)}}</template></p><dl class="wallet-detail-fields"><template v-for="[key,value] in [['created',record.transaction.createdAt],['posted',record.transaction.postedAt],['occurred',record.transaction.occurredAt]]" :key="key??''"><dt>{{t(`wallet.transactions.${key}`)}}</dt><dd>{{value?dateTime(value,record.transaction.timeZone):'—'}}</dd></template></dl><p v-if="record.transaction.summary">{{record.transaction.summary}}</p><p v-for="(event,index) in record.events" :key="index">{{event.label??event.type}} · {{event.occurredAt?dateTime(event.occurredAt,record.transaction.timeZone):'—'}}</p><h3 v-if="record.related.length">{{t('wallet.transactions.related')}}</h3><WalletTransactionRow v-for="row in record.related" :key="row.sourceType+row.id" :row="row" :customer="!!customer" :return-to="back" /></template></section></template>
