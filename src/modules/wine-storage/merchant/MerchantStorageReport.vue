<script setup lang="ts">
import {computed,nextTick,onUnmounted,ref,watch} from 'vue'
import type {StorageApi} from '../api'
import {StorageApiError} from '../api'
import type {ReportPage,ReportRow,ReportDetail} from './reportTypes'
import StorageReportDetail from './StorageReportDetail.vue'
import {useMerchantStorageText} from './text'
import {dateTime} from '../format'
import './report.css'
const props=defineProps<{api:StorageApi;timezone:string;refreshKey:number}>()
const {locale}=useMerchantStorageText(),x=(zh:string,en:string)=>locale.value.startsWith('en')?en:zh
const kind=ref('deposit'),query=ref(''),status=ref('all'),from=ref(''),to=ref(''),page=ref(0),data=ref<ReportPage|null>(null),loading=ref(false),error=ref(''),selected=ref<ReportRow|null>(null),detail=ref<ReportDetail|null>(null),detailLoading=ref(false),detailError=ref('')
const selectedPanel=ref<HTMLElement|null>(null)
const applied=ref({kind:'deposit',query:'',status:'all',from:'',to:''})
const dirty=computed(()=>JSON.stringify(filters())!==JSON.stringify(applied.value))
const statuses=computed(()=>kind.value==='deposit'?['stored','pending','cancelled','rejected']:['completed','authorized','awaiting_authorization','cancelled','expired'])
function label(value:string){return ({all:x('全部状态','All statuses'),stored:x('已收存','Received'),pending:x('待收存','Awaiting receipt'),completed:x('已取走','Collected'),authorized:x('待领取','Awaiting pickup'),awaiting_authorization:x('待确认','Awaiting confirmation'),cancelled:x('已取消','Cancelled'),rejected:x('已拒绝','Rejected'),expired:x('码已过期','Code expired')} as Record<string,string>)[value]||value}
function filters(){return {kind:kind.value,query:query.value.trim(),status:status.value,from:from.value,to:to.value}}
function wine(row:ReportRow){return (locale.value.startsWith('en')?row.winesEn||row.winesZh:row.winesZh||row.winesEn)||'—'}
let generation=0,detailGeneration=0,active=true
function close(){detailGeneration++;selected.value=null;detail.value=null;detailLoading.value=false;detailError.value=''}
async function load(){
 const request=++generation,api=props.api;close();loading.value=true;error.value='';data.value=null
 try{const result=await api.get<ReportPage>('/reports?'+new URLSearchParams({...applied.value,page:String(page.value),size:'20'}));if(active&&request===generation)data.value=result}
 catch(e){if(active&&request===generation)error.value=e instanceof Error?e.message:String(e)}
 finally{if(active&&request===generation)loading.value=false}
}
function search(){if(from.value&&to.value&&from.value>to.value){error.value=x('开始日期不能晚于结束日期','Start date must be before end date');return}applied.value=filters();page.value=0;void load()}
function reset(){query.value='';status.value='all';from.value='';to.value='';search()}
function changeKind(){status.value='all';search()}
function move(offset:number){if(loading.value||dirty.value||!data.value||offset<0&&page.value===0||offset>0&&!data.value.hasMore)return;page.value+=offset;void load()}
async function show(row:ReportRow){
 const request=++detailGeneration,epoch=generation,api=props.api;selected.value=row;detail.value=null;detailError.value='';detailLoading.value=true
 await nextTick();if(active&&request===detailGeneration){selectedPanel.value?.focus?.({preventScroll:true});selectedPanel.value?.scrollIntoView?.({block:'start',behavior:'smooth'})}
 try{const value=await api.get<ReportDetail>(`/reports/${row.kind}/${encodeURIComponent(row.id)}`);if(active&&request===detailGeneration&&epoch===generation)detail.value=value}
 catch(e){if(active&&request===detailGeneration&&epoch===generation){detailError.value=e instanceof Error?e.message:String(e);if(e instanceof StorageApiError&&[401,403].includes(e.status)){close();data.value=null;error.value=e.message}}}
 finally{if(active&&request===detailGeneration)detailLoading.value=false}
}
watch(()=>[props.api,props.refreshKey],()=>{page.value=0;void load()},{immediate:true})
onUnmounted(()=>{active=false;generation++;detailGeneration++})
</script>
<template><section class="storage-report" :aria-label="x('存取酒报表','Storage reports')" :aria-busy="loading">
 <form class="storage-panel storage-report-filters" @submit.prevent="search">
  <label>{{x('报表类型','Report type')}}<select v-model="kind" :disabled="loading" @change="changeKind"><option value="deposit">{{x('存酒记录','Deposits')}}</option><option value="pickup">{{x('取酒记录','Pickups')}}</option></select></label>
  <label class="storage-report-keyword">{{x('顾客 / 酒品 / 位置','Customer / wine / location')}}<input v-model="query" maxlength="120" :placeholder="x('姓名、手机号、酒名或标签','Name, phone, wine or label')"></label>
  <label>{{x('状态','Status')}}<select v-model="status"><option value="all">{{label('all')}}</option><option v-for="value in statuses" :key="value" :value="value">{{label(value)}}</option></select></label>
  <label>{{x('开始日期','From')}}<input v-model="from" type="date" :max="to||undefined"></label><label>{{x('结束日期','To')}}<input v-model="to" type="date" :min="from||undefined"></label>
  <div class="storage-report-filter-actions"><button class="primary" :disabled="loading">{{x('查询','Search')}}</button><button type="button" :disabled="loading" @click="reset">{{x('重置','Reset')}}</button></div>
 </form>
 <p class="storage-report-help">{{dirty?x('筛选已更改，点击查询更新结果。','Filters changed. Search to update results.'):x('按办理时间查询，每笔存酒或取酒单只列一行；汇总覆盖全部查询结果。','Search by processing date. One row per bill; totals cover all matching results.')}}</p>
 <p v-if="loading" role="status">{{x('查询中…','Loading…')}}</p><div v-else-if="error" class="storage-panel" role="alert"><p class="storage-error">{{error}}</p><button @click="search">{{x('重新查询','Retry')}}</button></div>
 <template v-else-if="data">
  <div class="storage-report-totals"><div><strong>{{data.summary.recordCount}}</strong><span>{{x('单据','Records')}}</span></div><div><strong>{{data.summary.completedCount}}</strong><span>{{x('已完成','Completed')}}</span></div><div><strong>{{data.summary.bottleCount}}</strong><span>{{applied.kind==='deposit'?x('实收瓶数','Received bottles'):x('实取瓶数','Collected bottles')}}</span></div></div>
  <p class="storage-report-help">{{x('仅已收存 / 已取走计入实收、实取瓶数；不是当前库存。','Only completed receipts / pickups count toward bottles; this is not current stock.')}}</p>
  <p v-if="!data.items.length" class="storage-panel storage-empty">{{x('没有符合条件的记录','No matching records')}}</p>
  <div v-else class="storage-report-results"><table><thead><tr><th>{{x('办理时间','Processed')}}</th><th>{{x('顾客','Customer')}}</th><th>{{x('酒品 / 数量','Wine / quantity')}}</th><th>{{x('状态','Status')}}</th><th>{{x('存放位置 / 标签','Location / label')}}</th><th>{{x('明细','Details')}}</th></tr></thead><tbody><tr v-for="row in data.items" :key="row.id" :class="{'is-selected':selected?.id===row.id}"><td class="report-time">{{dateTime(row.occurredAt,timezone)}}</td><td class="report-customer"><strong>{{row.customerName||x('姓名未记录','Name unavailable')}}</strong><small>{{row.customerPhone||'—'}}</small></td><td class="report-wine"><strong>{{wine(row)}}</strong><small>{{['stored','completed'].includes(row.status)?x('实际','Actual'):x('申请','Requested')}} {{row.quantity}} {{x('瓶','bottles')}}</small></td><td class="report-status"><span :class="['storage-report-status','status-'+row.status]">{{label(row.status)}}</span></td><td class="report-location">{{row.locations||'—'}}</td><td class="report-action"><button type="button" :aria-expanded="selected?.id===row.id" @click="show(row)">{{x('详情','Details')}}</button></td></tr></tbody></table></div>
  <nav class="storage-report-pages" :aria-label="x('报表分页','Report pages')"><span>{{x('共','Total')}} {{data.summary.recordCount}} {{x('笔','records')}} · {{x('第','Page')}} {{page+1}} / {{Math.max(1,Math.ceil(data.summary.recordCount/20))}}</span><div><button :disabled="loading||dirty||page===0" @click="move(-1)">{{x('上一页','Previous')}}</button><button :disabled="loading||dirty||!data.hasMore" @click="move(1)">{{x('下一页','Next')}}</button></div></nav>
 </template>
 <section v-if="selected" ref="selectedPanel" tabindex="-1" class="storage-report-selected"><p v-if="detailLoading" role="status">{{x('明细加载中…','Loading details…')}}</p><div v-else-if="detailError" role="alert"><p>{{detailError}}</p><button @click="show(selected!)">{{x('重试明细','Retry details')}}</button><button @click="close">{{x('关闭','Close')}}</button></div><StorageReportDetail v-else-if="detail" :detail="detail" :row="selected" :api="api" :timezone="timezone" @close="close"/></section>
</section></template>
