<script setup lang="ts">
import {computed,nextTick,onMounted,onUnmounted,ref,watch} from 'vue'
import {useI18n} from 'vue-i18n'
const props=withDefaults(defineProps<{disabled?:boolean;label?:string;error?:string;maxLength?:number}>(),{disabled:false,maxLength:4096})
const emit=defineEmits<{code:[string]}>(),{locale}=useI18n(),en=computed(()=>locale.value.startsWith('en'))
const input=ref<HTMLInputElement|null>(null),value=ref(''),focused=ref(false),visible=ref(true)
let active=true,emitting=false
function clear(){value.value='';if(input.value)input.value.value=''}
async function resume(){
  clear();await nextTick()
  if(active&&!props.disabled&&document.visibilityState==='visible')input.value?.focus?.()
}
function submit(){
  const code=value.value.trim();clear()
  if(props.disabled||emitting||!visible.value||document.visibilityState!=='visible'||!code||code.length>props.maxLength)return
  emitting=true;emit('code',code);void nextTick(()=>{emitting=false})
}
function keydown(event:KeyboardEvent){if(event.key==='Enter'){event.preventDefault();event.stopPropagation();if(!event.repeat&&!event.isComposing)submit()}}
function visibility(){visible.value=document.visibilityState==='visible';clear();if(visible.value&&(!document.activeElement||document.activeElement===document.body||document.activeElement===input.value))void resume()}
watch(()=>props.disabled,disabled=>{clear();if(!disabled&&(!document.activeElement||document.activeElement===document.body||document.activeElement===input.value))void resume()})
onMounted(()=>{visible.value=document.visibilityState==='visible';document.addEventListener('visibilitychange',visibility);void resume()})
onUnmounted(()=>{active=false;clear();document.removeEventListener('visibilitychange',visibility)})
</script>
<template><section class="keyboard-scan-input" :aria-busy="disabled">
  <p role="status" aria-live="polite">{{disabled?(en?'Processing / paused':'处理中／已暂停'):!visible?(en?'Paused in background':'页面在后台，已暂停'):focused?(en?'Ready to scan':'等待扫码'):(en?'Focus the input to resume scanning':'扫码接收已暂停，请恢复接收')}}</p>
  <label>{{label||(en?'Scanner input':'扫码枪输入')}}<input ref="input" v-model="value" type="text" :maxlength="maxLength" :disabled="disabled||!visible" autocomplete="off" autocapitalize="off" spellcheck="false" @keydown="keydown" @focus="clear();focused=true" @blur="focused=false"></label>
  <div class="keyboard-scan-actions"><button type="button" :disabled="disabled||!visible||!value.trim()" @click="submit">{{en?'Read code':'识别'}}</button><button v-if="!focused&&!disabled&&visible" type="button" @click="resume">{{en?'Resume scanning':'恢复接收'}}</button></div>
  <p v-if="error" role="alert">{{error}}</p>
</section></template>
<style scoped>.keyboard-scan-input{min-width:0}.keyboard-scan-input label{display:grid;gap:6px}.keyboard-scan-input input{box-sizing:border-box;width:100%;min-width:0}.keyboard-scan-actions{display:flex;flex-wrap:wrap;gap:8px;margin-top:8px}</style>
