export interface Pending {path:string;body:Record<string,unknown>}
export class StorageApiError extends Error {constructor(readonly status:number,readonly code:string,message:string,readonly problems:unknown[]=[]){super(message)}}
export function storageApi(store:string,customer:boolean){
 const base=`/api/v1/${customer?'public/':''}stores/${encodeURIComponent(store)}/wine-storage`,key=`rpb.storage.pending:${customer}:${store}`
 async function request<T>(path:string,body?:unknown):Promise<T>{const response=await fetch(base+path,{method:body===undefined?'GET':'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-RPB-Storage':'1'},body:body===undefined?undefined:JSON.stringify(body)});const data=await response.json();if(!response.ok)throw new StorageApiError(response.status,data.code,data.message,data.problems);return data}
 function pending():Pending|null {try{return JSON.parse(sessionStorage.getItem(key)??'null')}catch{return null}}
 async function execute<T>(p:Pending):Promise<T>{try{const result=await request<T>(p.path,p.body);sessionStorage.removeItem(key);return result}catch(e){if(e instanceof StorageApiError&&e.status<500)sessionStorage.removeItem(key);throw e}}
 return {base,get:<T>(path:string)=>request<T>(path),scan:(code:string)=>request<import('./types').Intent>('/scan',{code}),pending,
  command:<T>(path:string,body:Record<string,unknown>={})=>{if(pending())throw new Error('请先恢复未确定操作 / Recover pending operation');const p={path,body:{...body,idempotencyKey:crypto.randomUUID()}};sessionStorage.setItem(key,JSON.stringify(p));return execute<T>(p)},
  recover:()=>{const p=pending();return p?execute<unknown>(p):Promise.resolve(null)},
  async upload(file:File,customerId?:string,phase='receipt'){const form=new FormData();form.set('image',file);if(customerId){form.set('customerId',customerId);form.set('phase',phase)}const response=await fetch(base+'/photos',{method:'POST',credentials:'same-origin',headers:{'X-RPB-Storage':'1'},body:form});const data=await response.json();if(!response.ok)throw new StorageApiError(response.status,data.code,data.message);return data.id as string}
 }
}
export type StorageApi=ReturnType<typeof storageApi>
