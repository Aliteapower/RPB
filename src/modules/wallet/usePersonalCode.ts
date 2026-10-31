import { ref,watch,onUnmounted,type Ref } from 'vue'
import { walletApi } from './api'
import type { Bill } from './types'
import type { PersonalConsent,PersonalCode,PersonalCodeStatus } from './personalTypes'
export function usePersonalCode(store:Ref<string>,enabled:Ref<boolean>){
 const code=ref<PersonalCode|null>(null),consent=ref<PersonalConsent|null>(null),receipt=ref<Bill|null>(null),needsConfirmation=ref(false),busy=ref(false),error=ref('')
 let generation=0,polling=false,consentRequest=0,lastConsentCheck=0
 async function issue(){if(!enabled.value||busy.value)return;const current=generation;busy.value=true;error.value=''
  try{const result=await walletApi(store.value,true).post<PersonalCode>('/personal-codes',{});if(current===generation){code.value=result;void load()}}
  catch(e){if(current===generation)error.value=e instanceof Error?e.message:String(e)}finally{if(current===generation)busy.value=false}
 }
 async function load(){const current=generation,request=++consentRequest;lastConsentCheck=Date.now();try{const result=await walletApi(store.value,true).get<PersonalConsent>('/personal-payment-consent');if(current===generation&&request===consentRequest)consent.value=result}catch(e){if(current===generation&&request===consentRequest)error.value=String(e instanceof Error?e.message:e)}}
 async function accept(accepted:boolean,limitCents?:number){const policy=consent.value;if(!policy||busy.value)return
  const selected=accepted?limitCents:policy.consentedLimitCents
  if(accepted&&(typeof selected!=='number'||!Number.isSafeInteger(selected)||selected<=0||selected>9_999_999_999_999))return
  const current=generation;consentRequest++;busy.value=true;error.value='';let saved=false
  try{const result=await walletApi(store.value,true).command<PersonalConsent>('/personal-payment-consent',{accepted,policyVersion:policy.policyVersion,limitCents:selected});if(current!==generation)return;consent.value=result;code.value=null;saved=true}
  catch(e){if(current===generation){error.value=e instanceof Error?e.message:String(e);await load()}}finally{if(current===generation)busy.value=false}
  if(saved&&current===generation)await issue()
 }
 async function check(){const issued=code.value,current=generation;if(!issued||polling||busy.value||document.visibilityState!=='visible')return;polling=true
  if(Date.now()-lastConsentCheck>10000)void load()
  try{const result=await walletApi(store.value,true).post<PersonalCodeStatus>('/personal-codes/status',{code:issued.code});if(current!==generation||code.value?.code!==issued.code)return
   if(result.state==='used'||result.state==='confirmation_required'){receipt.value=result.bill??null;needsConfirmation.value=result.state==='confirmation_required';code.value=null;await issue()}
   else if(result.state==='expired'){code.value=null;await issue()}
  }catch(e){if(current===generation)error.value=e instanceof Error?e.message:String(e)}finally{polling=false}
 }
 watch([store,enabled],()=>{generation++;consentRequest++;code.value=null;consent.value=null;receipt.value=null;needsConfirmation.value=false;error.value='';busy.value=false;if(enabled.value){void load();void issue()}},{immediate:true})
 const timer=window.setInterval(()=>void check(),2000)
 onUnmounted(()=>{generation++;window.clearInterval(timer)})
 return{code,consent,receipt,needsConfirmation,busy,error,issue,accept,check}
}
