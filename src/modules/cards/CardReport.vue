<script setup lang="ts">
import {ref,watch} from 'vue'
import {cardApi} from './api'
import {useCardText} from './text'
import {money,businessDay,displayName} from './format'
const props=defineProps<{store:string}>(),{t,locale}=useCardText(),from=ref(businessDay('Asia/Singapore').slice(0,8)+'01'),to=ref(businessDay('Asia/Singapore')),busy=ref(false),error=ref('')
interface Report {expiredDaily:{planId:string;nameZh:string;nameEn:string;unit:string;unused:number}[];money:{receivedCents:number;refundedCents:number};usage:{planId:string;nameZh:string;nameEn:string;unit:string;stamps:number;redeemed:number;rewards:number;reversals:number}[]}
const report=ref<Report|null>(null);let revision=0
async function load(){const g=++revision,store=props.store;busy.value=true;error.value='';try{const value=await cardApi(store).get<Report>(`/summary?from=${from.value}&to=${to.value}`);if(g===revision)report.value=value}catch(e){if(g===revision)error.value=String(e)}finally{if(g===revision)busy.value=false}}
watch(()=>props.store,()=>{report.value=null;void load()},{immediate:true})
</script>
<template><section class="cards-panel"><h2>{{t('report')}}</h2><form class="cards-actions" @submit.prevent="load"><input v-model="from" type="date" required style="max-width:200px"><span>→</span><input v-model="to" type="date" required style="max-width:200px"><button :disabled="busy">{{t('refresh')}}</button></form><p v-if="error" class="cards-error">{{error}}</p><template v-if="report"><div class="cards-grid"><div class="cards-notice">{{t('received')}}<h2>{{money(report.money.receivedCents)}}</h2></div><div class="cards-notice">{{t('refunded')}}<h2>{{money(report.money.refundedCents)}}</h2></div></div><article v-for="row in report.usage" :key="row.planId" class="cards-panel"><h3>{{displayName(row,locale)}}</h3><dl><dt>{{t('stamp')}}</dt><dd>{{row.stamps}}</dd><dt>{{t('rewards')}}</dt><dd>{{row.rewards}} {{row.unit}}</dd><dt>{{t('redeem')}}</dt><dd>{{row.redeemed}} {{row.unit}}</dd><dt>{{t('reverse')}}</dt><dd>{{row.reversals}} {{row.unit}}</dd></dl></article><article v-for="row in report.expiredDaily" :key="`expired-${row.planId}`" class="cards-panel"><strong>{{displayName(row,locale)}}</strong><p>{{t('expiredUnused')}}: {{row.unused}} {{row.unit}}</p></article></template></section></template>
