import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { build } from 'esbuild'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { pathToFileURL } from 'node:url'

let router, buildTenantEntries, productEntryDefinitions, publicEntryOrigin, publicBookingUrlForTenant
const location = hostname => ({ hostname, protocol: 'https:', port: '', origin: `https://${hostname}` })
before(async () => {
  const result = await build({
    stdin: { contents: "export {router} from './src/router/index'; export * from './src/modules/tenant-entry-links/catalog'; export * from './src/utils/hostContext'", resolveDir: process.cwd(), loader: 'ts' },
    bundle: true, write: false, format: 'esm', platform: 'node', external: ['vue', 'vue-router'], define: { 'import.meta.env': '{}' },
    plugins: [{ name: 'memory-router', setup(b) {
      b.onLoad({ filter: /router[/\\]index\.ts$/ }, async ({path}) => ({ contents: (await readFile(path,'utf8')).replaceAll('createWebHistory', 'createMemoryHistory'), loader: 'ts' }))
      b.onLoad({ filter: /authSession\.ts$/ }, () => ({ contents: 'export function useAuthSessionStore() { throw new Error("Route resolution must not authenticate") }', loader: 'ts' }))
      b.onLoad({ filter: /\.vue$/ }, () => ({ contents: 'export default {render(){return null}}', loader: 'js' }))
    } }]
  })
  await mkdir('target/tenant-entry-routes', { recursive: true })
  await writeFile('target/tenant-entry-routes/routes.mjs', result.outputFiles[0].contents)
  ;({router, buildTenantEntries, productEntryDefinitions, publicEntryOrigin, publicBookingUrlForTenant} = await import(pathToFileURL(process.cwd()+'/target/tenant-entry-routes/routes.mjs').href))
  globalThis.window = { __RPB_HOST_PREFIX_BASE_HOST__: 'booking.yumstone.sg' }
})
after(() => { delete globalThis.window })

test('every distributed current product route resolves to a real store-scoped page with the right public boundary', async () => {
  const manifest = JSON.parse(await readFile('src/main/resources/product-line-entries.json', 'utf8'))
  const context = { identity: { tenantCode:'mw', tenantStatus:'active', storeId:'store-a', storeStatus:'active' }, publicBookingEnabled:true, products:manifest.filter(p=>p.appKey).map(p=>({appKey:p.appKey,available:true})) }
  const entries = buildTenantEntries(context, location('platform.booking.yumstone.sg'))
  assert.equal(new Set(entries.map(e=>e.key)).size, entries.length)
  for (const entry of entries) {
    const url = new URL(entry.url), target = entry.audience==='customer' ? url.pathname : url.searchParams.get('redirect')
    assert.equal(url.origin, 'https://booking.yumstone.sg')
    const resolved = router.resolve(target)
    assert.ok(resolved.matched.length > 0, `${entry.key}: ${target}`)
    assert.equal(resolved.params.storeId, 'store-a', entry.key)
    if (entry.audience==='customer') assert.equal(resolved.meta.public, true, entry.key)
    else {
      assert.equal(url.pathname,'/login')
      assert.equal(url.searchParams.get('tenantCode'),'mw')
      assert.equal(url.searchParams.get('entry'),entry.audience==='admin'?'tenant_admin':'staff')
    }
  }
})

test('future products inherit staff routes and can declare customer/admin routes in the same manifest', () => {
  const product = { appKey:'future_product', value:'/stores/:storeId/future', labelKey:'future.title', descriptionKey:'future.description' }
  assert.deepEqual(productEntryDefinitions([product]).map(e=>[e.key,e.path,e.login]), [['future_product',product.value,'staff']])
  const declared = productEntryDefinitions([{ ...product, distributionEntries:[{key:'futureCustomer', audience:'customer', path:'/wallet/:storeId/future'},{key:'futureAdmin', audience:'admin', path:'/stores/:storeId/future/settings'}] }])
  assert.equal(declared[0].login,undefined);assert.equal(declared[1].login,'tenant_admin')
  for (const path of ['https://evil.test','//evil.test','/stores/:storeId/../other','/stores/:storeId/future?redirect=https://evil.test']) {
    assert.throws(()=>productEntryDefinitions([{...product,distributionEntries:[{key:'bad',audience:'staff',path}]}]))
  }
})

test('sharing origins are configured, secure and independent of tenant certificate provisioning', () => {
  for (const host of ['booking.yumstone.sg','platform.booking.yumstone.sg','mw.booking.yumstone.sg']) {
    assert.equal(publicEntryOrigin(location(host)), 'https://booking.yumstone.sg')
    assert.equal(publicBookingUrlForTenant('mw','store-a',location(host)), 'https://booking.yumstone.sg/book/store-a')
  }
  assert.equal(publicEntryOrigin({...location('mw.booking.yumstone.sg'),protocol:'http:',port:'8443'}),'https://booking.yumstone.sg:8443')
  assert.throws(()=>publicEntryOrigin(location('evil.test')))
  assert.throws(()=>publicEntryOrigin(location('mw.booking.yumstone.sg.evil.test')))
  assert.throws(()=>publicEntryOrigin({...location('booking.yumstone.sg'),protocol:'javascript:'}))
  assert.equal(publicEntryOrigin({hostname:'127.0.0.1',protocol:'http:',port:'5178',origin:'http://127.0.0.1:5178'}),'http://127.0.0.1:5178')
  window.__RPB_HOST_PREFIX_BASE_HOST__=''
  assert.equal(publicEntryOrigin(location('custom.example.org')), 'https://custom.example.org')
  window.__RPB_HOST_PREFIX_BASE_HOST__='booking.yumstone.sg'
})
