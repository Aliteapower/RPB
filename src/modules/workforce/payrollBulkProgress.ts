import {computed,ref,watch} from 'vue'
import type {Ref} from 'vue'
import {WorkforceApiError} from './api'
import type {BatchSummary} from './payrollTypes'
import type {PayrollBulkAction,PayrollBulkWrite,PayrollBulkResult} from './payrollProgressTypes'
import {usePayrollPage} from './payrollPage'
export const bulkPayrollEligible=(batch:BatchSummary)=>['draft','calculated'].includes(batch.status)
export function usePayrollBulkProgress(items:Ref<BatchSummary[]>,done:()=>void){
 const {api,current,store,busy,failure,write,r,base,message}=usePayrollPage(),selected=ref<string[]>([]),note=ref(''),acknowledged=ref(false),result=ref<PayrollBulkResult|null>(null),command=ref<{id:string;body:PayrollBulkWrite}|null>(null)
 const blocked=computed(()=>busy.value||!!command.value),valid=computed(()=>selected.value.length>=1&&selected.value.length<=20&&!!note.value.trim()&&note.value.length<=500)
 watch(items,()=>{if(!command.value){selected.value=[];acknowledged.value=false}})
 function toggle(batch:BatchSummary,checked:boolean){if(blocked.value||!bulkPayrollEligible(batch))return;acknowledged.value=false;if(checked){if(selected.value.length<20&&!selected.value.includes(batch.id))selected.value=[...selected.value,batch.id]}else selected.value=selected.value.filter(id=>id!==batch.id)}
 function start(action:PayrollBulkAction){if(blocked.value||!valid.value||(action==='confirm'&&!acknowledged.value))return;const entries=selected.value.map(id=>items.value.find(batch=>batch.id===id));if(entries.some(batch=>!batch||!bulkPayrollEligible(batch))){failure.value=r('invalid');return}command.value={id:crypto.randomUUID(),body:{action,note:note.value.trim(),entries:entries.map(batch=>({batchId:batch!.id,expectedVersion:batch!.version}))}};result.value=null;recover()}
 function recover(){if(busy.value||!command.value)return;const pending=command.value,owner=store.value;void write(async()=>{try{const response=await api().put<PayrollBulkResult>('/payroll-batch-operations/'+pending.id,pending.body);if(current()&&owner===store.value){result.value=response;command.value=null;selected.value=[];acknowledged.value=false}}catch(e){if(current()&&owner===store.value&&e instanceof WorkforceApiError&&e.status>=400&&e.status<500)command.value=null;throw e}},done)}
 return {base,message,selected,note,acknowledged,result,command,blocked,valid,busy,failure,toggle,start,recover}
}

