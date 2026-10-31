<script setup lang="ts">
import type {ScheduleStaffing} from '../bulkScheduleTypes'
import {useScheduleText} from '../scheduleText'
import {useI18n} from 'vue-i18n'
import DailyScheduleStaffing from './DailyScheduleStaffing.vue'
defineProps<{value?:ScheduleStaffing}>()
const emit=defineEmits<{staffing:[shiftId:string,shiftName:string]}>()
const {s}=useScheduleText()
const {locale}=useI18n()
</script>
<template><section v-if="value" class="wf-staffing" :aria-label="s('staffing')"><header><strong>{{s('staffing')}}</strong><small>{{s('staffingHint')}}</small></header><div class="wf-staffing-counts"><button type="button" v-for="shift in value.shifts" :key="shift.shiftId" class="wf-count-button" :aria-label="s('viewStaffing')+' · '+shift.name" @click="emit('staffing',shift.shiftId,shift.name)"><strong>{{shift.name}}</strong> {{shift.people}} {{s('peopleUnit')}} · {{shift.personDays}} {{s('personDaysUnit')}}</button><span v-if="!value.shifts.length" class="wf-hint">{{s('noStaffing')}}</span></div><DailyScheduleStaffing :value="value" :english="locale.startsWith('en')"/><p v-if="value.dailyTotals?.length" class="wf-hint">{{locale.startsWith('en')?'Daily totals count each employee once across all shifts; shift counts may add up to more.':'每日总人数按员工去重；同一员工参加多个班次只计1人，各班次人数分别统计。'}}</p></section></template>
