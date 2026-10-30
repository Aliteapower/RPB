<script setup lang="ts">
import {computed} from 'vue'
import type {PayRule,PayGeneration} from '../payrollTypes'
import {usePayrollText} from '../payrollText'
import {generationMode} from '../payGeneration'
const props=defineProps<{rule:PayRule;disabled?:boolean}>(),{r}=usePayrollText()
const mode=computed({get:()=>generationMode(props.rule),set:(mode:PayGeneration['mode'])=>{props.rule.generation={mode,workingWeekdays:mode==='fixed'?[]:props.rule.generation?.workingWeekdays.length?props.rule.generation.workingWeekdays:[1,2,3,4,5]};if(mode==='fixed'){props.rule.basis='monthly';props.rule.overtimeEnabled=false}}})
</script>
<template><fieldset :disabled="disabled"><label>{{r('generationMode')}}<select v-model="mode" :aria-label="r('generationMode')"><option value="manual">{{r('generationManual')}}</option><option value="attendance">{{r('generationAttendance')}}</option><option value="fixed">{{r('generationFixed')}}</option></select></label><p class="wf-hint">{{r('recurringHint')}}</p><p v-if="mode==='fixed'" class="wf-hint">{{r('fixedSalaryHint')}}</p><template v-if="mode==='attendance'"><p class="wf-hint">{{r('attendanceGenerationHint')}}</p><template v-if="rule.basis==='monthly'&&rule.generation"><p>{{r('workingWeekdays')}}</p><div class="wf-inline"><label v-for="day in 7" :key="day" class="wf-check"><input v-model="rule.generation.workingWeekdays" type="checkbox" :value="day">{{r('weekday'+day as 'weekday1')}}</label></div></template></template></fieldset></template>
