<script setup lang="ts">
import type {PaymentSummary} from '../payrollWorkbenchTypes'
import {usePayrollText} from '../payrollText'
import {usePayrollWorkbenchText} from '../payrollWorkbenchText'
defineProps<{data:PaymentSummary}>();const w=usePayrollWorkbenchText(),{r}=usePayrollText()
</script>
<template><section class="wf-section"><h4>{{w('payments')}}</h4><p v-if="!data.available" class="wf-hint">{{w('paymentUnavailable')}}</p><template v-else><dl class="wf-details"><dt>{{w('due')}}</dt><dd>SGD {{data.due}}</dd><dt>{{w('paid')}}</dt><dd>SGD {{data.paid}}</dd><dt>{{w('remaining')}}</dt><dd>SGD {{data.remaining}}</dd></dl><p>{{w('unpaid')}} {{data.unpaid}} · {{w('partial')}} {{data.partial}} · {{w('settled')}} {{data.settled}} · {{w('zero')}} {{data.zero}}</p><div class="wf-table-wrap"><table><thead><tr><th>{{r('employee')}}</th><th>{{w('due')}}</th><th>{{w('paid')}}</th><th>{{w('remaining')}}</th><th></th></tr></thead><tbody><tr v-for="line in data.lines" :key="line.employeeId"><td>{{line.employeeName}}</td><td>{{line.netPay}}</td><td>{{line.paidAmount}}</td><td>{{line.remainingAmount}}</td><td>{{w(line.state)}}</td></tr></tbody></table></div></template><p class="wf-hint">{{w('paymentHint')}}</p></section></template>
