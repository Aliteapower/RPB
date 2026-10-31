import {ref} from 'vue'
import {useWorkforce} from './context'
import type {EmployeeExpiryPage,EmployeeExpiryCount} from './documentExpiry'
/** One scoped batch for the current employee page; old page results cannot replace it. */
export function useEmployeeExpiryCounts(){
 const {api,store,current}=useWorkforce(),counts=ref<Record<string,EmployeeExpiryCount>>({}),loading=ref(false),failed=ref(false);let revision=0
 async function read(ids:string[]){const rev=++revision,owner=store.value;counts.value={};failed.value=false;loading.value=ids.length>0;if(!ids.length)return;try{const result=await api().get<EmployeeExpiryPage>('/document-expiries/employees?'+new URLSearchParams({employeeIds:ids.join(','),days:'30'}));if(current()&&rev===revision&&owner===store.value)counts.value=Object.fromEntries(result.items.filter(row=>ids.includes(row.employeeId)).map(row=>[row.employeeId,row]))}catch{if(current()&&rev===revision&&owner===store.value)failed.value=true}finally{if(current()&&rev===revision)loading.value=false}}
 return {counts,loading,failed,read}
}
