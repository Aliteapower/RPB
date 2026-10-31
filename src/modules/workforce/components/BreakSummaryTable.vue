<script setup lang="ts">
import type {BreakSummary} from '../types'
import {hours} from '../time'
import {usePhaseText} from '../phaseText'
import {reviewText} from '../attendanceReview'
defineProps<{summaries?:BreakSummary[]}>()
const {p,english}=usePhaseText()
const r=(key:Parameters<typeof reviewText>[0])=>reviewText(key,english())
</script>
<template><section v-if="summaries?.length" class="wf-section"><h3>{{r('breakSummary')}}</h3><div class="wf-table-wrap"><table><thead><tr><th>{{p('breaks')}}</th><th>{{r('planned')}}</th><th>{{r('actual')}}</th><th>{{r('remaining')}}</th><th>{{r('over')}}</th><th>{{r('short')}}</th></tr></thead><tbody><tr v-for="b in summaries" :key="b.ruleId"><td>{{b.name}} · {{r(b.paid?'paid':'unpaid')}}<span v-if="b.missing" class="wf-issue">{{r('missing')}}</span></td><td>{{hours(b.plannedSeconds)}}</td><td>{{b.actualSeconds===null?r('unrecorded'):hours(b.actualSeconds)}}</td><td>{{hours(b.remainingSeconds)}}</td><td>{{hours(b.overSeconds)}}</td><td>{{hours(b.shortSeconds)}}</td></tr></tbody></table></div></section></template>
