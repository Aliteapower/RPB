import {computed} from 'vue'
import {useRoute,type RouteLocationRaw} from 'vue-router'
import {useWorkforce} from './context'
import {validMonth} from './payrollUi'
/** Only a structured, authorized workforce return is accepted; never an arbitrary URL. */
export function payrollReturnLocation(q:Record<string,unknown>,currentStore:string,stores:readonly {id:string}[]):RouteLocationRaw|null {
 const text=(k:string)=>typeof q[k]==='string'?q[k] as string:''
 const company=text('companyId'),month=text('month'),store=text('returnStoreId')||currentStore
 if(q.fromPayroll!=='1'||!company||!validMonth(month)||(store!==currentStore&&!stores.some(s=>s.id===store)))return null
 const scroll=text('payScroll');return {path:'/stores/'+encodeURIComponent(store)+'/workforce/payroll',query:{companyId:company,month,paySearch:text('paySearch').slice(0,100)||undefined,payIssues:q.payIssues==='1'?'1':undefined,payEmployee:text('payEmployee').slice(0,100)||undefined,payScroll:/^\d{1,6}$/.test(scroll)?scroll:undefined}}
}
export function usePayrollReturn(){const route=useRoute(),{store,home}=useWorkforce();return computed(()=>payrollReturnLocation(route.query,store.value,home.value?.stores||[]))}
