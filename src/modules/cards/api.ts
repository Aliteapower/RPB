import {moduleRequest as httpRequest,ModuleApiError} from '../shared-commerce/http'
export {ModuleApiError} from '../shared-commerce/http'
export function moduleRequest<T>(base:string,path:string,method='GET',body?:unknown){return httpRequest<T>(base,path,method,body,{'X-RPB-Wallet':'1','X-RPB-Wallet-Model':'components-v2'})}
interface Pending {operation:string;key:string}
export function cardApi(store:string,customer=false){const base=`/api/v1/${customer?'public/':''}stores/${encodeURIComponent(store)}/wallet/cards`,storage=`rpb.cards.pending:${customer?'customer':'staff'}:${store}`;
 const pending=():Pending|null=>{try{return JSON.parse(sessionStorage.getItem(storage)??'null')}catch{return null}};
 return {get:<T>(path:string)=>moduleRequest<T>(base,path),post:<T>(path:string,body:unknown={})=>moduleRequest<T>(base,path,'POST',body),put:<T>(path:string,body:unknown)=>moduleRequest<T>(base,path,'PUT',body),pending,
  async command<T>(path:string,body:Record<string,unknown>,operation:string):Promise<T>{if(pending())throw new ModuleApiError(409,'CARD_PENDING','有待恢复的操作 / Recover the pending operation first');const key=crypto.randomUUID();sessionStorage.setItem(storage,JSON.stringify({operation,key}));try{const result=await moduleRequest<T>(base,path,'POST',{...body,idempotencyKey:key});sessionStorage.removeItem(storage);return result}catch(e){if(e instanceof ModuleApiError&&e.status<500)sessionStorage.removeItem(storage);throw e}},
  async recover(){const p=pending();if(!p)return null;let result:unknown;try{result=await moduleRequest(base,`/commands/${p.operation}/${p.key}`)}catch(e){if(!(e instanceof ModuleApiError&&e.status===404))throw e;result=await moduleRequest(base,`/commands/${p.operation}/${p.key}/reconcile`,'POST',{})}sessionStorage.removeItem(storage);return result}
 }
}
