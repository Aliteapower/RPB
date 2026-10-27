import {test} from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {build} from 'esbuild'
const built=await build({stdin:{contents:"export * from './src/modules/customers/accessNavigation.ts'",resolveDir:process.cwd()},bundle:true,write:false,platform:'node',format:'esm'})
const {customerAccessTarget,customerAccessReturnTarget}=await import('data:text/javascript;base64,'+Buffer.from(built.outputFiles[0].contents).toString('base64'))
test('all product account entries and their return paths remain in the selected store',()=>{
 for(const source of ['wallet','wallet-settings','cards','storage','booking','admin','staff'])for(const kind of ['activation','email']){
  const url=new URL(customerAccessTarget('A/B',source,kind),'https://test.local');assert.ok(url.pathname.startsWith('/stores/A%2FB/admin/'));assert.equal(url.searchParams.get('accessSource'),source);assert.ok(customerAccessReturnTarget('A/B',source).startsWith('/stores/A%2FB/'))
 }
 for(const source of ['https://evil.test','//evil.test',undefined,['wallet']])assert.equal(customerAccessReturnTarget('A',source),'/stores/A/staff')
})
test('staff product surfaces expose one shared access component and the email route requires tenant admin',()=>{
 for(const file of ['src/modules/wine-storage/merchant/MerchantStorageSettingsPage.vue','src/modules/wallet/pages/WalletStaffMore.vue','src/modules/wallet/components/WalletSettingsPanel.vue','src/modules/cards/CardsStaffPage.vue','src/pages/StoreStaffHomePage.vue','src/components/tenant-admin/TenantAdminNav.vue'])assert.match(readFileSync(file,'utf8'),/<CustomerAccessLinks/)
 assert.match(readFileSync('src/router/index.ts','utf8'),/path:'\/stores\/:storeId\/admin\/customer-email'.*requiresTenantAdmin:true/)
})
