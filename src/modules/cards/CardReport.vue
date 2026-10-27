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
<template>
  <section class="cards-panel cards-report">
    <header class="cards-section-heading"><h2>{{t('report')}}</h2></header>
    <form class="cards-report-filters" @submit.prevent="load"><label>{{t('dateFrom')}}<input v-model="from" type="date" required></label><label>{{t('dateTo')}}<input v-model="to" type="date" required></label><button class="primary" :disabled="busy">{{t('refresh')}}</button></form>
    <p v-if="busy" class="cards-caption" role="status">{{t('loading')}}</p><p v-if="error" class="cards-error" role="alert">{{error}}</p>
    <template v-if="report">
      <dl class="cards-report-totals"><div><dt>{{t('received')}}</dt><dd>{{money(report.money.receivedCents)}}</dd></div><div><dt>{{t('refundAmount')}}</dt><dd>{{money(report.money.refundedCents)}}</dd></div></dl>
      <h3 class="cards-subheading">{{t('usageSummary')}}</h3>
      <p v-if="!report.usage.length" class="cards-empty">{{t('noUsage')}}</p>
      <div v-else class="cards-table-scroll" role="region" :aria-label="t('usageSummary')" tabindex="0"><table class="cards-data-table"><thead><tr><th scope="col">{{t('plans')}}</th><th scope="col">{{t('stampAction')}}</th><th scope="col">{{t('stampReward')}}</th><th scope="col">{{t('redeem')}}</th><th scope="col">{{t('reverse')}}</th></tr></thead><tbody><tr v-for="row in report.usage" :key="row.planId"><th scope="row">{{displayName(row,locale)}}</th><td>{{row.stamps}}</td><td>{{row.rewards}} {{row.unit}}</td><td>{{row.redeemed}} {{row.unit}}</td><td>{{row.reversals}} {{row.unit}}</td></tr></tbody></table></div>
      <h3 class="cards-subheading">{{t('expiredSummary')}}</h3><p class="cards-caption">{{t('expiredUnused')}}</p>
      <p v-if="!report.expiredDaily.length" class="cards-empty">{{t('noExpired')}}</p>
      <div v-else class="cards-table-scroll" role="region" :aria-label="t('expiredSummary')" tabindex="0"><table class="cards-data-table"><thead><tr><th scope="col">{{t('plans')}}</th><th scope="col">{{t('quantity')}}</th></tr></thead><tbody><tr v-for="row in report.expiredDaily" :key="row.planId"><th scope="row">{{displayName(row,locale)}}</th><td>{{row.unused}} {{row.unit}}</td></tr></tbody></table></div>
    </template>
  </section>
</template>
