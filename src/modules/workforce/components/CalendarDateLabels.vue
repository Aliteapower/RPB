<script setup lang="ts">
import {useHolidayText} from '../holidayYears'
import type {CalendarEvent} from '../phaseThreeTypes'
defineProps<{events?:CalendarEvent[];date:string}>()
const {h,english}=useHolidayText()
</script>
<template><small v-for="event in (events||[]).filter(row=>row.enabled&&row.date===date)" :key="event.id" class="wf-date-label" :class="event.kind==='public_holiday'?'wf-holiday-label':'wf-rest-label'" :title="event.observed?h('inLieu')+' '+event.originDate:undefined">{{english()&&event.englishName?event.englishName:event.name}}<span v-if="event.tentative"> · {{h('unconfirmed')}}</span><span v-if="event.observed"> · {{event.originDate}}</span></small></template>
<style scoped>.wf-date-label{display:block;white-space:normal;max-width:170px;line-height:1.35;margin-top:4px;padding:3px 4px;border-radius:4px;font-size:10px}.wf-holiday-label{color:#9a3935;background:#fff1f0}.wf-rest-label{color:#6c5935;background:#fff6e4}</style>
