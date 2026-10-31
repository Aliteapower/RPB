<script setup lang="ts">
import {useAuthSessionStore} from '../../stores/authSession'
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
const auth=useAuthSessionStore()
const route=useRoute(),historyOnly=computed(()=>route.query.history==='1')
watch(historyOnly,closed=>{if(closed)tab.value='records'},{immediate:true})
const historyCan=(permission:string)=>!historyOnly.value&&can(permission)
const changed=()=>{pending.value=!!api().pending();void run(load)}
async function toggle(){await run(async()=>{const result=await api().put<{enabled:boolean}>('/settings',{enabled:!enabled.value});enabled.value=result.enabled})}
function fail(message:string){error.value=message;pending.value=!!api().pending()}
</script>
<template><div class="cards-page cards-staff-page"><div class="cards-workspace-heading cards-page-tools" role="group" :aria-label="t('title')"><div class="cards-heading-actions"><label v-if="!historyOnly&&can('wallet.cards.manage')" class="check cards-feature-toggle"><input :checked="enabled" type="checkbox" :disabled="busy" @change="toggle">{{t('enabled')}}</label><RouterLink v-if="auth.isTenantAdmin||can('wallet.settings.manage')" :to="`/stores/${store}/wallet/settings`">{{t('settings')}}</RouterLink><RouterLink v-if="auth.isTenantAdmin && can('catalog.view')" :to="`/stores/${store}/catalog`">{{t('catalog')}}</RouterLink></div></div><p v-if="error" class="cards-error" role="alert">{{error}}</p><p v-if="notice" role="status">{{t('recovered')}}</p><button v-if="pending&&!historyOnly" :disabled="busy" @click="run(recover)">{{t('recover')}}</button><p v-if="busy">{{t('loading')}}</p><p v-if="!enabled" class="cards-notice">{{t('disabled')}}</p><nav class="cards-workspace-tabs" :aria-label="t('title')"><button v-for="v in (historyOnly?['purchases','records','report']:['holdings','plans','purchases','records','report'])" :key="v" :class="{primary:tab===v}" :aria-pressed="tab===v" @click="tab=v">{{t(v)}}</button></nav><CardOperations v-if="!historyOnly&&tab==='holdings'" :store="store" :plans="plans" :holdings="holdings" :disabled="busy||pending||!enabled" :can="historyCan" @changed="changed" @error="fail"/>
<section v-if="!historyOnly&&tab==='plans'" class="cards-panel cards-plans">
  <header class="cards-section-heading"><div><h2>{{t('plans')}}</h2><p class="cards-caption">{{t('snapshot')}}</p></div><button v-if="can('wallet.cards.manage')" class="primary" :disabled="busy||pending||!enabled" :aria-expanded="showEditor" @click="showEditor=!showEditor">{{t(showEditor?'cancel':'create')}}</button></header>
  <CardPlanEditor v-if="showEditor" :store="store" :disabled="busy||pending||!enabled" @saved="showEditor=false;changed()" @error="fail"/>
  <p v-if="!plans.length" class="cards-empty">{{t('noPlans')}}</p>
  <div v-else class="cards-list">
    <article v-for="p in plans" :key="p.id" class="cards-list-item cards-plan-item">
      <div class="cards-item-heading"><div class="cards-item-title"><h3>{{displayName(p,locale)}}</h3><span class="cards-badge">{{t(p.kind)}}</span><span class="cards-badge" :data-status="p.status">{{t(p.status)}}</span></div><strong class="cards-amount">{{money(p.priceCents)}}</strong><button v-if="can('wallet.cards.manage')" :disabled="busy||pending" @click="run(async()=>{await api().command(`/plans/${p.id}/status`,{status:p.status==='active'?'inactive':'active',expectedVersion:p.version},'plan.status');await load()})">{{t(p.status==='active'?'inactive':'active')}}</button></div>
      <p class="cards-plan-product">{{displayName(p.product,locale)}} <span>/ {{p.product.unit}} · {{t(p.taxMode)}}</span></p>
      <dl class="cards-plan-values"><div><dt>{{t('validity')}}</dt><dd>{{p.validDays}} {{t('daysUnit')}}</dd></div><div><dt>{{t('dailyAllowance')}}</dt><dd>{{p.dailyLimit??t('unlimited')}}</dd></div><div><dt>{{t('totalUses')}}</dt><dd>{{p.total??t('unlimited')}}</dd></div></dl>
    </article>
  </div>
</section>
<CardPurchasePanel v-if="tab==='purchases'" :store="store" :orders="purchases" :can="historyCan" :disabled="busy||pending" @changed="changed" @error="fail"/><CardReport v-if="tab==='report'" :store="store"/><CardHistory v-if="tab==='records'" :store="store" :entries="entries" :holdings="holdings" :can-reverse="!historyOnly&&can('wallet.cards.reverse')" :disabled="busy||pending" @changed="changed" @error="fail"/><button v-if="(tab==='records'&&moreEntries)||(tab==='purchases'&&morePurchases)" :disabled="busy" @click="run(()=>loadMore(tab==='records'?'entries':'purchases'))">{{t('loadMore')}}</button></div></template>
