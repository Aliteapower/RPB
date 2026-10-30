<script setup lang="ts">
import {useCdacText,type CdacCalculation} from '../cdac'
defineProps<{value:CdacCalculation}>();const {d}=useCdacText()
</script>
<template><section class="wf-section"><h3>{{d('title')}}</h3><dl class="wf-details"><dt>{{d('status')}}</dt><dd>{{d(value.mode==='override'?'overrideMode':value.mode)}}</dd><dt>{{d('basis')}}</dt><dd>{{value.basis}}</dd><template v-if="value.standardAmount!==null"><dt>{{d('standard')}}</dt><dd>{{value.standardAmount}}</dd></template><dt>{{d('actual')}}</dt><dd>{{value.amount}}</dd><template v-if="value.lowerWage!==null"><dt>{{d('bracket')}}</dt><dd>{{value.lowerWage}} – {{value.upperWage??d('unlimited')}}</dd></template><template v-if="value.policyVersion"><dt>{{d('version')}}</dt><dd>{{value.policyVersion}}</dd></template></dl></section></template>
