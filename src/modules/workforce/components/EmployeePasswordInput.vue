<script setup lang="ts">
import {ref} from 'vue'
import {useWorkforceText} from '../text'
import {useI18n} from 'vue-i18n'
import {employeeLoginText} from '../employeeLogin'
defineProps<{modelValue?:string;label:string;required?:boolean;disabled?:boolean}>()
defineEmits<{'update:modelValue':[value:string]}>()
const visible=ref(false),{t}=useWorkforceText()
const {locale}=useI18n(),english=()=>locale.value.startsWith('en')
</script>
<template><div class="wf-password"><label>{{label}}<input :value="modelValue||''" @input="$emit('update:modelValue',($event.target as HTMLInputElement).value)" :type="visible?'text':'password'" :required="required" :disabled="disabled" minlength="6" maxlength="6" pattern="[A-Za-z0-9]{6}" autocomplete="new-password" :placeholder="t('passwordHint')"></label><button type="button" :disabled="disabled" :aria-pressed="visible" @click="visible=!visible">{{employeeLoginText(visible?'hide':'show',english())}}</button></div></template>
<style scoped>.wf-password{min-width:0}.wf-password button{margin-top:6px}</style>
