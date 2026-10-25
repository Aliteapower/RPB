<script setup lang="ts">
import {computed,ref,watch,onUnmounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useStaffWallet} from '../useStaffWallet'
import {walletApi} from '../api'
import {money} from '../format'
import {channelLabel} from '../collectionMethods'
import {reportTable,reportQuery,reportCsv,isPoints,rowKey,reportCategories,reportCategory,filterReportRows,type BusinessReport,type ReportPeriod,type ReportRow,type ReportCategory} from '../report/model'

const {t}=useI18n(),{store,context,can}=useStaffWallet()
const timezone=computed(()=>context.value?.store.timezone??'Asia/Singapore')
const today=()=>new Intl.DateTimeFormat('en-CA',{timeZone:timezone.value,year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date())
const period=ref<ReportPeriod>('day'),selected=ref(today()),report=ref<BusinessReport|null>(null),busy=ref(false),error=ref('')
const category=ref<ReportCategory>('all')
const availableCategories=computed(()=>reportCategories.filter(item=>report.value?.rows.some(row=>row.count>0&&reportCategory(row)===item)))
const filteredRows=computed(()=>filterReportRows(report.value?.rows??[],category.value))
const table=computed(()=>reportTable(filteredRows.value)),allowed=computed(()=>can('wallet.reconcile.view'))
let generation=0
onUnmounted(()=>generation++)
const value=(row:ReportRow|undefined,column:ReportRow)=>row?(isPoints(column)?new Intl.NumberFormat().format(row.amount):money(row.amount)):'—'
function label(row:ReportRow){const name=t(`wallet.businessReport.metric.${row.metric}`);return row.channel?`${name} · ${channelLabel(t,row.channel,undefined,row.channelName)}`:name}
function selectPeriod(next:ReportPeriod){if(next===period.value)return;const date=selected.value.slice(0,10);period.value=next;selected.value=next==='year'?date.slice(0,4):next==='month'?date.slice(0,7):date.length===4?`${date}-01-01`:date.length===7?`${date}-01`:date}
async function load(){
 const current=++generation,id=store.value;report.value=null;error.value='';busy.value=false
 if(!allowed.value)return
 if(!(period.value==='year'?/^\d{4}$/:period.value==='month'?/^\d{4}-\d{2}$/:/^\d{4}-\d{2}-\d{2}$/).test(selected.value))return
 busy.value=true
 try{const result=await walletApi(id).get<BusinessReport>(reportQuery(period.value,selected.value));if(current===generation){report.value=result;if(category.value!=='all'&&!result.rows.some(row=>row.count>0&&reportCategory(row)===category.value))category.value='all'}}
 catch(e){if(current===generation)error.value=e instanceof Error?e.message:String(e)}
 finally{if(current===generation)busy.value=false}
}
function exportCsv(){if(!report.value||busy.value)return;const url=URL.createObjectURL(new Blob([reportCsv({...report.value,rows:filteredRows.value},label,t('wallet.businessReport.total'),t('wallet.businessReport.date'))],{type:'text/csv;charset=utf-8'}));const link=document.createElement('a');link.href=url;link.download=`wallet-business-${category.value}-${store.value}-${report.value.startDate}-${report.value.endDate}.csv`;link.click();URL.revokeObjectURL(url)}
watch([store,allowed],()=>{selected.value=today();void load()},{immediate:true})
watch([period,selected],()=>void load())
</script>
<template>
 <section class="wallet-page-heading"><div><h2>{{t('wallet.businessReport.title')}}</h2><p>{{t('wallet.businessReport.hint')}}</p></div></section>
 <p v-if="!allowed" class="wallet-empty">{{t('wallet.noRights')}}</p>
 <section v-else class="wallet-business-report">
  <form class="wallet-card report-filters" @submit.prevent="load">
   <nav :aria-label="t('wallet.businessReport.title')"><button v-for="kind in (['day','month','year'] as const)" :key="kind" type="button" :aria-pressed="period===kind" @click="selectPeriod(kind)">{{t(`wallet.businessReport.${kind}`)}}</button></nav>
   <label>{{t(`wallet.businessReport.${period}`)}}<input v-model="selected" :type="period==='year'?'number':period==='month'?'month':'date'" :min="period==='year'?'1900':period==='month'?'1900-01':'1900-01-01'" :max="period==='year'?'9999':period==='month'?'9999-12':'9999-12-31'" required :aria-label="t(`wallet.businessReport.${period}`)"></label>
   <button class="wallet-primary" :disabled="busy">{{t('wallet.businessReport.query')}}</button><button type="button" :disabled="busy||!report?.rows.length" @click="exportCsv">{{t('wallet.businessReport.export')}}</button>
  </form>
  <p class="report-note">{{t('wallet.businessReport.note')}}</p><p class="report-note">{{t('wallet.businessReport.receiptNote')}}</p>
  <p v-if="error" class="wallet-error" role="alert">{{error}}</p><p v-else-if="busy" class="wallet-empty" role="status">{{t('wallet.businessReport.loading')}}</p>
  <template v-else-if="report">
   <p class="report-period">{{report.startDate}} — {{report.endDate}} · {{t('wallet.businessReport.timezone',{zone:report.timezone})}}</p>
   <nav v-if="availableCategories.length" class="report-categories" :aria-label="t('wallet.businessReport.categories')"><button v-for="item in (['all',...availableCategories] as ReportCategory[])" :key="item" type="button" :aria-pressed="category===item" @click="category=item">{{t(`wallet.businessReport.category.${item}`)}}</button></nav>
   <section v-if="!table.columns.length" class="wallet-card wallet-empty"><h3>{{t('wallet.businessReport.empty')}}</h3><p>{{t('wallet.businessReport.emptyHint')}}</p></section>
   <template v-else>
    <section :aria-label="t('wallet.businessReport.total')"><h3>{{t('wallet.businessReport.total')}} · {{t(`wallet.businessReport.category.${category}`)}}</h3><div class="report-totals"><article v-for="column in table.totals" :key="rowKey(column)" class="wallet-card"><span>{{label(column)}}</span><strong>{{value(column,column)}}</strong><small>{{t('wallet.businessReport.count',{count:column.count})}}</small></article></div></section>
    <section class="wallet-card report-breakdown"><h3>{{t('wallet.businessReport.detail')}}</h3><div class="report-table-scroll" tabindex="0" :aria-label="t('wallet.businessReport.detail')"><table><thead><tr><th scope="col">{{t(period==='year'?'wallet.businessReport.bucketMonth':'wallet.businessReport.date')}}</th><th v-for="column in table.columns" :key="rowKey(column)" scope="col">{{label(column)}}</th></tr></thead><tbody><tr v-for="bucket in table.buckets" :key="bucket"><th scope="row">{{period==='year'?bucket.slice(0,7):bucket}}</th><td v-for="column in table.columns" :key="rowKey(column)">{{value(table.cell(bucket,column),column)}}</td></tr></tbody><tfoot><tr><th scope="row">{{t('wallet.businessReport.total')}}</th><td v-for="column in table.totals" :key="rowKey(column)">{{value(column,column)}}</td></tr></tfoot></table></div></section>
   </template>
  </template>
 </section>
</template>
<style scoped>
.report-categories{display:flex;flex-wrap:wrap;gap:8px}.report-categories [aria-pressed=true]{background:#1677ff;color:white;border-color:#1677ff}.wallet-business-report{display:grid;gap:16px}.report-filters{display:flex;align-items:end;flex-wrap:wrap;gap:12px}.report-filters nav{display:flex;gap:6px;width:100%}.report-filters [aria-pressed=true]{background:#1677ff;color:white;border-color:#1677ff}.report-filters label{display:grid;gap:6px}.report-filters input{min-height:44px}.report-note{color:#64748b;font-size:14px;margin:0;line-height:1.7}.report-period{color:#475569;margin:0}.report-totals{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px}.report-totals article{display:grid;gap:10px;margin:0}.report-totals strong{font-size:24px;color:#0f172a}.report-totals span,.report-totals small{color:#64748b}.report-breakdown{min-width:0}.report-table-scroll{overflow:auto}table{width:100%;border-collapse:collapse;font-size:14px}th,td{padding:14px 12px;text-align:right;white-space:nowrap;border-bottom:1px solid #e2e8f0}th:first-child{text-align:left}thead,tfoot{background:#f1f6fe}tfoot{font-weight:700}@media(max-width:480px){.report-totals{grid-template-columns:repeat(2,minmax(0,1fr))}.report-totals strong{font-size:21px}.report-filters label{width:100%}}
</style>
