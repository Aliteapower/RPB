<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import type {Home,Event,Item} from '../types'
import type {StorageApi} from '../api'
import StorageReceiptSummary from '../StorageReceiptSummary.vue'
import StorageApplicationEvidence from '../StorageApplicationEvidence.vue'
import StorageFollowupForm from '../StorageFollowupForm.vue'
import StorageItemPhotos from '../StorageItemPhotos.vue'
import {itemPhotos} from '../itemPhotos'
import {displayName} from '../../shared-commerce/format'
import {dateTime,storageLocationText,storageExpiryText,storageExpired} from '../format'
import {receiptRows,depositFacts,collectionTime,collectionStatus} from './recordSummaries'
import {useMerchantStorageText} from './text'
import './records.css'
const props=defineProps<{home:Home;api:StorageApi;disabled:boolean;canCorrect:boolean;canPhoto:boolean}>()
const emit=defineEmits<{command:[()=>Promise<unknown>];error:[unknown];changed:[]}>(),correction=ref({itemId:'',reason:'',note:''})
const {t,locale}=useMerchantStorageText(),section=ref('requests'),x=(zh:string,en:string)=>locale.value.startsWith('en')?en:zh
watch(()=>props.canCorrect,value=>{if(!value)correction.value={itemId:'',reason:'',note:''}},{flush:'sync'})
const tabs=computed(()=>[{key:'requests',label:x('存酒记录','Deposits')},{key:'collections',label:x('取酒记录','Pickups')},{key:'holdings',label:x('酒品明细','Holdings')},{key:'activity',label:x('操作日志','Audit log')}])
function grouped<T>(rows:T[],key:(row:T)=>string){const map=new Map<string,T[]>();for(const row of rows){const id=key(row);const list=map.get(id)??[];list.push(row);map.set(id,list)}return map}
const eventsBySource=computed(()=>grouped(props.home.events,e=>e.sourceId)),itemsByApplication=computed(()=>grouped(props.home.items,i=>i.applicationId)),itemById=computed(()=>new Map(props.home.items.map(i=>[i.id,i])))
const deposits=computed(()=>props.home.applications.map(application=>{const items=itemsByApplication.value.get(application.id)??[];return{application,items,locations:[...new Set(items.map(storageLocationText).filter(v=>v!=='—'))],rows:receiptRows(application),facts:depositFacts(application,items,eventsBySource.value.get(application.id)??[])}}))
const pickups=computed(()=>props.home.intents.map(intent=>({intent,status:collectionStatus(intent),time:collectionTime(intent,eventsBySource.value.get(intent.id)??[])})))
function fullTime(value:string|null|undefined){if(!value)return x('时间未记录','Time unavailable');try{return dateTime(value,props.home.timezone)}catch{return x('时间未记录','Time unavailable')}}
function shortTime(value:string|null|undefined){return fullTime(value).replace(/:\d{2}$/,'')}
function depositStatus(status:string){return({stored:x('已收存','Received'),pending:x('待收存','Awaiting receipt'),cancelled:x('已取消','Cancelled'),rejected:x('已拒绝','Rejected')} as Record<string,string>)[status]??t(status)}
function pickupStatus(status:string){return({completed:x('已取走','Collected'),authorized:x('待领取','Awaiting pickup'),awaiting_authorization:x('待确认','Awaiting confirmation'),expired:x('已过期','Expired'),cancelled:x('已取消','Cancelled')} as Record<string,string>)[status]??t(status)}
function pickupTimeLabel(kind:string){return kind==='completed'?x('取走','Collected'):kind==='cancelled'?x('取消','Cancelled'):kind==='created'?x('发起','Created'):''}
function holdingStatus(item:Item){return item.remaining<=0?x('已取完','Collected'):storageExpired(item.expiresAt)?x('已到期','Expired'):t(item.status)}
function logTitle(event:Event){const labels:Record<string,[string,string]>={applied:['提交寄存申请','Storage requested'],received:['确认收存','Receipt confirmed'],'staff.registered':['商户登记存酒','Staff deposit registered'],'intent.created':['创建取酒单（未扣库存）','Pickup created (stock unchanged)'],authorized:['授权取酒（未扣库存）','Pickup authorized (stock unchanged)'],delivered:['实物交付并扣减存酒','Physical handover; stock deducted'],'staff.delivery_registered':['门店登记取酒完成','Staff pickup registered'],'intent.cancelled':['取消取酒单（未扣库存）','Pickup cancelled (stock unchanged)'],cancelled:['取消寄存申请','Storage request cancelled'],rejected:['拒绝寄存申请','Storage request rejected'],correction:['追加说明','Note appended'],'photos.added':['补充实物照片','Evidence photos added']};const label=labels[event.action];return label?x(...label):x('其他操作','Other operation')}
function submitCorrection(){if(!props.canCorrect||props.disabled||!correction.value.itemId||!correction.value.reason.trim()||!correction.value.note.trim())return;const draft={...correction.value};emit('command',async()=>{if(!props.canCorrect||props.disabled)return;await props.api.command(`/items/${draft.itemId}/corrections`,draft);if(correction.value.itemId===draft.itemId)correction.value.itemId=''})}
</script>
<template><section class="storage-section storage-records">
 <nav class="storage-records-tabs" :aria-label="x('记录类别','Record categories')"><button v-for="tab in tabs" :key="tab.key" type="button" :aria-pressed="section===tab.key" :class="{primary:section===tab.key}" @click="section=tab.key">{{tab.label}}</button></nav>
 <template v-if="section==='requests'">
  <p class="storage-records-hint">{{x('存酒办理历史，当前余量请看“酒品明细”。','Deposit history. See Holdings for current balances.')}}</p>
  <p v-if="!deposits.length" class="storage-empty">{{x('暂无存酒记录。','No deposit records.')}}</p>
  <article v-for="record in deposits" :key="record.application.id" class="storage-record-card">
   <header><div class="storage-record-tags"><strong>{{record.facts.staff?x('商户登记','Staff deposit'):x('寄存申请','Storage request')}}</strong><span class="storage-record-status" :class="'status-'+record.application.status">{{depositStatus(record.application.status)}}</span></div><time>{{record.facts.timeKind==='received'?x('收存','Received'):x('登记','Registered')}} {{shortTime(record.facts.at)}}</time></header>
   <div class="record-summary-lines"><div v-for="line in record.rows" :key="line.id"><strong>{{displayName(line,locale)||x('酒品名称未记录','Name unavailable')}}</strong><span>{{record.facts.received!==null?x('实收','Received'):x('申请','Requested')}} <b>{{line.quantity}}</b> {{t('unit')}}</span></div></div>
   <p v-if="record.facts.different" class="storage-record-adjustment">{{x('数量有调整','Quantity adjusted')}}：{{x('申请','Requested')}} {{record.facts.requested}} → {{x('实收','Received')}} {{record.facts.received}} {{t('unit')}}</p>
   <p v-if="record.locations.length" class="storage-record-meta">{{t('location')}} {{record.locations.join('；')}}</p>
   <p v-if="record.facts.custodians.length||record.facts.operator" class="storage-record-meta"><span v-if="record.facts.operator">{{x('经办人','Handled by')}}：{{record.facts.operator}}</span><span v-if="record.facts.custodians.length">{{x('保管人','Custodian')}}：{{record.facts.custodians.join('、')}}</span></p>
   <details class="storage-record-details"><summary>{{x('查看详情与核实资料','Details and evidence')}}</summary><p>{{x('申请时间','Requested')}}：{{fullTime(record.application.createdAt)}}</p><p v-if="record.facts.timeKind==='received'">{{x('收存时间','Received')}}：{{fullTime(record.facts.at)}}</p><StorageReceiptSummary :application="record.application"/>
    <p v-if="record.application.reason">{{x('登记说明','Registration note')}}：{{record.application.reason}}</p><template v-if="record.application.confirmation"><p v-if="record.application.confirmation.reason">{{x('核实说明','Receipt note')}}：{{record.application.confirmation.reason}}</p><p v-for="(line,index) in record.application.confirmation.lines" :key="index">{{storageLocationText(line)}}<template v-if="line.label"> · {{t('label')}} {{line.label}}</template></p></template>
    <StorageApplicationEvidence :application="record.application" :api="api"/><StorageItemPhotos v-for="item in record.items" :key="item.id" :item="item" :api="api" :events="home.events"/>
   </details>
  </article>
 </template>
 <template v-if="section==='collections'">
  <p class="storage-records-hint">{{x('“已取走”才表示完成交付；待领取、已取消或已过期不扣减存酒。','Only Collected means handover is complete. Pending, cancelled or expired codes do not deduct stock.')}}</p>
  <p v-if="!pickups.length" class="storage-empty">{{x('暂无取酒记录。','No pickup records.')}}</p>
  <article v-for="record in pickups" :key="record.intent.id" class="storage-record-card">
   <header><div class="storage-record-tags"><strong>{{record.intent.origin==='customer'?x('顾客取酒码','Customer pickup code'):x('门店取酒单','Staff pickup')}}</strong><span class="storage-record-status" :class="'status-'+record.status">{{pickupStatus(record.status)}}</span></div><time>{{pickupTimeLabel(record.time.kind)}} {{shortTime(record.time.at)}}</time></header>
   <div class="record-summary-lines"><div v-for="line in record.intent.selections" :key="line.itemId"><strong>{{itemById.get(line.itemId)?displayName(itemById.get(line.itemId)!.snapshot,locale):x('酒品资料未记录','Holding unavailable')}}</strong><span><b>{{line.quantity}}</b> {{t('unit')}}</span></div></div>
   <details class="storage-record-details"><summary>{{x('查看取酒详情','Pickup details')}}</summary><p v-if="record.time.at">{{pickupTimeLabel(record.time.kind)}}：{{fullTime(record.time.at)}}</p><p v-if="!['completed','cancelled'].includes(record.status)">{{x('取酒码有效至','Code valid until')}}：{{fullTime(record.intent.expiresAt)}}</p><p v-for="line in record.intent.selections" :key="line.itemId">{{itemById.get(line.itemId)?storageLocationText(itemById.get(line.itemId)!):'—'}}<template v-if="itemById.get(line.itemId)?.label"> · {{t('label')}} {{itemById.get(line.itemId)!.label}}</template></p></details>
  </article>
 </template>
 <template v-if="section==='holdings'">
  <p class="storage-records-hint">{{x('按每批寄存酒品查看原存数量、当前余量、位置和到期时间。','Original quantity, current balance, location and expiry for each holding.')}}</p>
  <p v-if="!home.items.length" class="storage-empty">{{x('暂无寄存酒品。','No holdings.')}}</p>
  <article v-for="item in home.items" :key="item.id" class="storage-record-card">
   <header><strong>{{displayName(item.snapshot,locale)}}</strong><span class="storage-record-status" :class="item.remaining<=0?'status-collected':storageExpired(item.expiresAt)?'status-expired':'status-'+item.status">{{holdingStatus(item)}}</span></header>
   <p class="storage-record-balance">{{x('原存','Originally stored')}} {{item.quantity}} {{t('unit')}}<strong>{{t('remaining')}} {{item.remaining}} {{t('unit')}}</strong></p><p class="storage-record-meta">{{t('location')}} {{storageLocationText(item)}}<template v-if="item.label"> · {{t('label')}} {{item.label}}</template></p><p class="storage-record-meta">{{t('expiresAt')}} {{storageExpiryText(item.expiresAt,item.snapshot.timezone,t('unlimited'))}}</p><p v-if="item.snapshot.custodian" class="storage-record-meta">{{x('保管人','Custodian')}}：{{item.snapshot.custodian.name}}</p>
   <details v-if="itemPhotos(item,home.events).length" class="storage-record-details"><summary>{{x('查看照片','Photos')}} · {{itemPhotos(item,home.events).length}}</summary><StorageItemPhotos :item="item" :api="api" :events="home.events"/></details>
   <div v-if="canCorrect||canPhoto" class="storage-record-actions"><button v-if="canCorrect" type="button" :disabled="disabled" @click="correction={itemId:item.id,reason:'',note:''}">{{t('correction')}}</button><StorageFollowupForm v-if="canPhoto" :api="api" :item="item" :disabled="disabled" @saved="emit('changed')" @error="emit('error',$event)"/></div>
  </article>
  <form v-if="canCorrect&&correction.itemId" class="storage-panel" @submit.prevent="submitCorrection"><p>{{t('correctHint')}}</p><label>{{t('reason')}}<input v-model="correction.reason" required maxlength="2000" :disabled="disabled"></label><label>{{t('note')}}<textarea v-model="correction.note" maxlength="2000" :disabled="disabled" required/></label><button :disabled="disabled||!correction.reason.trim()||!correction.note.trim()">{{t('confirm')}}</button></form>
 </template>
 <template v-if="section==='activity'">
  <p class="storage-records-hint">{{x('用于追溯办理过程，不重复计算存酒或取酒数量。','For tracing the process; these events do not count as additional deposits or pickups.')}}</p><p v-if="!home.events.length" class="storage-empty">{{x('暂无操作日志。','No audit events.')}}</p>
  <article v-for="event in home.events" :key="event.id" class="storage-record-card storage-record-log"><header><strong>{{logTitle(event)}}</strong><time>{{fullTime(event.createdAt)}}</time></header><p class="storage-record-meta">{{x('操作人','Operator')}}：{{event.actorName?.trim()||t('unknownActor')}}</p><p v-if="event.action==='delivered'&&typeof event.details.quantity==='number'" class="storage-record-meta">{{x('本次取走','Collected')}} {{event.details.quantity}} {{t('unit')}}<template v-if="typeof event.details.before==='number'&&typeof event.details.after==='number'"> · {{x('余量','Balance')}} {{event.details.before}} → {{event.details.after}}</template></p><p v-if="event.details.reason" class="storage-record-meta">{{x('说明','Reason')}}：{{event.details.reason}}</p><p v-if="event.details.note" class="storage-record-meta">{{x('备注','Note')}}：{{event.details.note}}</p></article>
 </template>
</section></template>
