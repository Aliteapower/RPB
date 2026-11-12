// Local-only browser fixture. Fresh schema on this workspace's pointer PostgreSQL; never touches public/runtime schemas.
import { readFileSync, readdirSync, mkdirSync, writeFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { execFileSync } from 'node:child_process'
import { randomUUID, randomBytes, createHash } from 'node:crypto'
const root = process.cwd()
if(existsSync(resolve(root,'target/wine-storage-validation/fixture.json'))) throw new Error('An owned wallet fixture already exists. Reuse it; do not overwrite its schema or session metadata.')
const pointer = Object.fromEntries(readFileSync(resolve(root, 'target/local-postgres-current.txt'), 'utf8').split(/\r?\n/).filter(l => l.includes('=')).map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]))
if (resolve(pointer.runtimeWorktree) !== resolve(root) || !/^\d+$/.test(pointer.port)) throw new Error('Use the owner worktree and a valid pointer')
const schema = 'wallet_browser_' + randomUUID().replaceAll('-', '')
const psql = process.env.RPB_PSQL || 'C:/Program Files/PostgreSQL/17/bin/psql.exe'
function sql(text, scoped = true) {
  return execFileSync(psql, ['-h', '127.0.0.1', '-p', pointer.port, '-U', 'postgres', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-q'], { input: text, encoding: 'utf8', env: { ...process.env, PGCLIENTENCODING: 'UTF8', PGOPTIONS: scoped ? `-c search_path=${schema},public` : '' } })
}
sql(`create schema ${schema};`, false)
for (const file of readdirSync(resolve(root, 'src/main/resources/db/migration')).filter(f => /^V\d+__.*\.sql$/.test(f)).sort()) sql(readFileSync(resolve(root, 'src/main/resources/db/migration', file), 'utf8'))
const fixture = { schema, pgPort: pointer.port, backendPort: 8086, frontendPort: 5186, tenant: randomUUID(), issuer: randomUUID(), stores: [randomUUID(), randomUUID(), randomUUID()], customer: randomUUID(), staff: randomUUID(), platform: randomUUID(), customerAuth: randomUUID(), staffToken: randomBytes(32).toString('base64url'), customerToken: randomBytes(32).toString('base64url'), platformToken: randomBytes(32).toString('base64url') }
const hash = value => createHash('sha256').update(value).digest('hex')
sql(`insert into tenants(id,tenant_code,display_name,status) values('${fixture.tenant}','wallet-${fixture.tenant.slice(0,8)}','钱包隔离验收租户','active');
insert into operating_entities(id,tenant_id,entity_code,display_name,status) values('${fixture.issuer}','${fixture.tenant}','wallet-issuer','统一钱包发行主体','active');
${fixture.stores.map((store, i) => `insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values('${store}','${fixture.tenant}','${fixture.issuer}','wallet-${i}','钱包测试${['A店','B店','到期店'][i]}','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD');`).join('\n')}
insert into customers(id,tenant_id,customer_code,customer_type,display_name,email,status) values('${fixture.customer}','${fixture.tenant}','WALLET-TEST','regular','钱包测试顾客','wallet.test@example.com','active');
insert into customer_auth_accounts(id,tenant_id,customer_id,email,display_name,status,email_verified_at) values('${fixture.customerAuth}','${fixture.tenant}','${fixture.customer}','wallet.test@example.com','钱包测试顾客','active',now());
insert into customer_auth_sessions(tenant_id,auth_account_id,customer_id,session_hash,status,expires_at) values('${fixture.tenant}','${fixture.customerAuth}','${fixture.customer}','${hash(fixture.customerToken)}','active',now()+interval '4 hours');
insert into auth_accounts(id,tenant_id,username,display_name,actor_type,status,password_hash,default_store_id) values('${fixture.staff}','${fixture.tenant}','wallet-test-staff','钱包验收管理员','tenant_admin','active','unusable-test-password','${fixture.stores[0]}'),('${fixture.platform}',null,'wallet-test-platform','钱包验收平台','platform_admin','active','unusable-test-password',null);
insert into auth_account_roles(account_id,role_code) values('${fixture.staff}','tenant_admin'),('${fixture.platform}','platform_admin');
insert into auth_account_permissions(account_id,permission_code) select '${fixture.staff}',p from unnest(array['tenant.admin.manage','customer.lookup','wallet.view','wallet.topup.create','wallet.collection.confirm','wallet.charge.create','wallet.refund.manage','wallet.reconcile.view','wallet.settings.manage']) p;
insert into auth_account_permissions(account_id,permission_code) values('${fixture.platform}','platform.billing.manage'),('${fixture.platform}','platform.tenant.manage');
${fixture.stores.map(store => `insert into auth_account_store_access(account_id,tenant_id,store_id) values('${fixture.staff}','${fixture.tenant}','${store}');`).join('\n')}
insert into auth_user_sessions(account_id,tenant_id,session_hash,status,expires_at) values('${fixture.staff}','${fixture.tenant}','${hash(fixture.staffToken)}','active',now()+interval '4 hours'),('${fixture.platform}',null,'${hash(fixture.platformToken)}','active',now()+interval '4 hours');
update platform_product_line_prices set amount=30,status='active' where app_key='wallet';`)
mkdirSync(resolve(root, 'target/wine-storage-validation'), { recursive: true })
writeFileSync(resolve(root, 'target/wine-storage-validation/fixture.json'), JSON.stringify(fixture, null, 2))
console.log(JSON.stringify({ schema, tenant: fixture.tenant, stores: fixture.stores, backendPort: fixture.backendPort, frontendPort: fixture.frontendPort }))

// Scope-limited storage grants, using explicit store subscription rows.
sql(`insert into auth_account_permissions(account_id,permission_code) select '${fixture.staff}',p from unnest(array['wine_storage.view','wine_storage.receive','wine_storage.deliver','wine_storage.rules.manage','wine_storage.correct','catalog.view','catalog.manage','catalog.publish']) p;
insert into tenant_app_entitlements(tenant_id,app_key,status) values('${fixture.tenant}','wine_storage','enabled');
insert into store_app_settings(tenant_id,store_id,app_key,is_enabled,entry_visible) select '${fixture.tenant}',s,'wine_storage',true,true from unnest(array[${fixture.stores.map(s=>"'"+s+"'::uuid").join(',')}]) s;
insert into tenant_product_subscriptions(tenant_id,app_key,billing_cycle,status) values('${fixture.tenant}','wine_storage','manual','active');
insert into tenant_product_subscription_items(subscription_id,tenant_id,app_key,scope_type,store_id,billing_cycle,status) select id,tenant_id,app_key,'store',s,'manual','active' from tenant_product_subscriptions cross join unnest(array[${fixture.stores.map(s=>"'"+s+"'::uuid").join(',')}]) s where tenant_id='${fixture.tenant}' and app_key='wine_storage';`)
