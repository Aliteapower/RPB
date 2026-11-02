<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {SegmentSlip} from '../segmentPayroll'
import type {SupplementalPayslip} from '../supplemental'
import PayBreakdownSummary from './PayBreakdownSummary.vue'
defineProps<{segments:SegmentSlip[];supplements:SupplementalPayslip[]}>()
const {locale}=useI18n(),english=computed(()=>locale.value.startsWith('en')),x=(zh:string,en:string)=>english.value?en:zh
const status=(s:string)=>s==='published'?x('已发布','Published'):x('已确认','Confirmed')
</script>
<template>
 <section class="wf-section"><h3>{{x('历史分段工资','Independent settlement history')}}</h3>
 <details v-for="s in segments" :key="s.id" class="wf-section"><summary>{{s.companyName}} · {{s.employeeName}} · {{s.from}} – {{s.cutoff}} · {{status(s.status)}} · SGD {{s.net}}</summary><p>{{x('已付／未付','Paid / remaining')}} {{s.paid}} / {{s.remaining}}</p><PayBreakdownSummary :value="s.breakdown" details business-labels/><p v-for="p in s.payments" :key="p.id">{{p.paymentDate}} · {{p.method}} · {{p.reversesId?'−':''}}{{p.amount}}</p></details>
 <p v-if="!segments.length">{{x('本年度暂无可读的已冻结分段工资。','No readable frozen independent settlements this year.')}}</p></section>
 <section class="wf-section"><h3>{{x('历史补发工资','Supplemental payslip history')}}</h3>
 <details v-for="s in supplements" :key="s.batchId" class="wf-section"><summary>{{s.companyName}} · {{s.employeeName}} · {{s.originMonth}} / {{s.settlementMonth}} · {{status(s.status)}} · SGD {{s.employeeCashPayable}}</summary><p>{{x('已付／未付','Paid / remaining')}} {{s.paidAmount}} / {{s.remainingAmount}}</p><p v-for="(i,index) in s.items" :key="index">{{i.label}} · {{i.classification}} · {{i.amount}}</p></details>
 <p v-if="!supplements.length">{{x('本年度暂无可读的已冻结补发工资。','No readable frozen supplemental payslips this year.')}}</p></section>
</template>
