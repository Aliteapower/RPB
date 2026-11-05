<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { TopupOffer } from '../marketingTypes'
const props=defineProps<{base?:boolean}>()
const model=defineModel<TopupOffer[]>({required:true}),{t}=useI18n()
function add(){model.value=[...model.value,{id:crypto.randomUUID(),version:1,principalCents:0,bonusCents:0,creditTotalCents:0,enabled:true}]}
function set(index:number,field:'principalCents'|'bonusCents',event:Event){const raw=(event.target as HTMLInputElement).value;if(!/^\d+(?:\.\d{0,2})?$/.test(raw))return;const offer=model.value[index]!;offer[field]=Math.round(Number(raw)*100);offer.creditTotalCents=offer.principalCents+offer.bonusCents}
function seed(){model.value=[100,300,500,1000].map((n,i)=>({id:crypto.randomUUID(),version:1,principalCents:n*100,bonusCents:(props.base?0:[1000,5000,8000,20000][i]!),creditTotalCents:n*100+(props.base?0:[1000,5000,8000,20000][i]!),enabled:true}))}
</script>
<template><fieldset><legend>{{t('wallet.marketing.offers')}}</legend><div v-for="(offer,index) in model" :key="offer.id" class="wallet-offer-editor"><label>{{t('wallet.marketing.principal')}}<input :value="(offer.principalCents/100).toFixed(2)" type="number" min="0.01" step="0.01" required @input="set(index,'principalCents',$event)"></label><label>{{t('wallet.marketing.bonus')}}<input :value="(offer.bonusCents/100).toFixed(2)" type="number" min="0" step="0.01" :disabled="base" required @input="set(index,'bonusCents',$event)"></label><button type="button" @click="model=model.filter((_,i)=>i!==index)">{{t('wallet.marketing.remove')}}</button></div><button type="button" :disabled="model.length>=50" @click="add">{{t('wallet.marketing.addOffer')}}</button><button v-if="!model.length" type="button" @click="seed">{{t('wallet.marketing.seed')}}</button></fieldset></template>
