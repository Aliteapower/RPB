import test from 'node:test'
import assert from 'node:assert/strict'

const state = () => import('../../modules/staff-permissions/permissionSelection.mjs')
const choices = [
  {code:'wallet.view',requires:[],grantable:true},
  {code:'wallet.cards.view',requires:['wallet.view'],grantable:true},
  {code:'wallet.cards.sell',requires:['wallet.cards.view'],grantable:true},
  {code:'wallet.refund.manage',requires:['wallet.view'],grantable:false}
]

test('grant adds transitive viewing dependencies; revoke removes all dependent operations', async () => {
  const {togglePermission} = await state()
  const granted = togglePermission([], 'wallet.cards.sell', true, choices)
  assert.deepEqual(new Set(granted),new Set(['wallet.view','wallet.cards.view','wallet.cards.sell']))
  assert.deepEqual(togglePermission(granted,'wallet.view',false,choices),[])
})

test('non-grantable existing permissions remain revocable but cannot be newly granted',async()=>{
  const {togglePermission}=await state()
  assert.deepEqual(togglePermission([], 'wallet.refund.manage', true, choices),[])
  assert.deepEqual(togglePermission(['wallet.view','wallet.refund.manage'],'wallet.refund.manage',false,choices),['wallet.view'])
})

test('store permissions preserve explicit empty revocation and never accumulate across stores',async()=>{
  const {permissionsForStore}=await state(),base=['wallet.view','reservation.create']
  const maps={A:['wallet.view'],B:[]}
  assert.deepEqual(permissionsForStore(base,maps,'A'),['wallet.view'])
  assert.deepEqual(permissionsForStore(base,maps,'B'),[])
  assert.deepEqual(permissionsForStore(base,maps,'C'),base)
  assert.deepEqual(base,['wallet.view','reservation.create'])
})

test('permission request sends the selected store, version, explicit empty set and command header',async()=>{
  const {build}=await import('esbuild'),{writeFile,mkdir}=await import('node:fs/promises'),{pathToFileURL}=await import('node:url')
  const bundle=await build({entryPoints:['src/modules/staff-permissions/api.ts'],bundle:true,write:false,format:'esm',platform:'node'})
  await mkdir('target',{recursive:true});await writeFile('target/staff-permission-api-test.mjs',bundle.outputFiles[0].contents)
  const api=await import(pathToFileURL(process.cwd()+'/target/staff-permission-api-test.mjs'))
  const calls=[],fetcher=async(url,options)=>{calls.push({url,options});return {ok:true,status:200,json:async()=>({success:true,version:3,permissions:[],groups:[]})}}
  await api.saveStaffPermissions('store B','employee 1',2,[],fetcher)
  assert.equal(calls[0].url,'/api/v1/stores/store%20B/tenant-admin/staff/employee%201/permissions')
  assert.equal(calls[0].options.method,'PUT');assert.equal(calls[0].options.credentials,'include')
  assert.equal(calls[0].options.headers['X-RPB-Staff-Permissions'],'1')
  assert.deepEqual(JSON.parse(calls[0].options.body),{expectedVersion:2,permissions:[]})
})

test('auth store refresh applies each store snapshot, falls back to the original base, and fails closed',async()=>{
  const {build}=await import('esbuild'),{writeFile}=await import('node:fs/promises'),{pathToFileURL}=await import('node:url')
  const bundle=await build({entryPoints:['src/stores/authSession.ts'],bundle:true,write:false,format:'esm',platform:'node',external:['vue','pinia']})
  await writeFile('target/staff-permission-auth-test.mjs',bundle.outputFiles[0].contents)
  const {createPinia,setActivePinia}=await import('pinia');setActivePinia(createPinia())
  const {useAuthSessionStore}=await import(pathToFileURL(process.cwd()+'/target/staff-permission-auth-test.mjs'))
  const originalFetch=globalThis.fetch,originalWindow=globalThis.window
  const raw={accountId:'employee',roles:['store_staff'],permissions:['reservation.create'],storeIds:['A','B','C'],storePermissions:{A:['wallet.view'],B:[]}}
  globalThis.window={setTimeout,clearTimeout};globalThis.fetch=async(url,options)=>{
    assert.equal(options.cache,'no-store')
    return {ok:true,status:200,text:async()=>JSON.stringify({success:true,user:raw})}
  }
  try {
    const auth=useAuthSessionStore()
    await auth.refreshStorePermissions('A');assert.deepEqual([...auth.user.permissions],['wallet.view'])
    await auth.refreshStorePermissions('B');assert.deepEqual([...auth.user.permissions],[])
    await auth.refreshStorePermissions('C');assert.deepEqual([...auth.user.permissions],['reservation.create'])
    raw.storePermissions.A=[]
    await auth.refreshStorePermissions('A');assert.deepEqual([...auth.user.permissions],[])
    globalThis.fetch=async()=>{throw Error('offline')}
    await auth.refreshStorePermissions('A');assert.equal(auth.user,null)
    let completeOld
    globalThis.fetch=()=>new Promise(resolve=>{completeOld=resolve})
    const old=auth.refreshStorePermissions('A')
    globalThis.fetch=async()=>({ok:true,status:200,text:async()=>JSON.stringify({success:true,user:raw})})
    await auth.refreshStorePermissions('B')
    completeOld({ok:true,status:200,text:async()=>JSON.stringify({success:true,user:{...raw,storePermissions:{A:['wallet.view'],B:['wallet.charge.create']}}})})
    await old
    assert.equal(auth.permissionStoreId,'B');assert.deepEqual([...auth.user.permissions],[])
    globalThis.fetch=()=>new Promise(resolve=>{completeOld=resolve})
    const pending=auth.refreshStorePermissions('A')
    auth.clear()
    completeOld({ok:true,status:200,text:async()=>JSON.stringify({success:true,user:raw})})
    await pending
    assert.equal(auth.user,null);assert.equal(auth.loading,false)
  } finally {globalThis.fetch=originalFetch;globalThis.window=originalWindow}
})
