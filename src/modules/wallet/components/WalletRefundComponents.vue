<script setup lang="ts">
import WalletRefundAmounts from './WalletRefundAmounts.vue'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { walletApi } from '../api'
import { useWalletTask } from '../useWalletTask'
import type { RefundPreview, FundingLot } from '../marketingTypes'
import type { Account } from '../types'
import { money } from '../format'
const props=defineProps<{store:string;account:Account|null;sourceType:string;sourceId:string;amount:string;mode:string;reason:string}>(),emit=defineEmits<{preview:[value:RefundPreview|null];changed:[]}>(),{t}=useI18n(),{busy,error,run}=useWalletTask(),preview=ref<RefundPreview|null>(null),lots=ref<FundingLot[]>([]),chosen=ref(''),confirmed=ref(false)
const bonusLot=computed(()=>lots.value.find(l=>l.id===chosen.value))
let generation=0
watch(()=>[props.store,props.sourceType,props.sourceId,props.amount,props.mode],()=>{generation++;preview.value=null;emit('preview',null)})
watch(()=>[props.store,props.account?.id,props.account?.version],async()=>{const current=++generation;lots.value=[];chosen.value='';confirmed.value=false;if(!props.account)return;await run(async()=>{const result=await walletApi(props.store).get<FundingLot[]>(`/accounts/${props.account!.id}/funding-lots`);if(current===generation)lots.value=result})},{immediate:true})
async function review(){if(!props.sourceId)return;const current=generation;await run(async()=>{const result=await walletApi(props.store).post<RefundPreview>('/refund-previews',{sourceType:props.sourceType,sourceId:props.sourceId,...(props.mode==='remaining_lot_exit'?{mode:props.mode}:{amount:props.amount})});if(current!==generation)return;preview.value=result;emit('preview',result)})}
async function exit(){const lot=bonusLot.value;if(!lot||!props.account||!confirmed.value||!props.reason.trim())return;await run(async()=>{await walletApi(props.store).command(`/funding-lots/${lot.id}/exit-bonus`,{accountId:props.account!.id,expectedVersion:props.account!.version,reason:props.reason});confirmed.value=false;chosen.value='';emit('changed')})}
</script>
<template><p v-if="error" class="wallet-error" role="alert">{{error}}</p><button type="button" :disabled="busy||!sourceId||(mode!=='remaining_lot_exit'&&!amount)" @click="review">{{t('wallet.marketing.preview')}}</button><WalletRefundAmounts v-if="preview" :amounts="preview" /><details v-if="account"><summary>{{t('wallet.marketing.exitBonus')}}</summary><p>{{t('wallet.marketing.noCash')}}</p><label>{{t('wallet.marketing.lot')}}<select v-model="chosen"><option value="">—</option><option v-for="lot in lots.filter(l=>l.principal===0&&l.bonus>0)" :key="lot.id" :value="lot.id">{{lot.id}} · {{money(lot.bonus)}}</option></select></label><p v-if="bonusLot">{{t('wallet.marketing.frozen')}} {{money(bonusLot.reservedP+bonusLot.reservedG)}}</p><label><input v-model="confirmed" type="checkbox">{{t('wallet.marketing.noCash')}}</label><button type="button" :disabled="busy||!chosen||!confirmed||!reason.trim()||!!bonusLot?.reservedG" @click="exit">{{t('wallet.marketing.exitBonus')}}</button></details></template>
