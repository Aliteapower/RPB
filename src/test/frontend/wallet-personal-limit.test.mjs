import {test,before,after} from 'node:test'
import assert from 'node:assert/strict'
import {createServer} from 'vite'
import {createRenderer,ref,nextTick} from 'vue'
let vite,usePersonalCode
const oldFetch=globalThis.fetch,oldWindow=globalThis.window,oldDocument=globalThis.document
const renderer=createRenderer({createElement:()=>({}),createComment:()=>({}),createText:()=>({}),insert(){},remove(){},setText(){},setElementText(){},patchProp(){},parentNode(){return null},nextSibling(){return null}})
const tick=async()=>{for(let i=0;i<12;i++){await Promise.resolve();await nextTick()}}
before(async()=>{globalThis.window={setInterval:()=>1,clearInterval(){}};globalThis.document={visibilityState:'visible'};vite=await createServer({configFile:false,cacheDir:'target/wallet-limit-test-cache',server:{middlewareMode:true,hmr:false},appType:'custom',optimizeDeps:{noDiscovery:true,entries:[]}});({usePersonalCode}=await vite.ssrLoadModule('/src/modules/wallet/usePersonalCode.ts'))})
after(async()=>{await vite?.close();globalThis.fetch=oldFetch;globalThis.window=oldWindow;globalThis.document=oldDocument})
function mount(){let state;const app=renderer.createApp({setup(){state=usePersonalCode(ref('own-store'),ref(true));return()=>null}});app.mount({});return{state,unmount:()=>app.unmount()}}
function server(){let value={configured:false,policyVersion:0,limitCents:null,consentedLimitCents:null,paymentEnabled:false,requiresConsent:true,ruleVersion:1};const calls=[];let codes=0
 globalThis.fetch=async(url,options={})=>{const body=options.body&&JSON.parse(options.body);calls.push({url,body});if(url.endsWith('/personal-codes'))return{ok:true,json:async()=>({code:'WI.'+(++codes),paymentEnabled:value.paymentEnabled,limitCents:value.limitCents})};if(body){assert.equal(body.policyVersion,value.policyVersion);value={...value,configured:true,policyVersion:value.policyVersion+1,limitCents:body.limitCents,consentedLimitCents:body.limitCents,paymentEnabled:body.accepted,requiresConsent:!body.accepted}}return{ok:true,json:async()=>({...value})}}
 return{calls,consents:()=>calls.filter(c=>c.body&&c.url.endsWith('/personal-payment-consent'))}
}
test('customer selects an amount once, subsequent code refresh and remount reuse persisted authorization',async()=>{
 const api=server(),scope=mount();await tick();assert.equal(scope.state.consent.value.paymentEnabled,false)
 await scope.state.accept(true,2500);await tick();assert.equal(scope.state.consent.value.limitCents,2500);assert.equal(scope.state.code.value.paymentEnabled,true)
 assert.equal(api.consents().length,1);assert.equal(api.consents()[0].body.limitCents,2500);assert.ok(api.consents()[0].url.includes('/public/stores/own-store/'))
 for(let i=0;i<3;i++){await scope.state.issue();await tick();assert.equal(scope.state.code.value.limitCents,2500)}
 scope.unmount();const reopened=mount();await tick();assert.equal(reopened.state.consent.value.paymentEnabled,true);assert.equal(reopened.state.code.value.paymentEnabled,true);assert.equal(api.consents().length,1);reopened.unmount()
})
test('invalid amounts never authorize; updates and disable use latest personal revision',async()=>{
 const api=server(),scope=mount();await tick();for(const n of [undefined,0,-1,0.5,Infinity,10_000_000_000_000])await scope.state.accept(true,n);assert.equal(api.consents().length,0)
 await scope.state.accept(true,1000);await tick();await scope.state.accept(true,5000);await tick();assert.equal(api.consents()[1].body.policyVersion,1);assert.equal(scope.state.code.value.limitCents,5000)
 await scope.state.accept(false);await tick();assert.equal(api.consents()[2].body.policyVersion,2);assert.equal(scope.state.code.value.paymentEnabled,false);assert.ok(api.calls.every(c=>!c.url.includes('personal-payment-policy')));scope.unmount()
})
test('a stale save refreshes personal settings while retaining its error and never reports success',async()=>{
 server();const scope=mount();await tick();const handler=globalThis.fetch
 globalThis.fetch=async(url,options={})=>url.endsWith('/personal-payment-consent')?options.body?{ok:false,status:409,json:async()=>({code:'WALLET_POLICY_STALE',message:'您的付款授权已更新，请刷新后重新确认'})}:{ok:true,json:async()=>({configured:true,policyVersion:2,limitCents:2000,consentedLimitCents:2000,paymentEnabled:true})}:handler(url,options)
 await scope.state.accept(true,1000);await tick();assert.match(scope.state.error.value,/已更新/);assert.equal(scope.state.consent.value.limitCents,2000);assert.equal(scope.state.consent.value.policyVersion,2);scope.unmount()
})
