<script setup lang="ts">
import type {ScheduleStaffing} from '../bulkScheduleTypes'
import {useScheduleText} from '../scheduleText'
defineProps<{value?:ScheduleStaffing}>()
const emit=defineEmits<{staffing:[shiftId:string,shiftName:string]}>()
const {s}=useScheduleText()
</script>
<template><section v-if="value" class="wf-staffing" :aria-label="s('staffing')"><header><strong>{{s('staffing')}}</strong><small>{{s('staffingHint')}}</small></header><div class="wf-staffing-counts"><button type="button" v-for="shift in value.shifts" :key="shift.shiftId" class="wf-count-button" :aria-label="s('viewStaffing')+' · '+shift.name" @click="emit('staffing',shift.shiftId,shift.name)"><strong>{{shift.name}}</strong> {{shift.people}} {{s('peopleUnit')}} · {{shift.personDays}} {{s('personDaysUnit')}}</button><span v-if="!value.shifts.length" class="wf-hint">{{s('noStaffing')}}</span></div></section></template>
