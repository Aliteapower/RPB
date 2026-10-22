<script setup lang="ts">
import { onMounted, ref } from 'vue'
defineProps<{ title: string; wide?: boolean }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>()
onMounted(() => dialog.value?.showModal())
</script>
<template><dialog ref="dialog" :class="{ wide }" aria-modal="true" :aria-label="title" @cancel.prevent="emit('close')"><div class="modal-head"><h2>{{ title }}</h2><button type="button" class="icon-button" aria-label="关闭" @click="emit('close')">×</button></div><slot /></dialog></template>
