<script setup lang="ts">
import {computed} from 'vue'
import {permittedModes,useWorkModeText,type WorkMode} from '../workMode'
const props=defineProps<{modelValue?:WorkMode;modes?:WorkMode[];optional?:boolean}>()
const emit=defineEmits<{'update:modelValue':[value:WorkMode|undefined];'update:modes':[value:WorkMode[]|undefined]}>()
const {w}=useWorkModeText(),standard=['onsite','field','home'] as const
const allowed=computed(()=>props.modelValue?permittedModes(props.modelValue,props.modes):[])
function select(value:string){const mode=value as WorkMode|'';emit('update:modelValue',mode||undefined);emit('update:modes',!mode?undefined:mode==='free'||mode==='legacy'?[mode]:allowed.value.includes(mode)?allowed.value:[...allowed.value.filter(m=>standard.includes(m as typeof standard[number])),mode])}
function toggle(mode:typeof standard[number],checked:boolean){const next=checked?[...allowed.value.filter(m=>standard.includes(m as typeof standard[number])),mode]:allowed.value.filter(m=>m!==mode);if(!next.length)return;emit('update:modes',next);if(!props.modelValue||!next.includes(props.modelValue))emit('update:modelValue',next[0])}
</script>
<template><div class="wf-section"><label>{{w('defaultMode')}}<select :value="modelValue||''" @change="select(($event.target as HTMLSelectElement).value)"><option v-if="optional" value="">{{w('unchanged')}}</option><option v-for="mode in (['legacy','onsite','field','home','free'] as const)" :key="mode" :value="mode">{{w(mode)}}</option></select></label><template v-if="modelValue"><p class="wf-hint">{{w('allowedModes')}}</p><div class="wf-inline"><label v-for="mode in standard" :key="mode" class="wf-check"><input type="checkbox" :data-allowed-mode="mode" :checked="allowed.includes(mode)" :disabled="allowed.length===1&&allowed.includes(mode)" @change="toggle(mode,($event.target as HTMLInputElement).checked)">{{w(mode)}}</label></div><p class="wf-hint">{{w('modeHint')}}</p></template></div></template>
