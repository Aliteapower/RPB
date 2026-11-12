<script setup lang="ts">
import {useCdacText,type CdacSetting} from '../cdac'
import type {PayItem} from '../payrollTypes'
defineProps<{setting:{status:CdacSetting['status']|'';reference:string};items:PayItem[];citizenship:string;configured?:boolean}>();const {d}=useCdacText()
</script>
<template><section class="wf-section"><h3>{{d('profile')}}</h3><label>{{d('status')}}<select v-model="setting.status"><option value="" :disabled="configured">{{d('legacy')}}</option><option value="applicable" :disabled="citizenship==='foreign'">{{d('applicable')}}</option><option value="not_applicable">{{d('not_applicable')}}</option><option value="exempt">{{d('exempt')}}</option><option value="manual">{{d('manual')}}</option></select></label><label v-if="setting.status">{{d('reference')}}<textarea v-model="setting.reference" :required="setting.status==='exempt'||setting.status==='manual'" maxlength="500" rows="2"></textarea></label><p class="wf-hint">{{d('profileHint')}}</p><p v-if="setting.status&&setting.status!=='manual'&&items.some(i=>i.kind==='cdac')" role="alert" class="wf-issue">{{d('duplicate')}}</p></section></template>
