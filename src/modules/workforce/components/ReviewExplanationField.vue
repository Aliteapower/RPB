<script setup lang="ts">
import {computed,ref} from 'vue'
import {explanationText,type ReviewExplanation} from '../reviewExplanations'
const props=defineProps<{modelValue:string;code:string;items:ReviewExplanation[];english?:boolean;disabled?:boolean}>()
const emit=defineEmits<{'update:modelValue':[value:string]}>(),selected=ref('')
const text=computed({get:()=>props.modelValue,set:value=>{selected.value='';emit('update:modelValue',value)}})
function choose(){const item=props.items.find(i=>i.id===selected.value&&i.enabled&&(i.code===props.code||i.code==='GENERAL'));if(item)emit('update:modelValue',item.text)}
</script>
<template><div class="wf-explanation-field"><label>{{explanationText('choose',english)}}<select v-model="selected" :disabled="disabled" @change="choose"><option value="">—</option><option v-for="item in items.filter(i=>i.enabled&&(i.code===code||i.code==='GENERAL'))" :key="item.id" :value="item.id">{{item.text}}</option></select></label><label>{{explanationText('text',english)}} · {{explanationText('optional',english)}}<textarea v-model="text" maxlength="500" rows="2" :disabled="disabled"></textarea></label></div></template>
<style scoped>.wf-explanation-field{min-width:0}.wf-explanation-field select{max-width:100%}</style>
