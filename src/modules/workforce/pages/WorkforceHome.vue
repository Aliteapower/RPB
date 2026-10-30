<script setup lang="ts">
import {useWorkforce} from '../context'
import {useWorkforceText} from '../text'
import EmployeeDocuments from '../components/EmployeeDocuments.vue'
import {useDocumentText} from '../documentText'
const {d}=useDocumentText()
const {home}=useWorkforce(),{t}=useWorkforceText()
</script>
<template><section class="wf-panel"><h2>{{t('myself')}}</h2><dl v-if="home?.myself" class="wf-details"><dt>{{t('name')}}</dt><dd>{{home.myself.name}}</dd><dt>{{d('englishName')}}</dt><dd>{{home.myself.englishName||'—'}}</dd><dt>{{d('birthDate')}}</dt><dd>{{home.myself.birthDate||'—'}}</dd><dt>{{t('employeeNo')}}</dt><dd>{{home.myself.employeeNo}}</dd><dt>{{t('jobTitle')}}</dt><dd>{{home.myself.jobTitle||'—'}}</dd><dt>{{t('phone')}}</dt><dd>{{home.myself.phone||'—'}}</dd><dt>{{t('email')}}</dt><dd>{{home.myself.email||'—'}}</dd><dt>{{t('status')}}</dt><dd>{{t(home.myself.status)}}</dd></dl><p v-else class="wf-hint">{{t('selfProfileMissing')}}</p></section><EmployeeDocuments v-if="home?.myself" :employee-id="home.myself.id"/></template>
