<script setup lang="ts">
import {computed,onBeforeUnmount,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import ProductPageHeader from '../../../components/common/ProductPageHeader.vue'
import {workforceApi} from '../api'
import {useWorkforceText} from '../text'
import {emptyHistory,historyQuery,defaultHistoryTab,type HistoryContext,type HistoryData,type HistoryBatch} from '../history'
import type {PayslipSummary,Payslip} from '../payrollTypes'
import type {SegmentSlip} from '../segmentPayroll'
import type {SupplementalPayslip} from '../supplemental'
import type {Attendance,AttendanceDetail} from '../types'
import type {MonthPage} from '../attendanceMonth'
import {hours,storeToday,addDays} from '../time'
import {dayBadges} from '../attendanceMonth'
import PayBreakdownSummary from '../components/PayBreakdownSummary.vue'
import HistorySettlementSlips from '../components/HistorySettlementSlips.vue'
import '../workforce.css'
const route=useRoute(),router=useRouter(),{locale}=useI18n(),{error}=useWorkforceText(),english=computed(()=>locale.value.startsWith('en')),x=(zh:string,en:string)=>english.value?en:zh
const store=computed(()=>String(route.params.storeId)),context=ref<HistoryContext|null>(null),data=ref<HistoryData>(emptyHistory()),loading=ref(false),failure=ref('')
const year=ref(''),month=ref(''),from=ref(''),to=ref(''),company=ref('')
const tab=computed(()=>String(route.query.tab||defaultHistoryTab(context.value))),offset=computed(()=>Math.max(0,Number(route.query.offset)||0)),base=computed(()=>`/stores/${encodeURIComponent(store.value)}/workforce-history`)
function link(patch:Record<string,string|number|undefined>){return {path:base.value,query:historyQuery(route.query,patch)}}
function listLink(){return link({id:undefined,employeeId:undefined,scope:undefined})}
function changeTab(value:string){void router.push(link({tab:value,id:undefined,employeeId:undefined,scope:undefined,offset:0}))}
function filter(){const target=link({year:year.value,month:month.value,from:from.value,to:to.value,companyId:company.value,id:undefined,employeeId:undefined,scope:undefined,offset:0});if(router.resolve(target).fullPath===route.fullPath)void refresh();else void router.push(target)}
let revision=0,controller:AbortController|undefined
async function refresh(){
 const rev=++revision;controller?.abort();controller=new AbortController();const signal=controller.signal
 context.value=null;data.value=emptyHistory();failure.value='';loading.value=true
 year.value=String(route.query.year||storeToday().slice(0,4));month.value=String(route.query.month||storeToday().slice(0,7));from.value=String(route.query.from||addDays(storeToday(),-6));to.value=String(route.query.to||storeToday());company.value=String(route.query.companyId||'')
 const api=workforceApi(store.value),get=<T,>(path:string)=>api.get<T>('/history'+path,signal),next=emptyHistory()
 try{
  const c=await get<HistoryContext>('/context');if(rev!==revision)return;context.value=c
  const selected=String(route.query.tab||defaultHistoryTab(c)),id=typeof route.query.id==='string'?route.query.id:''
  if(selected==='mine'&&c.self){
   if(id)next.payslip=await get<Payslip>('/payslips/'+encodeURIComponent(id))
   else [next.slips,next.segments,next.supplements]=await Promise.all([get<PayslipSummary[]>('/payslips?year='+encodeURIComponent(year.value)),get<SegmentSlip[]>('/segments?year='+encodeURIComponent(year.value)),get<SupplementalPayslip[]>('/supplements?year='+encodeURIComponent(year.value))])
  }else if(selected==='managed'){
   if(!c.companies.length)throw new Error('forbidden')
   if(!company.value)company.value=c.companies[0].id
   if(id)next.payslip=await get<Payslip>('/payslips/'+encodeURIComponent(id)+'?scope=managed&employeeId='+encodeURIComponent(String(route.query.employeeId||'')))
   else{const params=new URLSearchParams({year:year.value,scope:'managed',companyId:company.value});[next.batches,next.segments,next.supplements]=await Promise.all([get<HistoryBatch[]>('/batches?year='+encodeURIComponent(year.value)+'&companyId='+encodeURIComponent(company.value)),get<SegmentSlip[]>('/segments?'+params),get<SupplementalPayslip[]>('/supplements?'+params)])}
  }else if(selected==='month')next.month=await get<MonthPage>('/attendance-month?month='+encodeURIComponent(month.value)+'&limit=30&offset='+offset.value)
  else if(selected==='attendance'){
   if(id)next.detail=await get<AttendanceDetail>('/attendance/'+encodeURIComponent(id))
   else next.attendance=(await get<{items:Attendance[]}>('/attendance?'+new URLSearchParams({from:from.value,to:to.value,limit:'50',offset:String(offset.value)}))).items
  }else throw new Error('forbidden')
  if(rev===revision)data.value=next
 }catch(e){if(rev===revision&&!signal.aborted){data.value=emptyHistory();failure.value=e instanceof Error&&e.message==='forbidden'?x('无权查看此历史范围。','You cannot view this history scope.'):error(e)}}finally{if(rev===revision)loading.value=false}
}
watch(()=>[route.fullPath,store.value],refresh,{immediate:true})
onBeforeUnmount(()=>{revision++;controller?.abort()})
</script>
<template><main class="workforce-page">
 <ProductPageHeader :title="x('员工与工资历史','Workforce history')" :back-to="`/stores/${store}/staff`" :back-label="x('返回','Back')" :refresh-label="x('刷新','Refresh')" :refresh-disabled="loading" @refresh="refresh"/>
 <section class="wf-panel"><p role="status" class="wf-hint">{{x('当前只读：保留既有工资与考勤记录；此页面不能打卡、申请、核准、算薪或登记付款。','Read only: existing payroll and attendance records. No clocking, requests, approvals, payroll calculation or payments are available here.')}}</p>
 <p v-if="loading" role="status">{{x('加载历史记录…','Loading history…')}}</p><p v-if="failure" role="alert" class="wf-error">{{failure}}</p>
 <template v-if="context"><nav class="wf-subnav" :aria-label="x('历史类别','History categories')"><button v-if="context.self" :aria-pressed="tab==='mine'" @click="changeTab('mine')">{{x('我的工资单','My payslips')}}</button><button v-if="context.self||context.employeeManage" :aria-pressed="tab==='attendance'" @click="changeTab('attendance')">{{x('打卡明细','Clock records')}}</button><button v-if="context.self||context.employeeManage" :aria-pressed="tab==='month'" @click="changeTab('month')">{{x('月度考勤','Monthly attendance')}}</button><button v-if="context.companies.length" :aria-pressed="tab==='managed'" @click="changeTab('managed')">{{x('公司历史工资','Company payroll history')}}</button></nav>
 <RouterLink v-if="route.query.id" :to="listLink()">{{x('返回只读列表','Back to read-only list')}}</RouterLink>
 <form v-else class="wf-filters" @submit.prevent="filter"><label v-if="tab==='mine'||tab==='managed'">{{x('年份','Year')}}<input v-model="year" type="number" min="2000" max="2100" required></label><label v-if="tab==='managed'">{{x('工资公司','Payroll company')}}<select v-model="company" required><option v-for="c in context.companies" :key="c.id" :value="c.id">{{c.name}}</option></select></label><label v-if="tab==='month'">{{x('月份','Month')}}<input v-model="month" type="month" required></label><template v-if="tab==='attendance'"><label>{{x('开始日期','From')}}<input v-model="from" type="date" required></label><label>{{x('结束日期','To')}}<input v-model="to" type="date" :min="from" :max="addDays(from,30)" required></label></template><button :disabled="loading">{{x('查询历史','Load history')}}</button></form>
 <template v-if="!loading&&!failure">
 <article v-if="data.payslip" class="wf-section"><h2>{{data.payslip.companyName}} · {{data.payslip.month}}</h2><p>{{data.payslip.line.employeeName}} · {{data.payslip.line.employeeNo}}</p><p>{{x('应实发／已登记付款／未付','Net payable / recorded paid / remaining')}}: SGD {{data.payslip.line.netPay??'—'}} / {{data.payslip.paidAmount}} / {{data.payslip.remainingAmount}}</p><p class="wf-hint">{{x('发布及付款登记不代表银行已经到账。','Publication and recorded payment do not prove bank receipt.')}}</p><PayBreakdownSummary v-if="data.payslip.breakdown" :value="data.payslip.breakdown" details business-labels/><div class="wf-table-wrap"><table><thead><tr><th>{{x('付款日期','Payment date')}}</th><th>{{x('方式／参考','Method / reference')}}</th><th>{{x('金额','Amount')}}</th></tr></thead><tbody><tr v-for="p in data.payslip.payments" :key="p.id"><td>{{p.paymentDate}}</td><td>{{p.method}} · {{p.reference}}</td><td>{{p.reversesId?'−':''}}{{p.amount}}</td></tr></tbody></table></div></article>
 <template v-else-if="tab==='mine'"><h2>{{x('本人已发布月工资单','My published monthly payslips')}}</h2><div class="wf-table-wrap"><table><thead><tr><th>{{x('月份／公司','Month / company')}}</th><th>{{x('应实发／已付','Net / paid')}}</th><th></th></tr></thead><tbody><tr v-for="s in data.slips" :key="s.batchId"><td>{{s.month}} · {{s.companyName}}</td><td>{{s.netPay??'—'}} / {{s.paidAmount}}</td><td><RouterLink :to="link({id:s.batchId})">{{x('查看只读明细','Read details')}}</RouterLink></td></tr><tr v-if="!data.slips.length"><td colspan="3">{{x('本年度暂无已发布月工资单。','No published monthly payslips this year.')}}</td></tr></tbody></table></div>
 <HistorySettlementSlips :segments="data.segments" :supplements="data.supplements"/></template>
 <template v-else-if="tab==='managed'"><h2>{{x('已冻结公司月工资','Frozen company monthly payroll')}}</h2><article v-for="b in data.batches" :key="b.id" class="wf-section"><h3>{{b.companyName}} · {{b.month}} · {{b.status==='published'?x('已发布','Published'):x('已确认','Confirmed')}}</h3><p v-for="e in b.employees" :key="e.id"><RouterLink :to="link({id:b.id,scope:'managed',employeeId:e.id,companyId:company})">{{e.name}} · {{x('只读工资单','Read-only payslip')}}</RouterLink></p></article><p v-if="!data.batches.length">{{x('本年度暂无已冻结月工资。','No frozen monthly payroll this year.')}}</p><HistorySettlementSlips :segments="data.segments" :supplements="data.supplements"/></template>
 <article v-else-if="data.detail"><h2>{{data.detail.attendance.schedule.employeeName}} · {{data.detail.attendance.schedule.workDate}}</h2><p>{{x('计划开始／结束','Scheduled start / end')}}: {{data.detail.attendance.schedule.startAt}} / {{data.detail.attendance.schedule.endAt}}</p><p>{{x('实际开始／结束','Actual start / end')}}: {{data.detail.attendance.session?.inAt||'—'}} / {{data.detail.attendance.session?.outAt||'—'}}</p><p>{{x('工时','Work hours')}} {{hours(data.detail.attendance.workSeconds)}}</p><p v-for="e in data.detail.events" :key="e.id">{{e.occurredAt}} · {{e.action}}</p><p v-for="c in data.detail.corrections" :key="c.id">{{c.createdAt}} · {{c.status}} · {{c.reason}}</p></article>
 <template v-else-if="tab==='attendance'"><div class="wf-table-wrap"><table><thead><tr><th>{{x('日期／员工','Date / employee')}}</th><th>{{x('工时','Work hours')}}</th><th></th></tr></thead><tbody><tr v-for="a in data.attendance" :key="a.schedule.id"><td>{{a.schedule.workDate}} · {{a.schedule.employeeName}}</td><td>{{hours(a.workSeconds)}}</td><td><RouterLink :to="link({id:a.schedule.id})">{{x('只读明细','Read details')}}</RouterLink></td></tr><tr v-if="!data.attendance.length"><td colspan="3">{{x('此范围暂无记录。','No records in this range.')}}</td></tr></tbody></table></div><footer class="wf-pagination"><RouterLink v-if="offset>0" :to="link({offset:Math.max(0,offset-50)})">{{x('上一页','Previous')}}</RouterLink><RouterLink v-if="data.attendance.length===50" :to="link({offset:offset+50})">{{x('下一页','Next')}}</RouterLink></footer></template>
 <template v-else-if="tab==='month'&&data.month"><p>{{x('此月历按现有来源展示，不更新或核准判定。','This calendar reads existing sources without updating or approving judgments.')}}</p><details v-for="e in data.month.employees" :key="e.employeeId" class="wf-section"><summary>{{e.name}} · {{e.employeeNo}}</summary><div class="wf-table-wrap"><table><thead><tr><th>{{x('日期','Date')}}</th><th>{{x('工时','Work hours')}}</th><th>{{x('状态','Status')}}</th></tr></thead><tbody><tr v-for="d in e.days" :key="d.workDate"><td>{{d.workDate}}</td><td>{{hours(d.workSeconds)}}</td><td>{{dayBadges(d,english).join(' · ')||x('正常，来源有效','Normal, effective source')}}</td></tr></tbody></table></div></details><p v-if="!data.month.employees.length">{{x('此范围暂无记录。','No records in this range.')}}</p><footer class="wf-pagination"><RouterLink v-if="offset>0" :to="link({offset:Math.max(0,offset-30)})">{{x('上一页','Previous')}}</RouterLink><RouterLink v-if="offset+data.month.employees.length<data.month.total" :to="link({offset:offset+30})">{{x('下一页','Next')}}</RouterLink></footer></template>
 </template></template></section>
</main></template>
