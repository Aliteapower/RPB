<script setup lang="ts">
import type {ReportDetail,ReportRow} from './reportTypes'
import type {StorageApi} from '../api'
import {useMerchantStorageText} from './text'
import {dateTime,storageLocationText} from '../format'
import {displayName} from '../../shared-commerce/format'
import StorageReceiptSummary from '../StorageReceiptSummary.vue'
import StorageApplicationEvidence from '../StorageApplicationEvidence.vue'
import StorageItemPhotos from '../StorageItemPhotos.vue'
defineProps<{detail:ReportDetail;row:ReportRow;api:StorageApi;timezone:string}>()
defineEmits<{close:[]}>()
const {locale}=useMerchantStorageText(),x=(zh:string,en:string)=>locale.value.startsWith('en')?en:zh
function action(value:string){return ({received:x('确认收存','Receipt confirmed'),'staff.registered':x('商户登记','Staff registration'),applied:x('提交申请','Request submitted'),delivered:x('实物交付','Physical handover'),'staff.delivery_registered':x('门店登记取酒','Staff pickup'),'intent.created':x('发起取酒单','Pickup created'),authorized:x('授权取酒','Pickup authorized'),'intent.cancelled':x('取消取酒单','Pickup cancelled'),cancelled:x('取消寄存','Deposit cancelled'),rejected:x('拒绝寄存','Deposit rejected'),correction:x('追加说明','Note added'),'photos.added':x('补充照片','Photos added')} as Record<string,string>)[value]||x('其他操作','Other action')}
</script>
<template><section class="storage-panel storage-report-detail" :aria-label="x('单据详情','Record details')">
 <header><h2>{{x('单据详情','Record details')}} · {{row.customerName||row.customerPhone||x('姓名未记录','Name unavailable')}}</h2><button type="button" @click="$emit('close')">{{x('关闭详情','Close details')}}</button></header>
 <p>{{dateTime(row.occurredAt,timezone)}} · {{row.kind==='deposit'?x('本单存酒','Deposit quantity'):x('本单取酒','Pickup quantity')}} {{row.quantity}} {{x('瓶','bottles')}}</p>
 <p class="cards-muted">{{x('仅供查询核对，不办理存取酒。','Read-only evidence; no deposit or handover actions.')}}</p>
 <template v-if="detail.application"><StorageReceiptSummary :application="detail.application"/><p v-if="detail.application.reason">{{x('登记说明','Registration note')}}：{{detail.application.reason}}</p><p v-if="detail.application.confirmation?.reason">{{x('核实说明','Receipt note')}}：{{detail.application.confirmation.reason}}</p><StorageApplicationEvidence :application="detail.application" :api="api"/></template>
 <div v-for="item in detail.items" :key="item.id" class="storage-report-detail-item"><strong>{{displayName(item.snapshot,locale)}}</strong><p v-if="detail.intent">{{x('本单取酒','This pickup')}} {{detail.intent.selections.find(line=>line.itemId===item.id)?.quantity}} {{x('瓶','bottles')}}</p><span>{{x('原存','Originally stored')}} {{item.quantity}} · {{x('现余','Current balance')}} {{item.remaining}}</span><p>{{storageLocationText(item)}}<template v-if="item.label"> · {{item.label}}</template><template v-if="item.snapshot.custodian"> · {{x('保管人','Custodian')}} {{item.snapshot.custodian.name}}</template></p><p v-if="item.snapshot.note">{{item.snapshot.note}}</p><StorageItemPhotos :item="item" :api="api" :events="detail.events"/></div>
 <details v-if="detail.events.length"><summary>{{x('查看办理日志','Show audit trail')}} · {{detail.events.length}}</summary><div v-for="event in detail.events" :key="event.id" class="storage-report-event"><strong>{{action(event.action)}}</strong><time>{{dateTime(event.createdAt,timezone)}}</time><span>{{event.actorName||x('操作人未记录','Operator unavailable')}}</span><p v-if="event.details.reason">{{event.details.reason}}</p><p v-if="event.details.note">{{event.details.note}}</p></div></details>
</section></template>
