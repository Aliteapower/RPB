<script setup lang="ts">
defineProps<{modelValue:number;steps:string[];disabled?:boolean}>()
defineEmits<{ 'update:modelValue':[value:number] }>()
</script>
<template><nav class="wf-inline wf-section" aria-label="办理步骤"><button v-for="(label,index) in steps" :key="label" type="button" :class="{primary:modelValue===index}" :aria-current="modelValue===index?'step':undefined" :disabled="disabled" @click="$emit('update:modelValue',index)">{{index+1}}. {{label}}</button></nav></template>
