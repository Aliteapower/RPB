<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useRoute} from 'vue-router'
import type {Home,Intent,Item} from '../types'
import type {StorageApi} from '../api'
import StorageReceiptSummary from '../StorageReceiptSummary.vue'
import StorageApplicationEvidence from '../StorageApplicationEvidence.vue'
import StorageIntentPanel from '../StorageIntentPanel.vue'
import {dateTime} from '../format'
import {displayName} from '../../shared-commerce/format'
import {useCustomerStorageText} from './text'
const props=defineProps<{home:Home;api:StorageApi;disabled:boolean;activeIntent:Intent|null;scannedCode:string}>(),emit=defineEmits<{command:[()=>Promise<unknown>];deposit:[];again:[{item:Item;intentId:string;quantity:number}];cleared:[]}>(),route=useRoute(),{t,locale}=useCustomerStorageText()
const section=ref('requests');watch(()=>route.query.record,value=>{section.value=value==='collections'||value==='pastBottles'?value:'requests'},{immediate:true})
const intents=computed(()=>{const all=props.home.intents.slice();if(props.activeIntent&&!all.some(i=>i.id===props.activeIntent?.id))all.unshift(props.activeIntent);return all.map(i=>({...i,code:i.id===props.activeIntent?.id&&['authorized','awaiting_authorization'].includes(i.status)&&Date.parse(i.expiresAt)>Date.now()?props.activeIntent.code:null}))})
const past=computed(()=>props.home.items.filter(i=>i.status!=='stored'||i.remaining<=0||Date.parse(i.expiresAt)<=Date.now()))
</script>
<template><section class="storage-section">
 <nav class="storage-record-tabs"><button v-for="key in ['requests','collections','pastBottles']" :key="key" :aria-pressed="section===key" :class="{primary:section===key}" :disabled="disabled" @click="section=key">{{t(key)}}</button></nav>
 <div v-if="section==='requests'" class="storage-panel"><h2>{{t('requests')}}</h2><p v-if="!home.applications.length" class="storage-empty">{{t('noRequests')}}</p>
  <article v-for="application in home.applications" :key="application.id" class="storage-panel"><span class="cards-badge">{{t(application.status)}}</span><span v-if="home.events.some(e=>e.sourceId===application.id&&e.action==='staff.registered')" class="cards-badge">{{t('staff.registered')}}</span><p>{{dateTime(application.createdAt,home.timezone)}}</p><p v-for="line in application.lines" :key="line.id"><strong>{{displayName(line,locale)}}</strong> × {{line.quantity}} {{t('bottleUnit')}}</p><StorageReceiptSummary :application="application"/><p v-if="home.items.find(item=>item.applicationId===application.id)?.snapshot.custodian">{{t('custodianOptional')}}：{{home.items.find(item=>item.applicationId===application.id)!.snapshot.custodian!.name}}</p><p v-if="application.reason">{{application.reason}}</p><StorageApplicationEvidence :application="application" :api="api"/><button v-if="application.status==='pending'" :disabled="disabled" @click="emit('command',()=>api.command(`/applications/${application.id}/cancel`,{}))">{{t('cancel')}}</button></article>
  <button :disabled="disabled" @click="emit('deposit')">{{t('deposit')}}</button>
 </div>
 <div v-if="section==='collections'"><h2>{{t('collections')}}</h2><p v-if="!intents.length" class="storage-panel storage-empty">{{t('noCollections')}}</p><StorageIntentPanel v-for="intent in intents" :key="intent.id" :intent="intent" :items="home.items" customer :timezone="home.timezone" :disabled="disabled" :can-deliver="false" :authorization-code="activeIntent?.id===intent.id?scannedCode:''" @authorize="emit('command',()=>api.command(`/intents/${intent.id}/authorize`,{code:scannedCode}))" @cancel="emit('command',async()=>{await api.command(`/intents/${intent.id}/cancel`,{});emit('cleared')})" @again="emit('again',$event)"/></div>
 <div v-if="section==='pastBottles'" class="storage-panel"><h2>{{t('pastBottles')}}</h2><p v-if="!past.length" class="storage-empty">{{t('noPastBottles')}}</p><article v-for="item in past" :key="item.id" class="storage-panel"><h3>{{displayName(item.snapshot,locale)}}</h3><span class="cards-badge">{{t(Date.parse(item.expiresAt)<=Date.now()&&item.status==='stored'?'expired':item.status)}}</span><p>{{t('remaining')}} {{item.remaining}} / {{item.quantity}} {{t('bottleUnit')}}</p><small>{{t('expiresAt')}} {{dateTime(item.expiresAt,item.snapshot.timezone)}}</small></article></div>
</section></template>
