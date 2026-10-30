import {test,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {createRenderer,h,nextTick} from 'vue'
import {createI18n} from 'vue-i18n'
import {createRouter,createMemoryHistory} from 'vue-router'
import {createPinia} from 'pinia'
import Page from '../../src/pages/PlatformTenantBillingPage.vue'
import {zhCN as zh} from '../../src/i18n/locales/zh-CN.ts'
import {enSG as en} from '../../src/i18n/locales/en-SG.ts'
const element=tag=>({tag,props:{},children:[],style:{},addEventListener(){},removeEventListener(){},getRootNode(){return {}},get options(){return nodes(this,n=>n.tag==='option')}})
const renderer=createRenderer({createElement:element,createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>{n.text=t;n.children=[]},patchProp:(n,k,o,v)=>{n.props[k]=v;if(k==='value')n.value=v},insert:(n,p,a)=>{if(n.parent)n.parent.children.splice(n.parent.children.indexOf(n),1);n.parent=p;const i=a?p.children.indexOf(a):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n.parent,nextSibling:n=>{const a=n.parent?.children||[];return a[a.indexOf(n)+1]||null}})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const text=n=>(n.text||'')+(n.children||[]).map(text).join('')
const settle=async()=>{for(let i=0;i<20;i++){await Promise.resolve();await nextTick()}}
const button=(root,label)=>nodes(root,n=>n.tag==='button'&&text(n)===label)[0]
const checkbox=(root,label)=>nodes(root,n=>n.tag==='label'&&text(n)===label).flatMap(n=>nodes(n,c=>c.tag==='input'))[0]
const apps=[];const oldFetch=globalThis.fetch;let calls=[]
afterEach(()=>{apps.splice(0).forEach(app=>app.unmount());globalThis.fetch=oldFetch;calls=[]})
const stores=[{id:'old',storeName:'食刻演示',storeCode:'main',status:'active',operatingEntityId:'entity'},{id:'new',storeName:'牛车水店',storeCode:'chinatown',status:'active',operatingEntityId:'entity'},{id:'inactive',storeName:'停用店',status:'inactive',operatingEntityId:'entity'}]
const item={id:'item',storeId:'old',storeName:'食刻演示',storeCode:'main',status:'active',amount:30,currency:'SGD',version:2}
async function mount({appKey='workforce',purchased=true,denied=false,inactivePrice=false,multiple=false,itemStatus='active',itemEnd=null}={}){
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document={}
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/platform/tenants/:tenantId/billing',component:Page},{path:'/:pathMatch(.*)*',component:{render:()=>null}}]});await router.push('/platform/tenants/T/billing')
 globalThis.fetch=async(url,opts={})=>{calls.push({url,opts,body:opts.body?JSON.parse(opts.body):null});if(denied)return new Response(JSON.stringify({success:false,error:{code:'FORBIDDEN',messageKey:'forbidden'}}),{status:403});let body={};if(url.includes('/history'))body={events:[]};else if(url.endsWith('/stores'))body={stores};else if(url.endsWith('/product-lines'))body={productLines:[{appKey,displayName:appKey,prices:[{billingCycle:'monthly',amount:30,currency:'SGD',status:inactivePrice?'inactive':'active'}]},...(multiple?[{appKey:'daily_ledger',displayName:'daily_ledger',prices:[{billingCycle:'monthly',amount:30,currency:'SGD',status:'active'}]}]:[])]};else if(url.endsWith('/product-subscriptions'))body={subscriptions:purchased?[{id:'sub',appKey,version:3,billingCycle:'monthly',status:'active',items:[{...item,status:itemStatus,currentPeriodEnd:itemEnd}]}]:[]};return new Response(JSON.stringify(body))}
 const root=element('root');const app=renderer.createApp({render:()=>h(Page)});app.use(createPinia());app.use(router);app.use(createI18n({legacy:false,locale:'zh-CN',messages:{'zh-CN':zh,en},missingWarn:false,fallbackWarn:false}));app.mount(root);apps.push(app);await settle();return root
}
const form=root=>nodes(root,n=>n.tag==='form')[0]
const labelInput=(root,name)=>nodes(root,n=>n.tag==='label'&&text(n).includes(name)).flatMap(n=>nodes(n,c=>c.tag==='input'&&c.props.type==='checkbox'))[0]
const submit=async root=>{await form(root).props.onSubmit({preventDefault(){},stopPropagation(){}});await settle()}
test('all products add only selected active unpurchased stores',async()=>{
 for(const appKey of ['workforce','daily_ledger','wine_storage','reservation_queue','payment','wallet']){
  const root=await mount({appKey});const mode=labelInput(root,'开通新增门店');assert.ok(mode,appKey);mode.props['onUpdate:modelValue'](true);await settle();assert.ok(labelInput(root,'牛车水店'));assert.equal(labelInput(root,'停用店'),undefined);assert.equal(labelInput(root,'食刻演示'),undefined)
  labelInput(root,'牛车水店').props['onUpdate:modelValue'](['new']);await settle();await submit(root);const sent=calls.findLast(c=>c.opts.method==='POST');assert.match(sent.url,/sub\/add-stores$/);assert.deepEqual(sent.body.storeIds,['new']);assert.equal(sent.body.appKey,appKey);assert.equal(sent.body.version,3)
 }
})
test('new product purchase selects store rather than all stores',async()=>{
 const root=await mount({purchased:false});assert.ok(labelInput(root,'牛车水店'));labelInput(root,'牛车水店').props['onUpdate:modelValue'](['new']);await settle();await submit(root);const sent=calls.findLast(c=>c.opts.method==='POST');assert.match(sent.url,/purchase$/);assert.deepEqual(sent.body.storeIds,['new'])
})
test('renew existing item retains item version and exact store',async()=>{
 const root=await mount();await submit(root);const sent=calls.findLast(c=>c.opts.method==='POST');assert.match(sent.url,/items\/item\/renew$/);assert.deepEqual(sent.body.storeIds,['old']);assert.equal(sent.body.version,2)
})
test('permission failure blocks billing with missing data',async()=>{
 const root=await mount({denied:true});assert.match(text(root),/权限|无权/);assert.equal(nodes(root,n=>n.tag==='button'&&n.props.type==='submit')[0].props.disabled,true);assert.equal(calls.filter(c=>c.opts.method==='POST').length,0)
})


test('inactive price and no selected store cannot submit even via direct form handler',async()=>{
 const root=await mount({purchased:false,inactivePrice:true});assert.match(text(root),/价格/);await submit(root);assert.equal(calls.filter(c=>c.opts.method==='POST').length,0)
})
test('switching product clears store selection and add mode',async()=>{
 const root=await mount({multiple:true});labelInput(root,'开通新增门店').props['onUpdate:modelValue'](true);await settle();labelInput(root,'牛车水店').props['onUpdate:modelValue'](['new']);await settle();nodes(root,n=>n.tag==='select')[0].props['onUpdate:modelValue']('daily_ledger');await settle();await submit(root);assert.equal(calls.filter(c=>c.opts.method==='POST').length,0)
})

test('cancelled purchased item is not offered as an additional new store',async()=>{
 const root=await mount({itemStatus:'cancelled'});labelInput(root,'开通新增门店').props['onUpdate:modelValue'](true);await settle();assert.equal(labelInput(root,'食刻演示'),undefined);assert.ok(labelInput(root,'牛车水店'))
})

test('all products retain cancelled and expired items for explicit single-store renewal',async()=>{
 for(const appKey of ['daily_ledger','wine_storage','reservation_queue','payment','wallet','workforce'])for(const itemStatus of ['cancelled','expired']){
  const root=await mount({appKey,itemStatus});await submit(root);const sent=calls.findLast(c=>c.opts.method==='POST');assert.ok(sent,`${appKey}/${itemStatus}`);assert.match(sent.url,/items\/item\/renew$/);assert.deepEqual(sent.body.storeIds,['old']);calls=[]
 }
})
test('all product lines show expired end dates rather than active state',async()=>{
 for(const appKey of ['daily_ledger','wine_storage','reservation_queue','payment','wallet','workforce']){
  const root=await mount({appKey,itemEnd:'2020-01-01T00:00:00Z'});assert.match(text(root),/已到期/,appKey)
 }
})
