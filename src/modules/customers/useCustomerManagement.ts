import {computed,onMounted,onUnmounted,reactive,ref,watch,type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {archiveCustomer,createCustomer,listCustomers,updateCustomer,getCustomerPrivacyNotice,listCustomerPrivacyConsents,TenantAdminApiError,type CustomerPrivacyNotice,type CustomerPrivacyRecord,type TenantAdminCustomer,type TenantAdminCustomerMutation,type TenantAdminPage} from '../../api/tenantAdminApi'
import {useAuthSessionStore} from '../../stores/authSession'
import {useGeneratedText} from '../../i18n/generatedText'
import {customerPhonePrefill} from './navigation'

export function useCustomerManagement(storeId:Ref<string>,initial:{keyword?:string;create?:boolean;surface?:'wallet'|'tenant_admin';createOnly?:boolean;directoryOnly?:boolean;onCreated?:(customer:TenantAdminCustomer)=>void}={}){
 const auth=useAuthSessionStore(),{gt}=useGeneratedText(),{t}=useI18n()
 const customers=ref<TenantAdminCustomer[]>([]),loading=ref(false),saving=ref(false),archivingId=ref(''),errorText=ref(''),successText=ref('')
 const keyword=ref(initial.keyword??''),limit=ref(20),offset=ref(0),page=ref<TenantAdminPage>({limit:20,offset:0,total:0})
 const editorOpen=ref(false),editingCustomer=ref<TenantAdminCustomer|null>(null),form=reactive({displayName:'',nickname:'',phoneE164:'',email:''})
 const privacyAccepted=ref(true),privacyNotice=ref<CustomerPrivacyNotice|null>(null),noticeLoading=ref(false),privacyCustomer=ref<TenantAdminCustomer|null>(null),privacyRecords=ref<CustomerPrivacyRecord[]>([]),privacyLoading=ref(false)
 const canManage=computed(()=>auth.isTenantAdmin&&auth.user?.storeIds?.includes(storeId.value)===true)
 const actionDisabled=computed(()=>loading.value||saving.value||archivingId.value!=='')
 const canSubmit=computed(()=>canManage.value&&!actionDisabled.value&&(!!editingCustomer.value||(privacyAccepted.value&&!!privacyNotice.value&&!noticeLoading.value)))
 const safePage=computed(()=>page.value??{limit:limit.value,offset:offset.value,total:customers.value.length})
 const hasDirtyQuery=computed(()=>keyword.value.trim()!==''),hasPhoneCount=computed(()=>customers.value.filter(c=>!!c.phoneE164).length),hasEmailCount=computed(()=>customers.value.filter(c=>!!c.email).length)
 let generation=0,request=0
 const current=(version:number)=>version===generation
 async function loadCustomers(nextOffset=offset.value){
  if(!canManage.value||initial.createOnly)return
  const version=generation,sequence=++request,id=storeId.value;loading.value=true;errorText.value=''
  try{const result=await listCustomers(id,{keyword:keyword.value,limit:limit.value,offset:nextOffset});if(!current(version)||sequence!==request)return;customers.value=result.customers;page.value=result.page;offset.value=result.page.offset}
  catch(e){if(current(version)&&sequence===request)errorText.value=apiErrorText(e)}
  finally{if(current(version)&&sequence===request)loading.value=false}
 }
 function activationIssued(customerId:string){const customer=customers.value.find(c=>c.id===customerId);if(customer)customer.activationState='verified_pending';void loadCustomers()}
 function searchCustomers(){if(actionDisabled.value)return;offset.value=0;void loadCustomers(0)}
 function resetFilters(){if(actionDisabled.value)return;keyword.value='';offset.value=0;void loadCustomers(0)}
 function refreshVisibleCustomers(){if(document.visibilityState==='visible'&&!editorOpen.value&&!actionDisabled.value)void loadCustomers()}
 function resetForm(){Object.assign(form,{displayName:'',nickname:'',phoneE164:'',email:''})}
 function openCreateEditor(){if(!canManage.value||saving.value||archivingId.value)return;privacyCustomer.value=null;privacyAccepted.value=true;editingCustomer.value=null;resetForm();errorText.value='';successText.value='';editorOpen.value=true}
 function openEditEditor(item:TenantAdminCustomer){if(!canManage.value||actionDisabled.value)return;privacyCustomer.value=null;editingCustomer.value=item;Object.assign(form,{displayName:item.displayName??'',nickname:item.nickname??'',phoneE164:item.phoneE164??'',email:item.email??''});errorText.value='';successText.value='';editorOpen.value=true}
 function closeEditor(){if(saving.value)return;editorOpen.value=false;editingCustomer.value=null;resetForm()}
 const blank=(s:string)=>s.trim()||null
 async function submitCustomer(){
  if(!canManage.value||actionDisabled.value)return
  if(!editingCustomer.value&&!canSubmit.value){errorText.value=gt(privacyAccepted.value?'generated.tenant-admin-customers.048':'generated.tenant-admin-customers.047');return}
  const payload:TenantAdminCustomerMutation={displayName:blank(form.displayName),nickname:blank(form.nickname),phoneE164:blank(form.phoneE164),email:blank(form.email)}
  if(!editingCustomer.value)payload.privacyConsent={accepted:privacyAccepted.value,noticeVersion:privacyNotice.value!.version,entrySource:initial.surface??'tenant_admin'}
  if(!payload.phoneE164){errorText.value=gt('generated.tenant-admin-customers.035');return}
  const version=generation,id=storeId.value,customerId=editingCustomer.value?.id,reloadOffset=customerId?offset.value:0;saving.value=true;errorText.value='';successText.value=''
  try{if(customerId)await updateCustomer(id,customerId,payload);else {const result=await createCustomer(id,payload);if(!current(version))return;initial.onCreated?.(result.customer)}if(!current(version))return;editorOpen.value=false;editingCustomer.value=null;resetForm();await loadCustomers(reloadOffset);if(current(version))successText.value=gt('generated.tenant-admin-customers.038')}
  catch(e){if(current(version))errorText.value=apiErrorText(e)}
  finally{if(current(version))saving.value=false}
 }
 async function loadPrivacyNotice(){
  if(!canManage.value||noticeLoading.value)return
  const version=generation;noticeLoading.value=true;privacyNotice.value=null
  try{const result=await getCustomerPrivacyNotice(storeId.value);if(current(version))privacyNotice.value=result}catch(e){if(current(version))errorText.value=apiErrorText(e)}finally{if(current(version))noticeLoading.value=false}
 }
 async function openPrivacyRecords(item:TenantAdminCustomer){
  if(!canManage.value||actionDisabled.value)return
  const version=generation;editorOpen.value=false;privacyCustomer.value=item;privacyRecords.value=[];privacyLoading.value=true;errorText.value='';successText.value=''
  try{const result=await listCustomerPrivacyConsents(storeId.value,item.id);if(current(version)&&privacyCustomer.value?.id===item.id)privacyRecords.value=result.records}catch(e){if(current(version)&&privacyCustomer.value?.id===item.id)errorText.value=apiErrorText(e)}finally{if(current(version)&&privacyCustomer.value?.id===item.id)privacyLoading.value=false}
 }
 async function archiveCustomerRecord(item:TenantAdminCustomer){
  if(!canManage.value||actionDisabled.value||!window.confirm(gt('generated.tenant-admin-customers.036')))return
  const version=generation,id=storeId.value;archivingId.value=item.id;errorText.value='';successText.value=''
  try{await archiveCustomer(id,item.id);if(!current(version))return;await loadCustomers(customers.value.length===1&&offset.value>0?Math.max(0,offset.value-limit.value):offset.value);if(current(version))successText.value=gt('generated.tenant-admin-customers.037')}
  catch(e){if(current(version))errorText.value=apiErrorText(e)}
  finally{if(current(version))archivingId.value=''}
 }
 function apiErrorText(error:unknown):string{
  if(!(error instanceof TenantAdminApiError))return gt('generated.tenant-admin-customers.029')
  if(error.status===401){auth.clear();return gt('generated.tenant-admin-customers.030')}
  const key:Record<string,string>={STORE_SCOPE_MISMATCH:'031',FORBIDDEN:'032',CUSTOMER_PHONE_CONFLICT:'033',CUSTOMER_NOT_FOUND:'034',REQUEST_INVALID:'035'}
  if(error.response.error.code==='CUSTOMER_HAS_RETAINED_ASSETS')return gt('generated.tenant-admin-customers.039')
  return gt('generated.tenant-admin-customers.'+(key[error.response.error.code]??'029'))
 }
 watch([storeId,canManage],()=>{generation++;request++;customers.value=[];page.value={limit:limit.value,offset:0,total:0};offset.value=0;loading.value=false;saving.value=false;archivingId.value='';editorOpen.value=false;editingCustomer.value=null;privacyCustomer.value=null;privacyRecords.value=[];privacyLoading.value=false;privacyNotice.value=null;noticeLoading.value=false;resetForm();errorText.value='';successText.value='';if(canManage.value){void loadCustomers(0);if(!initial.directoryOnly)void loadPrivacyNotice();if(initial.create){openCreateEditor();form.phoneE164=customerPhonePrefill(initial.keyword??'')}}},{immediate:true})
 onMounted(()=>{if(initial.createOnly)return;window.addEventListener('focus',refreshVisibleCustomers);document.addEventListener('visibilitychange',refreshVisibleCustomers)})
 onUnmounted(()=>{generation++;request++;if(initial.createOnly)return;window.removeEventListener('focus',refreshVisibleCustomers);document.removeEventListener('visibilitychange',refreshVisibleCustomers)})
 return {activationIssued,customers,loading,saving,archivingId,errorText,successText,keyword,limit,offset,page,safePage,editorOpen,editingCustomer,form,canManage,actionDisabled,canSubmit,hasDirtyQuery,hasPhoneCount,hasEmailCount,loadCustomers,searchCustomers,resetFilters,openCreateEditor,openEditEditor,closeEditor,submitCustomer,archiveCustomerRecord,privacyAccepted,privacyNotice,noticeLoading,privacyCustomer,privacyRecords,privacyLoading,loadPrivacyNotice,openPrivacyRecords}
}
