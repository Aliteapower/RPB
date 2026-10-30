<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import {useWorkforce} from '../context'
import {useWorkforceText} from '../text'
import {usePositionText} from '../positionText'
import {usePhaseThreeText} from '../phaseThreeText'
import {usePhaseText} from '../phaseText'
import {useDocumentExpiryText} from '../documentExpiry'
import {useDocumentText} from '../documentText'
import {useStatutoryText} from '../statutoryPolicy'
import {useWorkModeText} from '../workMode'
const {w}=useWorkModeText()
import PayrollSettingsLinks from '../components/PayrollSettingsLinks.vue'
const {home,store,payrollHome,payrollFailure,refresh}=useWorkforce(),{t}=useWorkforceText(),{j}=usePositionText(),{q}=usePhaseThreeText(),{p}=usePhaseText()
const route=useRoute(),selectedCompany=computed(()=>payrollHome?.value?.companies.find(c=>c.id===String(route.query.companyId||''))?.id)
const {d}=useDocumentText(),{x}=useDocumentExpiryText(),{s}=useStatutoryText()
</script>
<template><section v-if="home?.employeeManage||payrollHome?.companies.length||payrollFailure" class="wf-panel wf-management-page"><header class="wf-management-header"><h2>{{j('management')}}</h2></header><nav class="wf-management-links" :aria-label="j('management')"><template v-if="home?.employeeManage"><RouterLink :to="`/stores/${store}/workforce/clock-site`"><strong>{{w('site')}}</strong><span>{{w('siteHint')}}</span></RouterLink><RouterLink :to="`/stores/${store}/workforce/document-expiries`"><strong>{{x('title')}}</strong><span>{{x('hint')}}</span></RouterLink><template v-if="home.headOffice"><RouterLink :to="`/stores/${store}/workforce/statutory-policies`"><strong>{{s('title')}}</strong><span>{{s('entryHint')}}</span></RouterLink><RouterLink :to="`/stores/${store}/workforce/document-types`"><strong>{{d('types')}}</strong><span>{{d('typeHint')}}</span></RouterLink><RouterLink :to="`/stores/${store}/workforce/positions`"><strong>{{j('title')}}</strong><span>{{j('positionHint')}}</span></RouterLink><RouterLink :to="`/stores/${store}/workforce/companies`"><strong>{{t('companies')}}</strong><span>{{j('companyHint')}}</span></RouterLink></template><RouterLink :to="`/stores/${store}/workforce/shifts`"><strong>{{p('shifts')}}</strong><span>{{j('shiftHint')}}</span></RouterLink><RouterLink :to="`/stores/${store}/workforce/leave-types`"><strong>{{q('leaveTypes')}}</strong><span>{{q('leaveTypeEntryHint')}}</span></RouterLink><RouterLink :to="`/stores/${store}/workforce/calendar`"><strong>{{q('settings')}}</strong><span>{{j('attendanceHint')}}</span></RouterLink><RouterLink v-if="home?.headOffice" :to="`/stores/${store}/workforce/administrators`"><strong>{{j('authorization')}}</strong><span>{{j('authorizationHint')}}</span></RouterLink></template><PayrollSettingsLinks v-if="payrollHome?.companies.length" :base="`/stores/${store}/workforce`" :company-id="selectedCompany"/></nav><p v-if="payrollFailure" role="alert" class="wf-error">{{payrollFailure}} <button @click="refresh">{{t('refresh')}}</button></p></section><p v-else role="alert">{{t('noAccess')}}</p></template>

