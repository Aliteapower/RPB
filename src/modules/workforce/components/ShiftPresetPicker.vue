<script setup lang="ts">
import {computed,ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {restaurantPreset,restaurantShiftRange,restaurantText} from '../restaurantShifts'
import type {ShiftWrite} from '../types'
const emit=defineEmits<{apply:[fields:Pick<ShiftWrite,'name'|'startTime'|'endTime'|'nextDay'|'startNextDay'>]}>()
const {locale}=useI18n(),english=computed(()=>locale.value.startsWith('en')),text=computed(()=>restaurantText(english.value)),selected=ref('custom')
function apply(key:string){selected.value=key;const preset=restaurantPreset(key,english.value);if(preset)emit('apply',preset)}
</script>
<template><section class="wf-section"><label>{{text.preset}}<select :value="selected" @change="apply(($event.target as HTMLSelectElement).value)"><option value="custom">{{text.custom}}</option><option v-for="key in (['early','middle','late'] as const)" :key="key" :value="key">{{restaurantPreset(key,english)!.name}} · {{restaurantShiftRange(restaurantPreset(key,english)!,english)}}</option></select></label><p class="wf-hint">{{text.hint}}</p></section></template>
