import test from 'node:test'
import assert from 'node:assert/strict'
import {createRenderer,defineComponent,h,nextTick,ref} from 'vue'
import {createI18n} from 'vue-i18n'
import History from '../../src/components/platform/PlatformSubscriptionHistory.vue'
import {listSubscriptionHistory} from '../../src/api/platformSubscriptionHistoryApi.ts'

const event={id:'event1',eventType:'renew',billingCycle:'monthly',status:'active',periodStart:'2026-01-01T00:00:00Z',periodEnd:'2026-02-01T00:00:00Z',amount:128.25,currency:'SGD',paymentNote:'已确认商业记录',createdAt:'2026-01-01T00:00:00Z',snapshotAvailable:true,stores:[{storeId:'old-store',storeCode:'old-code',storeName:'历史店名',operatingEntityName:'历史主体',itemStatus:'active',storeStatus:'inactive',quantity:1,amount:128.25,unitAmount:128.25,currency:'SGD',periodStart:'2026-01-01T00:00:00Z',periodEnd:'2026-02-01T00:00:00Z'}]}
const tick=async()=>{await new Promise(r=>setTimeout(r,0));await nextTick()}
test('history API is authenticated no-store and preserves abort signal and permission errors',async()=>{
 const controller=new AbortController();let request;
 await listSubscriptionHistory('tenant/a','sub/a',controller.signal,async(url,options)=>{request={url,options};return new Response('{"events":[]}')});
 assert.match(request.url,/tenant%2Fa/);assert.equal(request.options.credentials,'include');assert.equal(request.options.cache,'no-store');assert.equal(request.options.signal,controller.signal);
 await assert.rejects(listSubscriptionHistory('t','s',undefined,async()=>new Response('{"success":false,"error":{"code":"FORBIDDEN","messageKey":"platform.billing.forbidden"}}',{status:403})),e=>e.status===403);
 await assert.rejects(listSubscriptionHistory('t','s',undefined,async()=>new Response('<html>login</html>')),e=>e.status===200);
})
test('renders historical names, amount, period, legacy notice and commercial meaning',async()=>{
 const old=globalThis.fetch;try{globalThis.fetch=async()=>new Response(JSON.stringify({events:[event,{...event,id:'legacy',snapshotAvailable:false,stores:[]}]}));const m=mount();await tick();const text=textOf(m.root);for(const label of ['历史店名','历史主体','128.25','2026-02-01','银行实收','未保存门店快照'])assert.ok(text.includes(label),label);m.app.unmount()}finally{globalThis.fetch=old}
})
test('tenant/subscription switches clear records, abort old calls and reject late responses',async()=>{
 const old=globalThis.fetch,pending=[];try{globalThis.fetch=(url,options)=>new Promise(resolve=>pending.push({url,options,resolve}));const m=mount();await tick();assert.match(textOf(m.root),/加载/);m.tenant.value='tenant2';m.subscription.value='sub2';await tick();assert.equal(pending[0].options.signal.aborted,true);pending[1].resolve(new Response('{"events":[]}'));await tick();pending[0].resolve(new Response(JSON.stringify({events:[event]})));await tick();assert.doesNotMatch(textOf(m.root),/历史店名/);assert.match(textOf(m.root),/暂无商业操作记录/);m.app.unmount()}finally{globalThis.fetch=old}
})
test('permission failure is displayed without retaining records',async()=>{
 const old=globalThis.fetch;try{globalThis.fetch=async()=>new Response('{"success":false,"error":{"code":"FORBIDDEN","messageKey":"platform.billing.forbidden"}}',{status:403});const m=mount();await tick();assert.match(textOf(m.root),/无权查看/);assert.doesNotMatch(textOf(m.root),/历史店名/);m.app.unmount()}finally{globalThis.fetch=old}
})
function mount(){const root={children:[]},tenant=ref('tenant1'),subscription=ref('sub1');const renderer=createRenderer({createElement:tag=>({tag,children:[],props:{}}),createText:text=>({text}),createComment:comment=>({comment}),setText:(n,text)=>n.text=text,setElementText:(n,text)=>{n.text=text;n.children=[]},parentNode:n=>n?.parent||null,nextSibling:()=>null,insert:(n,p,anchor)=>{if(n.parent)n.parent.children=n.parent.children.filter(c=>c!==n);n.parent=p;const i=anchor?p.children.indexOf(anchor):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n.parent)n.parent.children=n.parent.children.filter(c=>c!==n)},patchProp:(n,key,old,value)=>n.props[key]=value});const app=renderer.createApp(defineComponent({setup:()=>()=>h(History,{tenantId:tenant.value,subscriptionId:subscription.value})}));app.use(createI18n({legacy:false,locale:'zh-CN',messages:{'zh-CN':{},en:{}}}));app.mount(root);return {root,app,tenant,subscription}}
function textOf(n){return [n.text||'',...(n.children||[]).map(textOf)].join(' ')}
