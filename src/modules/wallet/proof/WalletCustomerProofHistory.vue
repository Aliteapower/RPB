<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useWalletRecord} from '../useWalletRecord'
import type {CustomerProof} from './useCustomerPayNowProof'
const props=defineProps<{store:string;sourceType:string;sourceId:string}>(),{t}=useI18n()
const store=computed(()=>props.store),path=computed(()=>`/collection-proofs/${props.sourceType}/${encodeURIComponent(props.sourceId)}`)
const {record,error,busy,refresh,run}=useWalletRecord<CustomerProof[]>(store,path,true)
</script>
<template><section v-if="record?.length||error" class="wallet-card"><h3>{{t('wallet.proof.customerTitle')}}</h3><p v-if="error" class="wallet-error" role="alert">{{error}}</p><article v-for="proof in record??[]" :key="proof.id"><p role="status">{{t('wallet.proof.'+proof.status)}}</p><p v-if="proof.bankReference">{{t('wallet.proof.bankReference')}}: {{proof.bankReference}}</p><a :href="proof.imageUrl" target="_blank" rel="noopener">{{t('wallet.proof.view')}}</a></article><button type="button" :disabled="busy" @click="run(refresh)">{{t('wallet.refresh')}}</button></section></template>
