<script setup lang="ts">
import LedgerIcon from './LedgerIcon.vue'
defineProps<{ disabled: boolean; submitted: boolean; mobile?: boolean; status: string }>()
defineEmits<{ save: []; submit: [] }>()
</script>
<template><div v-if="!mobile || !submitted" :class="[mobile ? 'mobile-save' : 'save-actions', { 'has-submitted': submitted }]"><span v-if="mobile" aria-live="polite">{{ status || (submitted ? '已提交' : '填写自动存草稿') }}</span><button v-if="!submitted" class="button secondary" :class="{ 'save-button': !mobile }" :disabled="disabled" @click="$emit('save')">立即同步</button><button class="button primary" :class="{ 'save-button': !mobile, 'submitted-button': submitted }" :disabled="disabled || submitted" @click="$emit('submit')"><LedgerIcon name="check" />{{ submitted ? '已提交成功' : '最终提交' }}</button></div></template>
