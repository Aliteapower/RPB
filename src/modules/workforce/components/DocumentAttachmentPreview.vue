<script setup lang="ts">
import {onBeforeUnmount,ref,watch} from 'vue'
import {usePhasePage} from '../phasePage'
import {useDocumentText} from '../documentText'
import type {DocumentPreviewSource} from '../documentTypes'
const props=defineProps<{source:DocumentPreviewSource}>(),emit=defineEmits<{close:[]}>()
const {api,current,store}=usePhasePage(),{d}=useDocumentText()
const url=ref(''),loading=ref(false),failure=ref(''),zoom=ref<number|null>(1)
let revision=0,controller:AbortController|undefined
function release(){controller?.abort();controller=undefined;if(url.value)URL.revokeObjectURL(url.value);url.value=''}
watch(()=>[props.source,store.value],async()=>{
 const rev=++revision,owner=store.value,source=props.source;release();loading.value=true;failure.value='';zoom.value=1
 controller=new AbortController()
 try{
  const blob=source.file||await api().file(source.path!,controller.signal)
  if(rev!==revision||owner!==store.value||!current())return
  if(!['image/jpeg','image/png','image/webp','application/pdf'].includes(blob.type)||blob.type!==source.contentType)throw Error('invalid preview')
  url.value=URL.createObjectURL(blob)
 }catch{if(rev===revision&&current())failure.value=d('previewFailure')}
 finally{if(rev===revision)loading.value=false}
},{immediate:true})
onBeforeUnmount(()=>{revision++;release()})
function resize(delta:number){zoom.value=Math.max(.25,Math.min(4,(zoom.value??1)+delta))}
</script>
<template><aside class="wf-attachment-preview"><header class="wf-toolbar"><h3>{{d('previewTitle')}}</h3><button type="button" @click="emit('close')">{{d('previewClose')}}</button></header><p class="wf-wrap-text">{{source.filename}}</p><p v-if="loading" role="status">{{d('previewLoading')}}</p><p v-if="failure" role="alert">{{failure}}</p><template v-if="url"><div v-if="source.contentType.startsWith('image/')" class="wf-inline"><button type="button" @click="resize(-.25)">{{d('previewZoomOut')}}</button><button type="button" @click="zoom=1">{{d('previewFit')}}</button><button type="button" @click="zoom=null">{{d('previewOriginal')}}</button><button type="button" @click="resize(.25)">{{d('previewZoomIn')}}</button></div><div class="wf-attachment-canvas"><img v-if="source.contentType.startsWith('image/')" :src="url" :alt="source.filename" :style="{width:zoom===null?'auto':zoom*100+'%'}"><iframe v-else :src="url" :title="source.filename" sandbox="allow-same-origin" referrerpolicy="no-referrer"/></div></template><p v-if="source.contentType==='application/pdf'" class="wf-hint">{{d('previewPdf')}}</p><a v-if="source.path" :href="api().url(source.path)" target="_blank" rel="noopener">{{d('download')}}</a></aside></template>
<style scoped>.wf-attachment-preview{min-width:0;border:1px solid #dce5ed;border-radius:8px;padding:12px;align-self:start;position:sticky;top:12px}.wf-attachment-preview h3{margin:0}.wf-attachment-canvas{overflow:auto;max-height:640px;background:#edf1f5;margin:10px 0}.wf-attachment-canvas img{display:block;max-width:none;height:auto}.wf-attachment-canvas iframe{width:100%;height:540px;border:0}.wf-inline{flex-wrap:wrap}@media(max-width:800px){.wf-attachment-preview{position:static}.wf-attachment-canvas{max-height:360px}.wf-attachment-canvas iframe{height:360px}}</style>
