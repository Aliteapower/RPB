<script setup lang="ts">
import { useLedgerI18n } from '../i18n'
import LedgerIcon from './LedgerIcon.vue'
const { text } = useLedgerI18n()
defineProps<{ disabled: boolean; submitted: boolean; mobile?: boolean; status: string }>()
defineEmits<{ save: []; submit: [] }>()
</script>
<template><div v-if="!mobile || !submitted" :class="[mobile ? 'mobile-save' : 'save-actions', { 'has-submitted': submitted }]"><span v-if="mobile" aria-live="polite">{{ status || (submitted ? text("已提交") : text("填写自动存草稿")) }}</span><button v-if="!submitted" class="button secondary" :class="{ 'save-button': !mobile }" :disabled="disabled" @click="$emit('save')">{{ text('立即同步') }}</button><button class="button primary" :class="{ 'save-button': !mobile, 'submitted-button': submitted }" :disabled="disabled || submitted" @click="$emit('submit')"><LedgerIcon name="check" />{{ submitted ? text("已提交成功") : text("最终提交") }}</button></div></template>
