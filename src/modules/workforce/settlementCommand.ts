import {computed,ref} from 'vue'
import {WorkforceApiError} from './api'
import {usePayrollPage} from './payrollPage'

/** An uncertain response freezes the exact URL and payload until replay succeeds. */
export function useSettlementCommand(){
 const {api,current,store,english,message}=usePayrollPage(),busy=ref(false),uncertain=ref(false),failure=ref(''),errorCode=ref('')
 let pending:{path:string;body:unknown;method:'put'|'post';store:string;validate:(v:any)=>boolean;accept:(v:any)=>void}|null=null
 const locked=computed(()=>busy.value||uncertain.value)
 async function replay(){
  if(busy.value||!pending||pending.store!==store.value)return
  const command=pending;busy.value=true;failure.value='';errorCode.value=''
  try{const value=await api()[command.method]<unknown>(command.path,command.body);if(!command.validate(value))throw new Error('Unconfirmed command response');if(!current()||command.store!==store.value)return;command.accept(value);pending=null;uncertain.value=false}
  catch(e){if(current()&&command.store===store.value){errorCode.value=e instanceof WorkforceApiError?e.code:'';uncertain.value=!(e instanceof WorkforceApiError&&e.status>=400&&e.status<500);if(!uncertain.value)pending=null;failure.value=uncertain.value?(english()?'The result is unconfirmed. Keep this page open and retry the exact command.':'提交结果尚未确认。请保留本页并重试原命令。'):message(e)}}
  finally{if(current())busy.value=false}
 }
 function run<T>(path:string,body:unknown,method:'put'|'post',validate:(v:any)=>boolean,accept:(v:T)=>void){if(locked.value)return;pending={path,body:JSON.parse(JSON.stringify(body)),method,validate,accept,store:store.value};return replay()}
 return {busy,uncertain,locked,failure,errorCode,run,replay}
}
