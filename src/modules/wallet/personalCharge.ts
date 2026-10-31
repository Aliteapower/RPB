import { walletApi, WalletApiError } from './api'
import { chargePreview } from './scanCharge'
import type { Bill, Account } from './types'
export type PersonalChargeResult = { status: 'paid'|'confirmation_required'; bill: Bill; account?: Account } | {status:'not_submitted';bill:null;account:null;storeId:string}
/** Persist the operation key and displayed intent; the one-use credential stays in memory. */
export function createPersonalChargeAttempt(store: string, actorId: string, currentActor:()=>string=()=>actorId) {
 const slot='rpb.wallet.personal-attempt.'+encodeURIComponent(store)
 let key: string|null=null,ownerAccountId:string|null=null,payload: {customerCode:string;amount:string;description:string}|null=null, intent:{amount:string;description:string}|null=null,running=false
 try {const stored=sessionStorage.getItem(slot);if(stored){
  try{const saved=JSON.parse(stored) as {key:string;amount:string;description:string;ownerAccountId?:string};key=saved.key;ownerAccountId=saved.ownerAccountId??null;intent={amount:saved.amount,description:saved.description}}
  catch{if(/^[0-9a-f-]{36}$/i.test(stored))key=stored}
 }} catch { /* memory fallback */ }
 function assertOwner(){if(!actorId||currentActor()!==actorId||(key&&ownerAccountId!==actorId))throw Error('Use the original cashier account to resolve this payment')}
 function clear(){
  const completed=key;key=null;payload=null;intent=null
  try{const stored=sessionStorage.getItem(slot);if(!stored)return
   let storedKey=stored;try{storedKey=(JSON.parse(stored) as {key:string}).key}catch{/* older opaque-key format */}
   if(storedKey===completed)sessionStorage.removeItem(slot)
  }catch{/* unavailable */}
 }
 function known(result:PersonalChargeResult){
  if((result.status==='paid'||result.status==='confirmation_required')&&result.bill?.id)return result
  if(result.status==='not_submitted'&&result.bill===null)return result
  throw Error('Transaction result could not be read. Check the original transaction.')
 }
 async function recover(){
  assertOwner()
  if(!key||running)throw Error('No pending transaction')
  running=true
  try {const result=known(await walletApi(store).get<PersonalChargeResult>('/personal-scan-charges/result?idempotencyKey='+encodeURIComponent(key)));assertOwner();clear();return result}
  finally{running=false}
 }
 async function scan(code:string,amount:string|number,description:string){
  assertOwner()
  if(running||key)throw Error('Resolve the pending transaction first')
  if(!/^WI\.[A-Za-z0-9_-]{43}$/.test(code)||!chargePreview(amount,0))throw Error('Invalid personal code or amount')
  key=crypto.randomUUID();ownerAccountId=actorId;payload={customerCode:code,amount:String(amount),description}
  intent={amount:String(amount),description}
  try{sessionStorage.setItem(slot,JSON.stringify({key,ownerAccountId,...intent}))}catch{/* memory fallback */}
  return send()
 }
 async function send(){
  assertOwner()
  if(running||!key||!payload)throw Error('Scan a fresh personal code')
  running=true
  try{const result=known(await walletApi(store).post<PersonalChargeResult>('/personal-scan-charges',{...payload,idempotencyKey:key}));assertOwner();clear();return result}
  catch(e){if(e instanceof WalletApiError&&e.status>=400&&e.status<500&&currentActor()===actorId)clear();throw e}
  finally{running=false}
 }
 async function retry(){
  try{return await recover()}catch(e){
   if(!(e instanceof WalletApiError)||e.status!==404||!key)throw e
   assertOwner()
   if(running)throw Error('Transaction is being checked')
   running=true
   try{const result=known(await walletApi(store).post<PersonalChargeResult>('/personal-scan-charges/reconcile',{idempotencyKey:key}));assertOwner();clear();return result}
   finally{running=false}
  }
 }
 return{scan,recover,retry,get pending(){return !!key},get ownerMismatch(){return !!key&&(!ownerAccountId||ownerAccountId!==actorId||currentActor()!==actorId)},get intent(){return intent}}
}
