<script setup lang="ts">
import type {ScheduleStaffing} from '../bulkScheduleTypes'
defineProps<{value:ScheduleStaffing;english?:boolean}>()
const dateLabel=(date:string)=>date.split('-').reverse().join('/')
</script>
<template><section v-if="value.dailyTotals?.length" class="wf-daily-staffing" :aria-label="english?'Daily unique employee counts':'每日排班人数（去重）'"><article v-for="day in value.dailyTotals" :key="day.date"><header><strong>{{dateLabel(day.date)}}</strong><span>{{english?'Total':'共'}} <b>{{day.people}}</b> {{english?'people':'人'}}</span></header><p v-for="shift in value.days.filter(s=>s.date===day.date)" :key="shift.shiftId">{{value.shifts.find(s=>s.shiftId===shift.shiftId)?.name||'—'}} <strong>{{shift.people}}</strong> {{english?'people':'人'}}</p></article></section></template>
<style scoped>.wf-daily-staffing{display:flex;gap:8px;overflow-x:auto;padding:8px 0;max-width:100%}.wf-daily-staffing article{flex:0 0 190px;padding:8px 10px;background:#fff;border:1px solid #dfe5ec;border-radius:6px;box-sizing:border-box}.wf-daily-staffing header{display:flex;flex-wrap:wrap;justify-content:space-between;gap:4px 8px;font-size:12px}.wf-daily-staffing header span{color:#245a7c}.wf-daily-staffing p{margin:5px 0 0;font-size:12px}</style>
