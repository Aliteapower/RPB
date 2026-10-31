import type {Employee,EmployeeWrite,Home} from './types'
export function localToday(){const parts=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Singapore',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());return `${parts.find(p=>p.type==='year')!.value}-${parts.find(p=>p.type==='month')!.value}-${parts.find(p=>p.type==='day')!.value}`}
export function employeeDraft(home:Home,store:string,e?:Employee):EmployeeWrite {
 if(e){const {id,version,editable,...fields}=e;return {...fields,expectedVersion:version}}
 const today=localToday(),company=home.stores.find(s=>s.id===store)?.companyId??home.companies[0]?.id??null
 return {expectedVersion:0,employeeNo:'',name:'',birthDate:null,englishName:'',enabled:true,phone:'',email:'',jobTitle:'',hireDate:today,leaveDate:null,status:'active',companyId:company,companyEffectiveDate:today,mainStoreId:store,storeIds:[store],accountId:null}
}
