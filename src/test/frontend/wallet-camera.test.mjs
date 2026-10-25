import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createCameraSession as createWalletCamera } from '../../modules/shared-commerce/scanning/cameraSession.ts'
import { normalizeWalletQr } from '../../modules/wallet/scanning/walletQr.ts'

const code='WP.'+'A'.repeat(43),bill='WB.'+'B'.repeat(43),origin='https://test.example'
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return{promise,resolve,reject}}
function stream(torch=false){const track={stopped:0,listeners:new Map(),constraints:[],stop(){this.stopped++},getSettings:()=>({facingMode:'environment'}),getCapabilities:()=>({torch}),applyConstraints:async function(c){this.constraints.push(c)},addEventListener(k,fn){this.listeners.set(k,fn)},removeEventListener(k){this.listeners.delete(k)}};return{track,getTracks:()=>[track],getVideoTracks:()=>[track]}}
function harness(getUserMedia,overrides={}){const frames=new Map(),states=[],codes=[],video={srcObject:null,muted:false,playsInline:false,readyState:2,videoWidth:1280,videoHeight:720,play:async()=>{assert.equal(video.muted,true);assert.equal(video.playsInline,true)},pause(){}};let id=0,read=()=>code
 const camera=createWalletCamera({getVideo:()=>video,mediaDevices:{getUserMedia},secure:true,requestFrame:fn=>{frames.set(++id,fn);return id},cancelFrame:n=>frames.delete(n),decode:(v,full)=>read(v,full),accept:value=>!!normalizeWalletQr(value,'customer',origin),onCode:c=>codes.push(c),onChange:s=>states.push(s),...overrides})
 return{camera,frames,states,codes,video,setRead:fn=>{read=fn},frame(time=200){const [n,fn]=frames.entries().next().value??[];if(fn){frames.delete(n);fn(time)}},get state(){return states.at(-1)}}
}

test('camera prefers the rear lens, plays inline and emits only once before releasing every track',async()=>{
 const s=stream();let constraints;const h=harness(async c=>{constraints=c;return s});await h.camera.start()
 assert.equal(constraints.audio,false);assert.equal(constraints.video.facingMode.ideal,'environment');assert.equal(h.state.phase,'scanning')
 h.frame();h.frame(400);assert.deepEqual(h.codes,[code]);assert.equal(s.track.stopped,1);assert.equal(h.frames.size,0);assert.equal(h.video.srcObject,null);assert.equal(h.state.phase,'recognized')
})
test('cancelling pending permission releases a late stream without attaching or emitting',async()=>{
 const d=deferred(),s=stream(),h=harness(()=>d.promise);const opening=h.camera.start();h.camera.stop();d.resolve(s);await opening
 assert.equal(s.track.stopped,1);assert.equal(h.video.srcObject,null);assert.equal(h.frames.size,0);assert.deepEqual(h.codes,[])
})
test('an old failed request cannot stop a newer successful camera session',async()=>{
 const d=deferred(),s=stream();let calls=0;const h=harness(()=>++calls===1?d.promise:Promise.resolve(s));const old=h.camera.start();h.camera.stop();await h.camera.start();d.reject({name:'NotAllowedError'});await old
 assert.equal(h.state.phase,'scanning');assert.equal(s.track.stopped,0);h.camera.stop();assert.equal(s.track.stopped,1)
})
test('a late video.play rejection cannot detach or stop the replacement camera',async()=>{
 const a=stream(),b=stream(),p=deferred();let calls=0,plays=0;const h=harness(async()=>++calls===1?a:b);h.video.play=()=>++plays===1?p.promise:Promise.resolve()
 const first=h.camera.start();await Promise.resolve();await Promise.resolve();h.camera.stop();await h.camera.start();p.reject({name:'NotAllowedError'});await first
 assert.equal(h.state.phase,'scanning');assert.equal(h.video.srcObject,b);assert.equal(b.track.stopped,0);h.camera.stop()
})
test('double start while permission is pending asks only once and denial never triggers a second request',async()=>{
 const d=deferred();let calls=0;const h=harness(()=>{calls++;return d.promise});const a=h.camera.start();await h.camera.start();d.reject({name:'NotAllowedError'});await a
 assert.equal(calls,1);assert.equal(h.state.issue,'denied');assert.equal(h.state.phase,'error')
})
test('only unsupported constraints retry with simpler video requirements',async()=>{
 const s=stream(),requests=[],h=harness(async c=>{requests.push(c);if(requests.length===1)throw{name:'OverconstrainedError'};return s});await h.camera.start()
 assert.equal(requests.length,2);assert.equal(requests[1].audio,false);assert.equal(h.state.phase,'scanning');h.camera.stop()
 for(const [name,issue] of [['NotFoundError','missing'],['NotReadableError','busy']]){const fail=harness(async()=>{throw{name}});await fail.camera.start();assert.equal(fail.state.issue,issue)}
})
test('playback restriction has a retry prompt and releases the granted stream',async()=>{
 const s=stream(),h=harness(async()=>s);h.video.play=async()=>{throw{name:'NotAllowedError'}};await h.camera.start();assert.equal(h.state.issue,'playback');assert.equal(s.track.stopped,1)
})
test('non-wallet codes keep the camera scanning and a decode error releases it safely',async()=>{
 const s=stream(),h=harness(async()=>s);h.setRead(()=> 'https://unrelated.example');await h.camera.start();h.frame();assert.equal(h.state.phase,'scanning');assert.equal(h.state.issue,'wrongCode');assert.deepEqual(h.codes,[])
 h.setRead(()=>{throw Error('canvas unavailable')});h.frame(500);assert.equal(h.state.issue,'decode');assert.equal(s.track.stopped,1);assert.equal(h.frames.size,0)
})
test('switching lenses releases the first stream, torch is capability gated and pause stops capture',async()=>{
 const a=stream(true),b=stream();let count=0;const h=harness(async constraints=>{if(++count===2){assert.equal(a.track.stopped,1);assert.equal(constraints.video.facingMode.ideal,'user')}return count===1?a:b});await h.camera.start();await h.camera.toggleTorch();assert.equal(h.state.torch,true);assert.equal(a.track.constraints[0].advanced[0].torch,true)
 await h.camera.switchFacing();assert.equal(h.state.torchAvailable,false);h.camera.pause();assert.equal(b.track.stopped,1);assert.equal(h.state.phase,'paused');assert.equal(h.frames.size,0)
})
test('device interruption releases the stream and tells the user to reopen',async()=>{
 const s=stream(),h=harness(async()=>s);await h.camera.start();s.track.listeners.get('ended')();assert.equal(h.state.issue,'interrupted');assert.equal(s.track.stopped,1);assert.equal(s.track.listeners.size,0)
})
test('QR type and origin are checked locally before server identity checks',()=>{
 assert.equal(normalizeWalletQr(' '+code+' ','customer',origin),code);assert.equal(normalizeWalletQr(bill,'customer',origin),null)
 assert.equal(normalizeWalletQr(code,'bill',origin),null);assert.equal(normalizeWalletQr(bill,'bill',origin),bill)
 const link=origin+'/wallet/20000000-0000-0000-0000-000000000983#bill='+bill
 assert.equal(normalizeWalletQr(link,'bill',origin),link);assert.equal(normalizeWalletQr(link.replace(origin,'https://foreign.example'),'bill',origin),null)
 assert.equal(normalizeWalletQr('not a code','bill',origin),null)
})
test('insecure and unsupported browsers explain the limitation without requesting any device',async()=>{
 let calls=0;const acquire=async()=>{calls++;return stream()}
 const insecure=harness(acquire,{secure:false});await insecure.camera.start();assert.equal(insecure.state.issue,'insecure')
 const unsupported=harness(acquire,{mediaDevices:undefined});await unsupported.camera.start();assert.equal(unsupported.state.issue,'unsupported');assert.equal(calls,0)
})
test('disposing a pending camera prevents late attachment and any later start',async()=>{
 const d=deferred(),s=stream();let calls=0;const h=harness(()=>{calls++;return d.promise});const opening=h.camera.start();h.camera.dispose();d.resolve(s);await opening;await h.camera.start()
 assert.equal(s.track.stopped,1);assert.equal(calls,1);assert.equal(h.frames.size,0);assert.deepEqual(h.codes,[])
})


test('personal QR accepts identity and direct personal payment, while legacy debit stays separate',()=>{
 const identity='WI.'+'A'.repeat(43);assert.equal(normalizeWalletQr(identity,'identity',origin),identity);assert.equal(normalizeWalletQr(code,'identity',origin),code);assert.equal(normalizeWalletQr('WC.'+'A'.repeat(43),'identity',origin),null);assert.equal(normalizeWalletQr(identity,'customer',origin),identity);assert.equal(normalizeWalletQr(identity,'personal',origin),identity);assert.equal(normalizeWalletQr('WC.'+'A'.repeat(43),'personal',origin),null);assert.equal(normalizeWalletQr(identity,'bill',origin),null)
})


test('personal payment scanner states immediate collection while identity keeps read-only guidance',async()=>{
 const {readFile}=await import('node:fs/promises')
 const source=await readFile(new URL('../../modules/wallet/components/WalletQrScanner.vue',import.meta.url),'utf8')
 assert.match(source,/purpose==='personal'\?'wallet.personal.scanPrivacy':'wallet.scanner.privacy'/)
 assert.equal((source.match(/purpose==='identity'\|\|purpose==='personal'\?'wallet.marketing.pastePersonal':'wallet.manualCode'/g)||[]).length,2)
 const locales=await readFile(new URL('../../modules/wallet/personalLocales.ts',import.meta.url),'utf8')
 assert.match(locales,/扫码后按已填写金额收款，超限由顾客确认/)
 assert.doesNotMatch(locales,/扫码不会直接扣款/)
})
