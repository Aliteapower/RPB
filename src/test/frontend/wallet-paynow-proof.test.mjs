import {test,before,after} from 'node:test'
import assert from 'node:assert/strict'
import {parse,compileScript} from '@vue/compiler-sfc'
import {createRenderer,h,nextTick,reactive,ref} from 'vue'
import {routeLocationKey,routerKey} from 'vue-router'
import {createI18n} from 'vue-i18n'
import {walletRoutes} from '../../modules/wallet/routes.ts'
import {readFile,readdir,mkdir,writeFile} from 'node:fs/promises'
let StaffShell,CustomerShell,Page,Detail,Row,query,api,composable,messages
const oldFetch=globalThis.fetch,oldDocument=globalThis.document,oldWindow=globalThis.window
const tick=async()=>{for(let i=0;i<8;i++){await Promise.resolve();await nextTick()}}
const renderer=createRenderer({
 createElement:tag=>({tag,tagName:tag.toUpperCase(),children:[],props:{},value:'',get options(){return this.children.filter(c=>c.tag==='option')},listeners:{},addEventListener(name,fn){this.listeners[name]=fn},removeEventListener(){},setAttribute(){},removeAttribute(){},getRootNode(){return globalThis.document}}),createText:text=>({text}),createComment:()=>({}),
 setText:(node,text)=>{node.text=text},setElementText:(node,text)=>{node.text=text},
 patchProp:(node,key,old,value)=>{node.props[key]=value;if(key==='value'){node._value=value;node.value=value}},
 insert:(child,parent,anchor)=>{if(child.parent){const i=child.parent.children.indexOf(child);if(i>=0)child.parent.children.splice(i,1)}child.parent=parent;const i=anchor?parent.children.indexOf(anchor):-1;if(i<0)parent.children.push(child);else parent.children.splice(i,0,child)},
 remove:node=>{if(node.parent){const i=node.parent.children.indexOf(node);if(i>=0)node.parent.children.splice(i,1);node.parent=null}},
 parentNode:node=>node.parent,nextSibling:node=>{const c=node.parent?.children||[];return c[c.indexOf(node)+1]||null}
})
function nodes(root,predicate){let out=[];if(predicate(root))out.push(root);for(const c of root.children||[])out.push(...nodes(c,predicate));return out}


const text=root=>[root.text||'',...(root.children||[]).map(text)].join(' ')
const response=body=>({ok:true,status:200,json:async()=>body})
let StaffHistory,CustomerBill,CustomerProof,customerHook,Form,Upload,proofHook
const oldCreate=URL.createObjectURL,oldRevoke=URL.revokeObjectURL
function mountProof(component,props={},locale='zh-CN'){
 const root={children:[]},app=renderer.createApp({setup:()=>()=>h(component,props)})
 app.use(createI18n({legacy:false,locale,messages:{'zh-CN':{wallet:messages.walletZh},'en-SG':{wallet:messages.walletEn}}}));app.mount(root);return{root,unmount:()=>app.unmount()}
}
before(async()=>{
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document=new Document();URL.createObjectURL=()=> 'blob:test';URL.revokeObjectURL=()=>{}
 const {build}=await import('esbuild'),{pathToFileURL}=await import('node:url')
 const result=await build({stdin:{contents:`export {default as StaffHistory} from './src/modules/wallet/proof/WalletProofHistory.vue';export {default as CustomerBill} from './src/modules/wallet/components/WalletCustomerBill.vue';export {default as CustomerProof} from './src/modules/wallet/proof/WalletCustomerPayNowProof.vue';export * as customerHook from './src/modules/wallet/proof/useCustomerPayNowProof.ts';export {default as Form} from './src/modules/wallet/components/WalletCollectionForm.vue';export {default as Upload} from './src/components/payment/PaymentProofUpload.vue';export * as proofHook from './src/modules/wallet/proof/useWalletPayNowProof.ts';export * as messages from './src/modules/wallet/locales.ts'`,resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,platform:'node',format:'esm',external:['vue','vue-i18n','qrcode'],plugins:[{name:'vue',setup(b){b.onLoad({filter:/\.vue$/},async({path})=>{const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}})}}]})
 const dir='target/wallet-proof-component-tests';await mkdir(dir,{recursive:true});await writeFile(dir+'/entry.mjs',result.outputFiles[0].contents);({StaffHistory,CustomerBill,CustomerProof,customerHook,Form,Upload,proofHook,messages}=await import(pathToFileURL(process.cwd()+'/'+dir+'/entry.mjs').href+'?'+Date.now()))
})
after(()=>{globalThis.window=oldWindow;globalThis.fetch=oldFetch;globalThis.document=oldDocument;URL.createObjectURL=oldCreate;URL.revokeObjectURL=oldRevoke})
const context={sourceType:'topup',sourceId:'one',channel:'paynow',systemReference:'W0123456789abcdefabcd',amountCents:1000,expectedAccount:'202012345A',merchantName:'Shop'}
const evidence=(checks={reference:'match',amount:'match',account:'match',status:'match'})=>({id:'proof-one',context,bankReference:'BANK-123',checks,extracted:{},confirmation:{},imageUrl:'/private/image'})
const props=()=>reactive({store:'A',sourceType:'topup',sourceId:'one',amountCents:1000,fixedChannel:'paynow',channels:['paynow'],busy:false,paynowConfigured:true,topup:true})
const button=(scope,label)=>nodes(scope.root,n=>n.tag==='button'&&text(n).trim()===label)[0]
const choose=(scope,file)=>nodes(scope.root,n=>n.tag==='input'&&n.props.type==='file')[0].props.onChange({target:{files:[file],value:'x'}})

test('shared upload validates images and revokes previews on replacement and disposal',async()=>{
 const changes=[],revoked=[];URL.revokeObjectURL=value=>revoked.push(value)
 const p=reactive({modelValue:null,captureLabel:'photo',albumLabel:'album',emptyLabel:'none',invalidLabel:'invalid',maxBytes:4,'onUpdate:modelValue':f=>{p.modelValue=f;changes.push(f)}}),scope=mountProof(Upload,p)
 choose(scope,new File(['text'],'bad.txt',{type:'text/plain'}));await tick();assert.equal(changes.length,0);assert.match(text(scope.root),/invalid/)
 choose(scope,new File(['abc'],'one.png',{type:'image/png'}));await tick();assert.equal(changes.length,1);assert.equal(nodes(scope.root,n=>n.tag==='img').length,1)
 choose(scope,new File(['def'],'two.png',{type:'image/png'}));await tick();assert.equal(revoked.length,1);scope.unmount();assert.equal(revoked.length,2)
})

test('wallet carries system reference separately and upload never posts a confirmation; mismatches block submit',async()=>{
 const requests=[],confirmations=[];let mismatch=false
 globalThis.fetch=async(url,options={})=>{requests.push({url,options});if(url.endsWith('/submitted'))return response([]);return response(url.includes('/context')?context:evidence(mismatch?{reference:'match',amount:'mismatch',account:'match',status:'match'}:undefined))}
 const p=props();p.onConfirm=value=>confirmations.push(value);const scope=mountProof(Form,p);await tick();assert.match(text(scope.root),/W0123456789abcdefabcd/)
 const bank=nodes(scope.root,n=>n.tag==='input'&&n.props.maxlength==='160')[0];assert.equal(bank.value,'')
 choose(scope,new File(['image'],'receipt.png',{type:'image/png'}));await tick();await button(scope,'识别并核对').props.onClick();await tick()
 assert.equal(bank.value,'BANK-123');assert.equal(confirmations.length,0);const upload=requests.find(r=>r.options.method==='POST');assert.ok(upload.options.body instanceof FormData);assert.equal(upload.options.headers['Content-Type'],undefined);assert.equal(upload.options.headers['X-RPB-Wallet'],'1')
 nodes(scope.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0].props['onUpdate:modelValue'](true);await tick();await nodes(scope.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});assert.equal(confirmations[0].proofId,'proof-one');assert.equal(confirmations[0].receiptReference,'BANK-123')
 mismatch=true;choose(scope,new File(['different'],'other.png',{type:'image/png'}));await tick();assert.equal(bank.value,'');await button(scope,'识别并核对').props.onClick();await tick();assert.match(text(scope.root),/信息不匹配/)
 nodes(scope.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0].props['onUpdate:modelValue'](true);await tick();await nodes(scope.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});assert.equal(confirmations.length,1);scope.unmount()
})

test('late scans cannot attach to a changed source; response-loss retry keeps the upload key',async()=>{
 let workspace,resolveScan;const posted=[]
 const input=ref({store:'A',sourceType:'topup',sourceId:'one',channel:'paynow',amount:'10'})
 globalThis.fetch=async(url,options={})=>{if(url.includes('/context'))return response(context);posted.push(options.body.get('idempotencyKey'));if(posted.length===1)throw Error('lost');return new Promise(resolve=>resolveScan=resolve)}
 const scope=mountProof({setup(){workspace=proofHook.useWalletPayNowProof(input);return()=>h('div')}});await tick();workspace.select(new File(['x'],'receipt.png',{type:'image/png'}));await workspace.scan();assert.match(workspace.error.value,/lost/)
 const pending=workspace.scan();await tick();assert.equal(posted[0],posted[1]);input.value={...input.value,sourceId:'two'};await tick();resolveScan(response(evidence()));await pending;await tick();assert.equal(workspace.proof.value,null);assert.equal(workspace.file.value,null);scope.unmount()
})


test('amount changes and replacement receipts with missing Ref cannot retain an old OCR reference',async()=>{
 let missing=false;const p=props(),confirmed=[];p.onConfirm=value=>confirmed.push(value)
 globalThis.fetch=async url=>response(url.endsWith('/submitted')?[]:url.includes('/context')?{...context,amountCents:p.amountCents}:{...evidence(missing?{reference:'missing',amount:'missing',account:'missing',status:'missing'}:undefined),bankReference:missing?null:'BANK-OLD',extracted:missing?{ocrStatus:'unavailable'}:{}})
 const scope=mountProof(Form,p);await tick();choose(scope,new File(['one'],'one.png',{type:'image/png'}));await tick();await button(scope,'识别并核对').props.onClick();await tick()
 const bank=nodes(scope.root,n=>n.tag==='input'&&n.props.maxlength==='160')[0];assert.equal(bank.value,'BANK-OLD')
 p.amountCents=2000;await tick();assert.equal(bank.value,'');assert.equal(nodes(scope.root,n=>n.tag==='img').length,0)
 missing=true;choose(scope,new File(['two'],'two.png',{type:'image/png'}));await tick();await button(scope,'识别并核对').props.onClick();await tick();assert.equal(bank.value,'');assert.match(text(scope.root),/自动识别未完成，原图已保留/)
 bank.props['onUpdate:modelValue']('MANUAL-BANK');await tick();nodes(scope.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0].props['onUpdate:modelValue'](true);await tick();await nodes(scope.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});assert.equal(confirmed[0].receiptReference,'MANUAL-BANK');scope.unmount()
})


test('context request errors can be retried without reopening or changing the transaction',async()=>{
 let state,calls=0;globalThis.fetch=async()=>{if(++calls===1)throw Error('network unavailable');return response(context)}
 const input=ref({store:'A',sourceType:'topup',sourceId:'one',channel:'paynow',amount:'10'})
 const scope=mountProof({setup(){state=proofHook.useWalletPayNowProof(input);return()=>h('div')}});await tick();assert.equal(state.context.value,null);assert.match(state.error.value,/network unavailable/)
 await state.refresh();await tick();assert.equal(state.context.value.systemReference,context.systemReference);assert.equal(state.error.value,'');scope.unmount()
})


test('customer receipt submits to public evidence endpoint and waits for merchant without posting payment',async()=>{
 const requests=[],submitted=[],p={store:'A',sourceType:'topup',sourceId:'one',channel:'paynow',amount:'10',onSubmitted:()=>submitted.push(true)}
 globalThis.fetch=async(url,options={})=>{requests.push({url,options});return response(url.includes('/context')?context:options.method==='POST'?{...evidence(),status:'waiting_confirmation'}:[])}
 const scope=mountProof(CustomerProof,p);await tick();assert.match(text(scope.root),/仅凭上传不代表到账/)
 choose(scope,new File(['receipt'],'receipt.png',{type:'image/png'}));await tick();await button(scope,'提交付款凭证').props.onClick();await tick()
 const posts=requests.filter(r=>r.options.method==='POST');assert.equal(posts.length,1);assert.match(posts[0].url,/public\/stores\/A\/wallet\/collection-proofs\/topup\/one\/submit$/);assert.equal(posts[0].options.body.get('amount'),'10');assert.equal(posts[0].options.headers['X-RPB-Wallet'],'1');assert.equal(posts[0].options.headers['Content-Type'],undefined);assert.match(text(scope.root),/已提交，待商户确认/);assert.equal(submitted.length,1);scope.unmount()
})

test('customer upload retry retains key and late result cannot attach to another bill',async()=>{
 let state,resolve;const keys=[],input=ref({store:'A',sourceType:'bill',sourceId:'one',channel:'paynow',amount:'10'})
 globalThis.fetch=async(url,options={})=>{if(options.method!=='POST')return response(url.includes('/context')?context:[]);keys.push(options.body.get('idempotencyKey'));if(keys.length===1)throw Error('lost response');return new Promise(r=>resolve=r)}
 const scope=mountProof({setup(){state=customerHook.useCustomerPayNowProof(input,()=>{});return()=>h('div')}});await tick();state.select(new File(['receipt'],'receipt.png',{type:'image/png'}));await state.submit();assert.match(state.error.value,/lost response/);const pending=state.submit();await tick();assert.equal(keys[0],keys[1]);input.value={...input.value,sourceId:'two'};await tick();resolve(response({...evidence(),status:'waiting_confirmation'}));await pending;await tick();assert.equal(state.records.value.length,0);assert.equal(state.file.value,null);scope.unmount()
})

test('merchant can select customer evidence but still must explicitly attest before confirmation',async()=>{
 const confirmations=[],p=props();p.onConfirm=c=>confirmations.push(c)
 globalThis.fetch=async url=>response(url.endsWith('/submitted')?[evidence()]:context)
 const scope=mountProof(Form,p);await tick();await button(scope,'选用此凭证').props.onClick();await tick();assert.equal(confirmations.length,0)
 const bank=nodes(scope.root,n=>n.tag==='input'&&n.props.maxlength==='160')[0];assert.equal(bank.value,'BANK-123');await nodes(scope.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});assert.equal(confirmations.length,0)
 nodes(scope.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0].props['onUpdate:modelValue'](true);await tick();await nodes(scope.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});assert.equal(confirmations[0].proofId,'proof-one');scope.unmount()
})


test('settled or closed bill still exposes own receipt history without context, QR or upload calls',async()=>{
 const requests=[];globalThis.window={setInterval:()=>1,clearInterval:()=>{}};
 const p=reactive({store:'A',bill:{id:'one',version:2,amountCents:1000,walletPaidCents:0,externalPaidCents:1000,closed:false,status:'paid',refundStatus:'none',walletApplied:false},context:{store:{storeName:'Shop',operatingEntityName:'Owner'},program:{channels:['paynow']},paynowConfigured:true,transactionsEnabled:true}})
 globalThis.fetch=async url=>{requests.push(url);return response([{...evidence(),status:'confirmed'}])}
 const scope=mountProof(CustomerBill,p);await tick();assert.match(text(scope.root),/商户已确认收款/);assert.equal(nodes(scope.root,n=>n.tag==='a'&&n.props.href==='/private/image').length,1);assert.equal(nodes(scope.root,n=>n.tag==='input'&&n.props.type==='file').length,0);assert.ok(requests.length);assert.ok(requests.every(url=>!url.includes('/context')&&!url.includes('/paynow')))
 p.bill={...p.bill,closed:true,version:3};await tick();assert.match(text(scope.root),/商户已确认收款/);scope.unmount()
})


test('merchant history shows pending customer receipt without implying collection; refresh exposes new uploads',async()=>{
 globalThis.window={setInterval:()=>1,clearInterval:()=>{}}
 const requests=[];let uploaded=false
 globalThis.fetch=async(url,options={})=>{requests.push({url,options});return response(uploaded?[{...evidence({reference:'match',amount:'mismatch',account:'missing',status:'missing'}),confirmation:{},imageUrl:'/api/v1/stores/A/wallet/collection-proofs/images/proof-one'}]:[])}
 const p=reactive({store:'A',sourceType:'topup',sourceId:'one'}),scope=mountProof(StaffHistory,p);await tick()
 assert.match(text(scope.root),/暂无记录/);uploaded=true;await button(scope,'刷新').props.onClick();await tick()
 assert.match(text(scope.root),/顾客已提交，尚未核实收款/);assert.match(text(scope.root),/202012345A/);assert.match(text(scope.root),/BANK-123/);assert.match(text(scope.root),/信息不匹配/)
 assert.doesNotMatch(text(scope.root),/商户已确认收款/);assert.doesNotMatch(text(scope.root),/实际收款账户/)
 assert.equal(nodes(scope.root,n=>n.tag==='a'&&n.props.href==='/api/v1/stores/A/wallet/collection-proofs/images/proof-one').length,1)
 assert.ok(requests.every(r=>!r.options.method||r.options.method==='GET'));assert.ok(requests.every(r=>r.url==='/api/v1/stores/A/wallet/collection-proofs/topup/one'));scope.unmount()
})
test('merchant history distinguishes confirmed receipt and clears stale images on source changes or denied reads',async()=>{
 globalThis.window={setInterval:()=>1,clearInterval:()=>{}}
 let resolveLate,fail=false
 globalThis.fetch=async url=>{if(url.includes('/two'))return new Promise(r=>resolveLate=r);if(fail)throw Error('没有查看凭证权限');return response([{...evidence(),confirmation:{verified:true,bankReference:'BANK-CONFIRMED',actualAccount:'MERCHANT',note:'checked'}}])}
 const p=reactive({store:'A',sourceType:'bill',sourceId:'one'}),scope=mountProof(StaffHistory,p);await tick();assert.match(text(scope.root),/商户已确认收款/);assert.match(text(scope.root),/BANK-CONFIRMED/)
 p.sourceId='two';await tick();assert.equal(nodes(scope.root,n=>n.tag==='a').length,0)
 p.store='B';p.sourceId='three';fail=true;await tick();resolveLate(response([evidence()]));await tick();assert.equal(nodes(scope.root,n=>n.tag==='a').length,0);assert.match(text(scope.root),/没有查看凭证权限/);scope.unmount()
})


test('merchant pending receipt labels and original-image action are available in English',async()=>{
 globalThis.window={setInterval:()=>1,clearInterval:()=>{}}
 globalThis.fetch=async()=>response([evidence()])
 const scope=mountProof(StaffHistory,{store:'A',sourceType:'topup',sourceId:'one'},'en-SG');await tick()
 assert.match(text(scope.root),/Customer submitted; receipt not yet verified/);assert.doesNotMatch(text(scope.root),/wallet.proof.unconfirmed/)
 assert.equal(nodes(scope.root,n=>n.tag==='a'&&n.props.href==='/private/image').length,1);scope.unmount()
})
