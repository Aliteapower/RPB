import {inject,onBeforeUnmount,type InjectionKey,type Ref} from 'vue'
import type {Home} from './types'
import type {WorkforceApi} from './api'
import type {PayrollHomeData} from './payrollTypes'
export interface WorkforceContext {store:Ref<string>;home:Ref<Home|null>;payrollHome?:Ref<PayrollHomeData|null>;payrollFailure?:Ref<string>;api:()=>WorkforceApi;refresh:()=>Promise<void>}
export const workforceKey:InjectionKey<WorkforceContext>=Symbol('workforce')
export function useWorkforce(){const context=inject(workforceKey);if(!context)throw new Error('workforce context missing');let active=true;onBeforeUnmount(()=>{active=false});return {...context,current:()=>active}}
