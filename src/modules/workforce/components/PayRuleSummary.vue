<script setup lang="ts">
import {computed} from 'vue'
import type {PayRule} from '../payrollTypes'
import {usePayrollText} from '../payrollText'
import {usePhaseText} from '../phaseText'
import {generationMode} from '../payGeneration'
const props=defineProps<{rule:PayRule}>(),{r}=usePayrollText(),{english}=usePhaseText(),x=(z:string,e:string)=>english()?e:z
const mode=computed(()=>generationMode(props.rule))
const weekdays=computed(()=>(props.rule.generation?.workingWeekdays||[]).map(d=>(english()?['Mon','Tue','Wed','Thu','Fri','Sat','Sun']:['周一','周二','周三','周四','周五','周六','周日'])[d-1]).join('、'))
</script>
<template><section :aria-label="x('工资规则明细（只读）','Pay rule details (read-only)')">
 <dl class="wf-details"><dt>{{r('basis')}}</dt><dd>{{r(rule.basis)}}</dd><dt>{{r('rate')}}</dt><dd>SGD {{rule.rate}}</dd><dt>{{r('dayHours')}}</dt><dd>{{rule.dayHours}}</dd><dt>{{r('weekHours')}}</dt><dd>{{rule.weekHours}}</dd><dt>{{r('generationMode')}}</dt><dd>{{r(mode==='attendance'?'generationAttendance':mode==='fixed'?'generationFixed':'generationManual')}}</dd><template v-if="mode==='attendance'"><dt>{{r('workingWeekdays')}}</dt><dd>{{weekdays||'—'}}</dd></template><dt>{{x('加班计算','Overtime')}}</dt><dd>{{rule.overtimeEnabled?x('启用，倍率 ','Enabled, multiplier ')+rule.overtimeMultiplier:x('未启用','Disabled')}}</dd></dl>
 <h3>{{r('fixedItems')}}</h3><div v-if="rule.items.length" class="wf-table-wrap"><table><thead><tr><th>{{x('项目','Item')}}</th><th>{{x('类别','Type')}}</th><th>{{x('金额（SGD）','Amount (SGD)')}}</th><th>{{r('proration')}}</th></tr></thead><tbody><tr v-for="(item,index) in rule.items" :key="item.itemId||index"><td class="wf-wrap-text">{{item.label}}</td><td>{{item.kind==='wage_event'?x('工资事件','Wage event'):r(item.kind)}}</td><td>{{item.amount}}</td><td>{{r(item.proration==='full'?'prorationFull':item.proration==='payable'?'prorationPayable':'prorationLegacy')}}</td></tr></tbody></table></div><p v-else class="wf-hint">{{x('未配置津贴或其他固定项目。','No allowances or recurring items configured.')}}</p>
 <p v-if="rule.items.some(i=>i.kind==='ow_attendance')" class="wf-hint">{{x('全勤奖按当月核准结果判断；这里的标准金额不代表已获得。','Attendance bonus depends on approved monthly results; this amount is a configured standard.')}}</p>
</section></template>
