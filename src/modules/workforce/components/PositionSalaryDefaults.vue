<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useWorkforce} from '../context'
import {useWorkforceText} from '../text'
import {usePayrollConfigurationText} from '../payrollConfigurationText'
import {storeToday} from '../time'
import type {SalaryTemplate} from '../payrollTypes'
import type {PositionSalaryDefault} from '../payrollConfigurationTypes'
import PayRuleSummary from './PayRuleSummary.vue'
import {usePhaseText} from '../phaseText'
import {usePayrollText} from '../payrollText'
const {r}=usePayrollText(),{english}=usePhaseText(),x=(z:string,e:string)=>english()?e:z
const basis=ref('monthly')
const props=defineProps<{positionId:string;companyId?:string}>(),{api,payrollHome,current,store}=useWorkforce(),{pc}=usePayrollConfigurationText(),{error}=useWorkforceText()
const companies=computed(()=>payrollHome?.value?.companies||[]),company=ref(props.companyId||''),items=ref<PositionSalaryDefault[]>([]),templates=ref<SalaryTemplate[]>([]),failure=ref(''),busy=ref(false),loading=ref(false),editing=ref<PositionSalaryDefault|null>(null),date=ref(storeToday()),template=ref(''),enabled=ref(true)
const eligibleTemplates=computed(()=>templates.value.filter(t=>t.enabled&&t.rule.basis===basis.value))
const selectedTemplate=computed(()=>templates.value.find(t=>t.id===template.value))
let id=crypto.randomUUID(),revision=0
watch(()=>props.companyId,value=>{company.value=value||''})
function reset(){editing.value=null;date.value=storeToday();template.value='';enabled.value=true;id=crypto.randomUUID()}
watch(()=>[company.value,props.positionId,basis.value,companies.value] as const,async()=>{const rev=++revision;items.value=[];templates.value=[];failure.value='';reset();if(!companies.value.some(c=>c.id===company.value)){loading.value=false;return}loading.value=true;try{const [ds,ts]=await Promise.all([api().get<PositionSalaryDefault[]>(`/position-salary-defaults?companyId=${company.value}&positionId=${props.positionId}&basis=${basis.value}`),api().get<SalaryTemplate[]>(`/salary-templates?companyId=${company.value}`)]);if(current()&&rev===revision){items.value=ds;templates.value=ts}}catch(e){if(current()&&rev===revision)failure.value=error(e)}finally{if(current()&&rev===revision)loading.value=false}},{immediate:true})
function edit(d:PositionSalaryDefault){editing.value=d;date.value=d.effectiveFrom;template.value=d.templateId;enabled.value=d.enabled}
async function save(){if(busy.value||!company.value||!template.value||!date.value)return;busy.value=true;failure.value='';const rev=revision;try{const d=await api().put<PositionSalaryDefault>(`/position-salary-defaults/${editing.value?.id||id}`,{expectedVersion:editing.value?.version||0,companyId:company.value,positionId:props.positionId,effectiveFrom:date.value,templateId:template.value,enabled:enabled.value,basis:basis.value});if(current()&&rev===revision){items.value=[...items.value.filter(x=>x.id!==d.id),d].sort((a,b)=>a.effectiveFrom.localeCompare(b.effectiveFrom));reset()}}catch(e){if(current()&&rev===revision)failure.value=error(e)}finally{if(current())busy.value=false}}
</script>
<template>
 <details v-if="companies.length" :open="!!companyId" class="wf-task-section">
  <summary>{{pc('defaults')}}</summary>
  <label v-if="!companyId">{{pc('company')}}<select v-model="company" :disabled="busy"><option value="">—</option><option v-for="c in companies" :key="c.id" :value="c.id">{{c.name}}</option></select></label>
  <label>{{x('1. 选择计薪方式','1. Choose pay basis')}}<select v-model="basis" :aria-label="r('basis')" :disabled="busy"><option value="monthly">{{r('monthly')}}</option><option value="hourly">{{r('hourly')}}</option><option value="daily">{{r('daily')}}</option></select></label>
  <p v-if="failure" role="alert" class="wf-error">{{failure}}</p><p v-if="loading" role="status">{{x('加载岗位标准…','Loading position defaults…')}}</p>
  <template v-if="company&&!loading">
   <p class="wf-hint">{{pc('candidateOnly')}}</p>
   <form @submit.prevent="save">
    <fieldset :disabled="busy">
     <legend>{{editing?x('修改岗位标准','Edit position default'):x('2. 设置岗位默认模板','2. Set the position template')}}</legend>
     <div class="wf-grid"><label>{{pc('from')}}<input v-model="date" type="date" required :disabled="!!editing"></label><label>{{pc('template')}}<select v-model="template" required :aria-label="pc('template')"><option value="">{{x('请选择匹配计薪方式的模板','Choose a matching template')}}</option><option v-for="t in eligibleTemplates" :key="t.id" :value="t.id">{{t.name}}</option></select></label></div>
     <p v-if="!eligibleTemplates.length" class="wf-hint">{{x('暂无匹配的启用模板。请先到工资模板查看可用规则；总部发布后，门店预览并启用。','No enabled template matches this basis. Check salary templates first; publish and enable a matching version.')}} <RouterLink target="_blank" rel="noopener" :to="{path:`/stores/${store}/workforce/salary-templates`,query:{companyId:company}}">{{x('在新页查看工资模板','Open salary templates in a new tab')}}</RouterLink></p>
     <details v-if="selectedTemplate" class="wf-task-section"><summary>{{x('查看所选模板金额、津贴与加班依据','Review selected pay, allowances and overtime rules')}} · {{selectedTemplate.name}}</summary><PayRuleSummary :rule="selectedTemplate.rule"/></details>
     <label class="wf-check"><input v-model="enabled" type="checkbox">{{pc('enable')}}</label>
     <footer class="wf-form-actions"><button v-if="editing" type="button" :disabled="busy" @click="reset">{{x('取消修改','Cancel edit')}}</button><button class="primary" :disabled="busy||!template||!date">{{pc('save')}}</button></footer>
    </fieldset>
   </form>
   <p class="wf-hint">{{x('保存后到个人薪资方案预览并应用岗位默认；个人已有方案保持原样。','After saving, preview and apply this default in employee pay plans. Existing plans stay unchanged.')}}</p>
   <details v-if="items.length" class="wf-task-section"><summary>{{x('已保存的岗位标准及生效日期','Saved defaults and effective dates')}} ({{items.length}})</summary><div class="wf-table-wrap"><table><thead><tr><th>{{pc('from')}}</th><th>{{pc('template')}}</th><th>{{x('版本','Version')}}</th><th>{{x('状态','Status')}}</th><th>{{x('操作','Action')}}</th></tr></thead><tbody><tr v-for="d in items" :key="d.id"><td>{{d.effectiveFrom}}</td><td>{{templates.find(t=>t.id===d.templateId)?.name||x('模板已不可用','Template unavailable')}}</td><td>{{d.templateVersion}}</td><td>{{d.enabled?x('启用','Enabled'):x('停用','Disabled')}}</td><td><button type="button" :disabled="busy" @click="edit(d)">{{pc('edit')}}</button></td></tr></tbody></table></div></details>
  </template>
 </details>
</template>
