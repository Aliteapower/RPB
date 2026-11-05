import {test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
const result=await build({entryPoints:['src/frontend-version/versionProbe.ts'],bundle:true,write:false,format:'esm',platform:'node'})
const {createVersionProbe}=await import('data:text/javascript;base64,'+Buffer.from(result.outputFiles[0].contents).toString('base64'))
const a='a'.repeat(40),b='b'.repeat(40),response=v=>({ok:true,headers:new Headers({'Content-Type':'application/json'}),json:async()=>({version:v})})
test('reports changed releases and rollback, without navigation or business requests',async()=>{
 const changes=[],calls=[];let version=a
 const probe=createVersionProbe(a,v=>changes.push(v),async(...args)=>{calls.push(args);return response(version)})
 await probe.check();assert.deepEqual(changes,['']);version=b;await probe.check();await probe.check();assert.deepEqual(changes,['',b]);version=a;await probe.check();assert.deepEqual(changes,['',b,''])
 for(const [url,options] of calls){assert.equal(url,'/frontend-version.json');assert.equal(options.cache,'no-store');assert.equal(options.credentials,'omit');assert.equal(options.redirect,'error');assert.equal(options.method,undefined)}probe.stop()
})
test('network errors missing manifests HTML and invalid versions never fabricate an update',async()=>{
 for(const read of [()=>Promise.reject(new Error('offline')),async()=>({ok:false}),async()=>({ok:true,headers:new Headers({'Content-Type':'text/html'}),json:async()=>({version:b})}),async()=>response('bad'),async()=>({ok:true,headers:new Headers({'Content-Type':'application/json'}),json:async()=>{throw new Error('bad json')}})]){
  const changes=[],p=createVersionProbe(a,v=>changes.push(v),read);await p.check();assert.deepEqual(changes,[]);p.stop()
 }
})
test('deduplicates concurrent checks and ignores late responses after disposal',async()=>{
 let finish,calls=0;const changes=[],p=createVersionProbe(a,v=>changes.push(v),()=>{calls++;return new Promise(r=>finish=r)})
 const first=p.check(),second=p.check();assert.equal(calls,1);p.stop();finish(response(b));await Promise.all([first,second]);assert.deepEqual(changes,[]);await p.check();assert.equal(calls,1)
})
test('timeouts release the request guard so the next check can recover',async t=>{
 t.mock.timers.enable({apis:['setTimeout']});let calls=0
 const changes=[],p=createVersionProbe(a,v=>changes.push(v),async(_url,options)=>{calls++;if(calls===2)return response(b);return new Promise((_,reject)=>options.signal.addEventListener('abort',()=>reject(new Error('abort'))))})
 const work=p.check();t.mock.timers.tick(5001);await work;await p.check();assert.deepEqual(changes,[b]);p.stop()
})
