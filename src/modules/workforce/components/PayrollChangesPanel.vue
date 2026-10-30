<script setup lang="ts">
import {ref,watch} from 'vue'
import {usePayrollPage} from '../payrollPage'
import {usePayrollProgressText} from '../payrollProgressText'
import type {PayrollChangeReport} from '../payrollProgressTypes'
import PayrollChangeComparison from './PayrollChangeComparison.vue'
import PhaseStatus from './PhaseStatus.vue'
import {usePayrollWorkbenchText} from '../payrollWorkbenchText'
const props=defineProps<{batchId:string;version:number}>(),{api,load,loading,failure}=usePayrollPage(),g=usePayrollProgressText(),w=usePayrollWorkbenchText(),data=ref<PayrollChangeReport|null>(null)
function refresh(){data.value=null;const id=props.batchId,version=props.version;void load(()=>api().get<PayrollChangeReport>('/payroll-batches/'+encodeURIComponent(id)+'/changes'),result=>{if(props.batchId===id&&props.version===version)data.value=result})}
watch(()=>[props.batchId,props.version],refresh,{immediate:true})
</script>
<template><section class="wf-section"><header class="wf-toolbar"><h3>{{g('changes')}}</h3><button :disabled="loading" @click="refresh">{{g('refresh')}}</button></header><PhaseStatus :failure="failure" :loading="loading"/><template v-if="data"><p>{{g('recalculated')}} {{data.recalculated}} · {{g('reused')}} {{data.reused}}</p><p v-if="data.sourceChanged" class="wf-issue">{{['confirmed','published','voided'].includes(data.status)?w('frozen'):g('sourceChangedHint')}}</p><h4>{{g('pending')}} ({{data.pending.length}})</h4><p v-if="!['confirmed','published','voided'].includes(data.status)" class="wf-hint">{{g('pendingHint')}}</p><ul v-if="data.pending.length"><li v-for="(line,i) in data.pending" :key="line.employeeId+':'+i">{{line.employeeName||'—'}} · {{g(line.reason)}}</li></ul><PayrollChangeComparison :comparison="data.lastCalculation" :title="g('lastCalculation')"/><PayrollChangeComparison :comparison="data.previousMonth" :title="g('previousMonth')"/></template></section></template>
