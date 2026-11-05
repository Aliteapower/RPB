<script setup lang="ts">
import {ref} from 'vue'
const props=defineProps<{title:string;description?:string;summary?:string;defaultOpen?:boolean;lazy?:boolean}>()
const expanded=ref(!!props.defaultOpen),activated=ref(!props.lazy||!!props.defaultOpen)
function toggle(event:Event){expanded.value=(event.currentTarget as HTMLDetailsElement).open;if(expanded.value)activated.value=true}
</script>
<template>
 <details class="employee-task" :open="expanded" @toggle="toggle">
  <summary><span class="employee-task-heading"><strong>{{title}}</strong><span v-if="summary" class="employee-task-summary">{{summary}}</span><small v-if="description">{{description}}</small></span></summary>
  <div v-if="activated" class="employee-task-content"><slot/></div>
 </details>
</template>
<style scoped>
.employee-task{border:1px solid #dfe6ee;border-radius:8px;background:#fff;margin:0 0 12px;min-width:0}.employee-task>summary{display:flex;align-items:center;gap:12px;padding:14px 16px;cursor:pointer;list-style:none}.employee-task>summary::-webkit-details-marker{display:none}.employee-task>summary::after{content:'＋';color:#64778b;font-size:18px;margin-left:auto;flex:none}.employee-task[open]>summary::after{content:'−'}.employee-task[open]>summary{border-bottom:1px solid #e7ecf2}.employee-task-heading{display:flex;flex-direction:column;gap:4px;min-width:0}.employee-task-heading strong{font-size:14px;color:#253d53}.employee-task-summary{font-size:12px;color:#4b6075;overflow-wrap:anywhere}.employee-task-heading small{font-size:12px;font-weight:400;color:#748095}.employee-task-content{padding:16px;min-width:0}.employee-task>summary:focus-visible{outline:2px solid #87b6d6;outline-offset:2px;border-radius:8px}@media(max-width:600px){.employee-task>summary,.employee-task-content{padding:12px}}
</style>
