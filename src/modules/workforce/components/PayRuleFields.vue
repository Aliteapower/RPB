<script setup lang="ts">
import type {PayRule} from '../payrollTypes'
import {usePayrollText} from '../payrollText'
import PayGenerationFields from './PayGenerationFields.vue'
import PayItemFields from './PayItemFields.vue'
defineProps<{rule:PayRule;disabled?:boolean;preserveItemIds?:boolean;basisLocked?:boolean;hideItems?:boolean}>();const {r}=usePayrollText()
</script>
<template><fieldset :disabled="disabled"><PayGenerationFields :rule="rule" :disabled="disabled"/><div class="wf-grid"><label>{{r('basis')}}<select v-model="rule.basis" :disabled="basisLocked"><option value="monthly">{{r('monthly')}}</option><option value="daily">{{r('daily')}}</option><option value="hourly">{{r('hourly')}}</option></select></label><label>{{r('rate')}}<input v-model="rule.rate" required inputmode="decimal" pattern="\d{1,9}(\.\d{1,2})?"></label><label>{{r('dayHours')}}<input v-model="rule.dayHours" required inputmode="decimal"></label><label>{{r('weekHours')}}<input v-model="rule.weekHours" required inputmode="decimal"></label></div><label class="wf-check"><input v-model="rule.overtimeEnabled" type="checkbox">{{r('overtimeEnabled')}}</label><label v-if="rule.overtimeEnabled">{{r('multiplier')}}<input v-model="rule.overtimeMultiplier" required inputmode="decimal"></label><p class="wf-hint">{{r('otHint')}}</p><h2 v-if="!hideItems">{{r('fixedItems')}}</h2><PayItemFields v-if="!hideItems" :items="rule.items" :preserve-item-ids="preserveItemIds" recurring :disabled="disabled"/></fieldset></template>
