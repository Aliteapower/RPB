<script setup lang="ts">
import {useI18n} from 'vue-i18n'
const {locale}=useI18n()
import {usePayrollReturn} from '../payrollReturn'
const payrollReturn=usePayrollReturn()
import {computed,onMounted,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {useWorkforce} from '../context'
import {useWorkforceText} from '../text'
import {employeeDraft} from '../employeeDraft'
import {usePositionText} from '../positionText'
import {WorkforceApiError} from '../api'
import type {Employee,EmployeeWrite,Position} from '../types'
import {useDocumentText} from '../documentText'
import EmployeePaySummary from '../components/EmployeePaySummary.vue'
import EmployeeDocuments from '../components/EmployeeDocuments.vue'
import EmployeeAccountFields from '../components/EmployeeAccountFields.vue'
import EmployeeLoginSettings from '../components/EmployeeLoginSettings.vue'
import EmployeeTaskSection from '../components/EmployeeTaskSection.vue'
import {useEmployeeEditorText} from '../employeeEditorText'
const {api,home,store,current}=useWorkforce(),route=useRoute(),router=useRouter(),{t,error}=useWorkforceText()
const {ee}=useEmployeeEditorText()
const existing=String(route.params.employeeId||''),id=existing||crypto.randomUUID(),draft=ref<EmployeeWrite|null>(null),busy=ref(false),failure=ref(''),loading=ref(true),editable=ref(true),linked=ref(false)
const {d}=useDocumentText(),{j}=usePositionText(),positions=ref<Position[]>([]),originalPosition=ref<string|null>(null)
const employmentSummary=computed(()=>draft.value?[positions.value.find(p=>p.id===draft.value!.positionId)?.name||ee('unassigned'),t(draft.value.status),draft.value.enabled===false?d('disabled'):d('enabled')].join(' · '):'')
function revealInvalid(event:Event){let section=(event.target as HTMLElement)?.closest<HTMLDetailsElement>('details');while(section){section.open=true;section=section.parentElement?.closest<HTMLDetailsElement>('details')||null}}
onMounted(async()=>{try{const e=existing?await api().get<Employee>(`/employees/${id}`):undefined;if(!current())return;editable.value=e?.editable??true;linked.value=!!e?.accountId;if(editable.value&&home.value?.employeeManage){const options=await api().get<Position[]>('/positions');if(!current())return;positions.value=options;originalPosition.value=e?.positionId??null;draft.value=employeeDraft(home.value,store.value,e);draft.value.positionId=e?.positionId??null}}catch(e){if(current())failure.value=error(e)}finally{if(current())loading.value=false}})
async function save(){if(!draft.value||busy.value)return;busy.value=true;failure.value='';try{const c={...draft.value,birthDate:draft.value.birthDate||null,companyEffectiveDate:existing?draft.value.companyEffectiveDate:draft.value.hireDate};await api().put(`/employees/${id}`,c);if(current())await router.push(payrollReturn.value||(existing?`/stores/${store.value}/workforce/employees`:`/stores/${store.value}/workforce/employees/${id}/edit`))}catch(e){if(current())failure.value=e instanceof WorkforceApiError&&e.code==='WORKFORCE_POSITION_UNAVAILABLE'?j('unavailable'):e instanceof WorkforceApiError&&e.code==='WORKFORCE_HIRE_DATE_IN_USE'?d('hireInUse'):error(e)}finally{if(current())busy.value=false}}
</script>
<template>
 <div class="wf-panel employee-editor">
  <header class="employee-editor-header"><div><h2>{{existing?ee('title'):t('create')}}</h2><p class="wf-hint">{{ee('intro')}}</p></div><RouterLink :to="`/stores/${store}/workforce/employees`">{{ee('back')}}</RouterLink></header>
  <p v-if="failure" role="alert" class="wf-error">{{failure}}</p><p v-if="loading" role="status">{{t('loading')}}</p><p v-else-if="!editable||!home?.employeeManage" role="alert">{{t('noAccess')}}</p>
  <template v-else-if="draft">
   <div class="employee-identity"><strong>{{draft.name||ee('namePending')}}</strong><span>{{draft.employeeNo||ee('numberPending')}}</span><span class="wf-badge">{{t(draft.status)}}</span><span v-if="draft.enabled===false" class="wf-badge wf-badge-warning">{{d('disabled')}}</span></div>
   <form @submit.prevent="save" @invalid.capture="revealInvalid">
    <fieldset :disabled="busy" class="employee-profile-fields">
     <EmployeeTaskSection :title="ee('basic')" :description="ee('basicHint')" default-open data-employee-task="basic">
      <div class="wf-grid">
       <label>{{t('employeeNo')}}<input v-model="draft.employeeNo" data-field="employeeNo" required maxlength="40"></label>
       <label>{{t('name')}}<input v-model="draft.name" data-field="name" required maxlength="100"></label>
       <label>{{d('englishName')}}<input v-model="draft.englishName" maxlength="100"></label>
       <label>{{d('birthDate')}}<input v-model="draft.birthDate" type="date" :max="draft.hireDate||undefined"></label>
       <label>{{t('phone')}}<input v-model="draft.phone" maxlength="40" type="tel" autocomplete="tel"></label>
       <label>{{t('email')}}<input v-model="draft.email" maxlength="160" type="email" autocomplete="email"></label>
      </div>
     </EmployeeTaskSection>
     <EmployeeTaskSection :title="ee('employment')" :description="ee('employmentHint')" :summary="employmentSummary" :default-open="!existing" data-employee-task="employment">
      <div class="wf-grid">
       <label>{{t('jobTitle')}}<select v-model="draft.positionId" data-field="positionId" @change="draft.jobTitle=''" :aria-label="t('jobTitle')"><option :value="null">{{j('choose')}}</option><option v-for="p in positions.filter(p=>p.enabled||p.id===originalPosition)" :key="p.id" :value="p.id">{{p.name}}{{p.enabled?'':' · '+j('retained')}}</option></select><RouterLink v-if="home?.headOffice" :to="`/stores/${store}/workforce/positions`">{{j('manage')}}</RouterLink><small v-else>{{j('contact')}}</small></label>
       <label>{{t('hireDate')}}<input v-model="draft.hireDate" data-field="hireDate" required type="date" :disabled="!!existing&&!home?.headOffice"></label>
       <label>{{t('status')}}<select v-model="draft.status" data-field="status"><option value="active">{{t('active')}}</option><option value="left">{{t('left')}}</option></select></label>
       <label v-if="draft.status==='left'">{{t('leaveDate')}}<input v-model="draft.leaveDate" data-field="leaveDate" required type="date" :min="draft.hireDate||undefined"></label>
       <label>{{d('availability')}}<select v-model="draft.enabled" data-field="enabled"><option :value="true">{{d('enabled')}}</option><option :value="false">{{d('disabled')}}</option></select><small>{{d('employmentHint')}}</small></label>
      </div>
      <template v-if="!existing"><div class="wf-grid"><label>{{t('company')}}<select v-model="draft.companyId" data-field="companyId" required><option :value="null" disabled>{{t('select')}}</option><option v-for="c in home?.companies" :key="c.id" :value="c.id">{{c.name}}</option></select></label><label>{{t('mainStore')}}<select v-model="draft.mainStoreId" data-field="mainStoreId" required @change="draft.storeIds=[store,draft.mainStoreId!].filter((v,i,a)=>a.indexOf(v)===i)"><option v-for="s in home?.stores" :key="s.id" :value="s.id">{{s.name}}</option></select></label></div><p class="wf-hint">{{t('newEmployeeHint')}}</p></template>
     </EmployeeTaskSection>
     <EmployeeTaskSection v-if="!linked" :title="ee('account')" :description="ee('accountHint')" data-employee-task="account"><EmployeeAccountFields :draft="draft" :accounts="home?.accounts||[]"/></EmployeeTaskSection>
    </fieldset>
    <footer class="wf-form-actions employee-savebar"><p class="wf-hint">{{ee('saveHint')}}</p><div class="wf-inline"><RouterLink class="wf-button" :to="payrollReturn||`/stores/${store}/workforce/employees`">{{t('cancel')}}</RouterLink><button class="primary" :disabled="busy">{{busy?t('saving'):ee('save')}}</button></div></footer>
   </form>
   <template v-if="existing"><p class="wf-section"><RouterLink class="wf-button" :to="`/stores/${store}/workforce/employees/${id}/leave-settings`">{{locale.startsWith('en')?'Contract & leave balances':'合同与假期额度'}}</RouterLink></p><h3 class="employee-other-title">{{ee('more')}}</h3>
    <EmployeeTaskSection v-if="linked" :title="ee('login')" :description="ee('loginHint')" lazy data-employee-task="login"><EmployeeLoginSettings :employee-id="id" embedded/></EmployeeTaskSection>
    <EmployeeTaskSection :title="ee('pay')" :description="ee('payHint')" lazy data-employee-task="pay"><EmployeePaySummary :employee-id="id" :company-id="draft.companyId" :saved="true" embedded/></EmployeeTaskSection>
    <EmployeeTaskSection :title="ee('documents')" :description="ee('documentsHint')" lazy data-employee-task="documents"><EmployeeDocuments :employee-id="id" embedded/></EmployeeTaskSection>
   </template><p v-else class="wf-hint">{{ee('newHint')}}</p>
  </template>
 </div>
</template>
<style scoped>
.employee-editor{min-width:0}.employee-editor-header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:16px;flex-wrap:wrap}.employee-editor-header h2{margin:0}.employee-editor-header .wf-hint{margin:5px 0 0}.employee-editor-header>a{font-size:12px;white-space:nowrap}.employee-identity{display:flex;align-items:center;flex-wrap:wrap;gap:8px 14px;padding:12px 16px;border:1px solid #dbe5ee;border-radius:8px;background:#edf4f9;margin-bottom:16px;overflow-wrap:anywhere}.employee-identity>strong{font-size:15px}.employee-identity>span:not(.wf-badge){font-size:12px;color:#607287}.employee-editor .employee-profile-fields{margin:0}.employee-savebar{align-items:center;justify-content:space-between;gap:12px;margin:4px 0 24px}.employee-savebar .wf-hint{margin:0;max-width:420px}.employee-savebar .wf-inline{gap:8px;flex-shrink:0}.employee-other-title{font-size:13px;color:#61738a;margin:20px 0 12px}.employee-editor :deep(.employee-task-content>.wf-panel){max-width:none}.employee-editor :deep(.wf-table-wrap){max-width:100%}@media(max-width:600px){.employee-savebar{align-items:stretch;flex-direction:column}.employee-savebar .wf-inline{justify-content:flex-end}.employee-identity{padding:12px}}
</style>
