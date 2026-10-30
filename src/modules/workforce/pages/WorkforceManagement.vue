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
import {attendanceSettingsText as a} from '../attendanceSettingsText'
import {explanationText} from '../reviewExplanations'
import PayrollSettingsLinks from '../components/PayrollSettingsLinks.vue'
const {home,store,payrollHome,payrollFailure,refresh}=useWorkforce(),{t}=useWorkforceText(),{j}=usePositionText(),{q}=usePhaseThreeText(),{p,english}=usePhaseText()
const route=useRoute(),selectedCompany=computed(()=>payrollHome?.value?.companies.find(c=>c.id===String(route.query.companyId||''))?.id)
const {d}=useDocumentText(),{x}=useDocumentExpiryText(),{s}=useStatutoryText()
</script>
<template><section v-if="home?.employeeManage||payrollHome?.companies.length||payrollFailure" class="wf-panel wf-management-page"><header class="wf-management-header"><h2>{{j('management')}}</h2></header>
<section v-if="home?.employeeManage" class="wf-section"><h3>{{a('peopleGroup',english())}}</h3><nav class="wf-management-links" :aria-label="a('peopleGroup',english())">
<RouterLink :to="`/stores/${store}/workforce/clock-site`"><strong>{{w('site')}}</strong><span>{{w('siteHint')}}</span></RouterLink>
<RouterLink :to="`/stores/${store}/workforce/document-expiries`"><strong>{{x('title')}}</strong><span>{{x('hint')}}</span></RouterLink>
<RouterLink :to="`/stores/${store}/workforce/pay-template-library`"><strong>{{english()?'Store pay template library':'门店工资模板库'}}</strong><span>{{english()?'Preview and enable fixed published templates.':'预览并显式启用总部发布的固定版本模板。'}}</span></RouterLink>
<RouterLink :to="`/stores/${store}/workforce/pay-change-requests`"><strong>{{english()?'Store pay derivation requests':'本店派生变更申请'}}</strong><span>{{english()?'Create independent store template changes and track approval.':'创建本店独立模板草稿并跟踪审批，不授予个人工资访问权。'}}</span></RouterLink><template v-if="home.headOffice"><RouterLink :to="`/stores/${store}/workforce/document-types`"><strong>{{d('types')}}</strong><span>{{d('typeHint')}}</span></RouterLink><RouterLink :to="`/stores/${store}/workforce/positions`"><strong>{{j('title')}}</strong><span>{{j('positionHint')}}</span></RouterLink><RouterLink :to="`/stores/${store}/workforce/companies`"><strong>{{t('companies')}}</strong><span>{{j('companyHint')}}</span></RouterLink><RouterLink :to="`/stores/${store}/workforce/administrators`"><strong>{{j('authorization')}}</strong><span>{{j('authorizationHint')}}</span></RouterLink></template></nav></section>
<section v-if="home?.employeeManage" class="wf-section"><h3>{{a('attendanceGroup',english())}}</h3><nav class="wf-management-links" :aria-label="a('attendanceGroup',english())">
<RouterLink :to="`/stores/${store}/workforce/shifts`"><strong>{{p('shifts')}}</strong><span>{{j('shiftHint')}}</span></RouterLink>
<RouterLink :to="`/stores/${store}/workforce/leave-types`"><strong>{{a('leave',english())}}</strong><span>{{a('leaveHint',english())}}</span></RouterLink>
<RouterLink :to="`/stores/${store}/workforce/public-holidays`"><strong>{{a('annual',english())}}</strong><span>{{a('annualHint',english())}}</span></RouterLink>
<RouterLink :to="`/stores/${store}/workforce/calendar`"><strong>{{a('calendar',english())}}</strong><span>{{a('calendarHint',english())}}</span></RouterLink>
<RouterLink :to="`/stores/${store}/workforce/attendance-rules`"><strong>{{a('rules',english())}}</strong><span>{{english()?'Maintain reusable break and attendance rules.':'维护班次可选用的休息及异常判定规则。'}}</span></RouterLink>
<RouterLink :to="`/stores/${store}/workforce/review-explanations`"><strong>{{explanationText('title',english())}}</strong><span>{{explanationText('hint',english())}}</span></RouterLink></nav></section>
<section v-if="payrollHome?.companies.length||home?.headOffice&&home?.employeeManage" class="wf-section"><h3>{{a('payrollGroup',english())}}</h3><nav class="wf-management-links" :aria-label="a('payrollGroup',english())"><RouterLink v-if="home?.headOffice&&home?.employeeManage" :to="`/stores/${store}/workforce/statutory-policies`"><strong>{{s('title')}}</strong><span>{{s('entryHint')}}</span></RouterLink><PayrollSettingsLinks v-if="payrollHome?.companies.length" :base="`/stores/${store}/workforce`" :company-id="selectedCompany"/></nav></section>
<p v-if="payrollFailure" role="alert" class="wf-error">{{payrollFailure}} <button @click="refresh">{{t('refresh')}}</button></p></section><p v-else role="alert">{{t('noAccess')}}</p></template>
