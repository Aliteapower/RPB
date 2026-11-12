<script setup lang="ts">
import {computed} from 'vue'
import {useRestSwapText,type RestSwapLink} from '../restSwap'
const props=defineProps<{items?:RestSwapLink[];date:string;employeeId?:string;showName?:boolean}>(),{r}=useRestSwapText()
const rows=computed(()=>props.items?.filter(row=>(!props.employeeId||row.employeeId===props.employeeId)&&(row.restDate===props.date||row.makeupDate===props.date))||[])
</script>
<template><small v-for="row in rows" :key="row.id" class="wf-rest-transfer"><span v-if="showName">{{row.employeeName}} {{row.employeeNo}} · </span>{{r(row.restDate===date?'restLabel':'makeupLabel')}} {{row.restDate===date?row.makeupDate:row.restDate}}<span v-if="row.makeupStatus!=='scheduled'"> · {{r('changed')}}</span></small></template>
<style scoped>.wf-rest-transfer{display:block;margin:4px 0;padding:4px 6px;background:#eef6f2;color:#28634a;border-radius:4px;white-space:normal;overflow-wrap:anywhere}</style>
