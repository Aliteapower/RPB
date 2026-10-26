<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useRoute} from 'vue-router'
import {useCardsWorkspace} from './useCardsWorkspace'
import {useCardText} from './text'
import {displayName,money} from './format'
import CardPlanEditor from './CardPlanEditor.vue'
import CardOperations from './CardOperations.vue'
import CardPurchasePanel from './CardPurchasePanel.vue'
import CardHistory from './CardHistory.vue'
import CardReport from './CardReport.vue'
import './cards.css'
const {t,locale}=useCardText(),{store,busy,error,notice,plans,holdings,entries,purchases,enabled,pending,can,api,run,load,recover,loadMore,moreEntries,morePurchases}=useCardsWorkspace(),tab=ref('holdings'),showEditor=ref(false)
const route=useRoute(),historyOnly=computed(()=>route.query.history==='1')
watch(historyOnly,closed=>{if(closed)tab.value='records'},{immediate:true})
const historyCan=(permission:string)=>!historyOnly.value&&can(permission)
const changed=()=>{pending.value=!!api().pending();void run(load)}
async function toggle(){await run(async()=>{const result=await api().put<{enabled:boolean}>('/settings',{enabled:!enabled.value});enabled.value=result.enabled})}
function fail(message:string){error.value=message;pending.value=!!api().pending()}
</script>
<template><div class="cards-page"><nav><h1>{{t('title')}}</h1><RouterLink v-if="can('catalog.view')" :to="`/stores/${store}/catalog`">{{t('catalog')}}</RouterLink><button :disabled="busy" @click="run(load)">{{t('refresh')}}</button></nav><p v-if="error" class="cards-error" role="alert">{{error}}</p><p v-if="notice" role="status">{{t('recovered')}}</p><button v-if="pending&&!historyOnly" :disabled="busy" @click="run(recover)">{{t('recover')}}</button><p v-if="busy">{{t('loading')}}</p><label v-if="!historyOnly&&can('wallet.cards.manage')" class="check"><input :checked="enabled" type="checkbox" :disabled="busy" @change="toggle">{{t('enabled')}}</label><p v-if="!enabled" class="cards-notice">{{t('disabled')}}</p><nav><button v-for="v in (historyOnly?['purchases','records','report']:['holdings','plans','purchases','records','report'])" :key="v" :class="{primary:tab===v}" @click="tab=v">{{t(v)}}</button></nav><CardOperations v-if="!historyOnly&&tab==='holdings'" :store="store" :plans="plans" :holdings="holdings" :disabled="busy||pending||!enabled" :can="historyCan" @changed="changed" @error="fail"/>
<section v-if="!historyOnly&&tab==='plans'" class="cards-panel"><h2>{{t('plans')}}</h2><p class="cards-muted">{{t('snapshot')}}</p><button v-if="!historyOnly&&can('wallet.cards.manage')" :disabled="busy||pending||!enabled" @click="showEditor=!showEditor">{{t('create')}}</button><CardPlanEditor v-if="showEditor" :store="store" :disabled="busy||pending||!enabled" @saved="showEditor=false;changed()" @error="fail"/><p v-if="!plans.length">{{t('empty')}}</p><div class="cards-grid"><article v-for="p in plans" :key="p.id" class="cards-panel"><h3>{{displayName(p,locale)}}</h3><span class="cards-badge">{{t(p.kind)}} · {{t(p.status)}}</span><p>{{displayName(p.product,locale)}} / {{p.product.unit}}</p><p>{{money(p.priceCents)}} · {{t(p.taxMode)}} · {{t('days')}} {{p.validDays}}</p><p>{{t('daily')}} {{p.dailyLimit??t('unlimited')}} · {{t('total')}} {{p.total??t('unlimited')}}</p><button v-if="!historyOnly&&can('wallet.cards.manage')" :disabled="busy||pending" @click="run(async()=>{await api().command(`/plans/${p.id}/status`,{status:p.status==='active'?'inactive':'active',expectedVersion:p.version},'plan.status');await load()})">{{t(p.status==='active'?'inactive':'active')}}</button></article></div></section>
<CardPurchasePanel v-if="tab==='purchases'" :store="store" :orders="purchases" :can="historyCan" :disabled="busy||pending" @changed="changed" @error="fail"/><CardReport v-if="tab==='report'" :store="store"/><CardHistory v-if="tab==='records'" :store="store" :entries="entries" :holdings="holdings" :can-reverse="!historyOnly&&can('wallet.cards.reverse')" :disabled="busy||pending" @changed="changed" @error="fail"/><button v-if="(tab==='records'&&moreEntries)||(tab==='purchases'&&morePurchases)" :disabled="busy" @click="run(()=>loadMore(tab==='records'?'entries':'purchases'))">{{t('loadMore')}}</button></div></template>
