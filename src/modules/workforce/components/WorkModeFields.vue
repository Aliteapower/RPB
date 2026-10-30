<script setup lang="ts">
import {useWorkModeText,type WorkMode} from '../workMode'
defineProps<{modelValue?:WorkMode;optional?:boolean}>()
defineEmits<{'update:modelValue':[value:WorkMode|undefined]}>()
const {w}=useWorkModeText()
</script>
<template><label>{{w('mode')}}<select :value="modelValue||''" @change="$emit('update:modelValue',($event.target as HTMLSelectElement).value as WorkMode||undefined)"><option v-if="optional" value="">{{w('unchanged')}}</option><option v-for="mode in (['legacy','onsite','field','home','free'] as const)" :key="mode" :value="mode">{{w(mode)}}</option></select></label></template>
