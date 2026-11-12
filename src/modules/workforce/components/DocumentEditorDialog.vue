<script setup lang="ts">
import {onBeforeUnmount,onMounted,ref} from 'vue'
import {useDocumentExpiryText} from '../documentExpiry'
defineProps<{title:string;disabled:boolean}>()
const emit=defineEmits<{close:[]}>(),{x}=useDocumentExpiryText(),dialog=ref<HTMLDialogElement|null>(null)
onMounted(()=>dialog.value?.showModal?.())
onBeforeUnmount(()=>dialog.value?.close?.())
</script>
<template><dialog ref="dialog" class="wf-schedule-dialog wf-document-dialog" aria-labelledby="wf-document-editor-title" aria-modal="true" @cancel.prevent="!disabled&&emit('close')"><header class="wf-toolbar"><h2 id="wf-document-editor-title">{{title}}</h2><button type="button" class="wf-dialog-close" :aria-label="x('close')" :disabled="disabled" @click="emit('close')">×</button></header><slot/></dialog></template>
<style scoped>.wf-document-dialog{width:min(1220px,calc(100vw - 32px));max-width:calc(100vw - 32px);max-height:90vh;overflow:auto;box-sizing:border-box}.wf-document-dialog>header{position:sticky;top:-1px;z-index:2;background:white;margin-bottom:12px;padding:8px 0}.wf-document-dialog::backdrop{background:rgb(15 28 42 / 42%)}@media(max-width:800px){.wf-document-dialog{width:calc(100vw - 16px);max-width:calc(100vw - 16px);max-height:94vh;padding:12px}}</style>
