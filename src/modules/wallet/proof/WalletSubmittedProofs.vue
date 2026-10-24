<script setup lang="ts">
import {ref,watch,onUnmounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {walletApi} from '../api'
import {money} from '../format'
import type {WalletProof} from './types'
const props=defineProps<{store:string;sourceType:string;sourceId:string;channel:string;amount:string;disabled:boolean}>()
const emit=defineEmits<{select:[proof:WalletProof]}>(),{t}=useI18n(),records=ref<WalletProof[]>([]),error=ref(''),loading=ref(false)
let generation=0
async function refresh(){const version=++generation;records.value=[];error.value='';loading.value=true;try{const value=await walletApi(props.store).get<WalletProof[]>(`/collection-proofs/${props.sourceType}/${encodeURIComponent(props.sourceId)}/submitted`);if(version===generation)records.value=value}catch(e){if(version===generation)error.value=e instanceof Error?e.message:String(e)}finally{if(version===generation)loading.value=false}}
watch(()=>[props.store,props.sourceType,props.sourceId],refresh,{immediate:true});onUnmounted(()=>generation++)
</script>
<template><section><h4>{{t('wallet.proof.customerSubmitted')}}</h4><button type="button" :disabled="disabled||loading" @click="refresh">{{t('wallet.refresh')}}</button><p v-if="loading" role="status">{{t('wallet.loading')}}</p><p v-if="error" class="wallet-error">{{error}}</p><p v-if="!loading&&!error&&!records.length">{{t('wallet.proof.noCustomerProof')}}</p><article v-for="record in records" :key="record.id"><p>{{money(record.context.amountCents)}} · {{record.bankReference||t('wallet.proof.missing')}}</p><a :href="record.imageUrl" target="_blank" rel="noopener">{{t('wallet.proof.view')}}</a><button type="button" :disabled="disabled||record.context.channel!==channel||record.context.amountCents!==Math.round(Number(amount)*100)" @click="emit('select',record)">{{t('wallet.proof.useCustomerProof')}}</button></article></section></template>
