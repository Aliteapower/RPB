<script setup lang="ts">
import {useId} from 'vue'
import DateInput from '../../../components/common/DateInput.vue'
defineProps<{modelValue:string;label:string;placeholder:string;calendarLabel:string;min?:string;max?:string;resetKey?:number}>()
const emit=defineEmits<{'update:modelValue':[string];'validity-change':[boolean]}>(),id=useId()
</script>
<template><div class="storage-report-date-field"><label :for="id">{{label}}</label><DateInput :id="id" :reset-key="resetKey" :model-value="modelValue" :placeholder="placeholder" :calendar-label="calendarLabel" :min="min" :max="max" @update:model-value="emit('update:modelValue',$event)" @validity-change="emit('validity-change',$event)"/></div></template>
