# 租户管理员按门店管理产品线开关

2026-10-24，新加坡时区；工作区 D:\RPB。初轮本地实现后，用户已明确授权“提交部署”。功能提交137096e1已推送并发布，完整证据见 docs/development/STORE_PRODUCT_SWITCHES_PRODUCTION_RELEASE_20261024.md。

## 行为

租户管理员选择门店后，在“产品线管理”中关闭或重新开启钱包积分、每日营业记录。该入口独立于产品使用状态，关闭后仍能进入管理页面。

关闭隐藏员工及管理员产品入口，既有AppGate阻止员工和顾客的产品业务访问。数据保留，重新开启后继续使用；不取消订阅、不停止计费，也不改变其他门店。已到期、暂停或未开通的产品不能通过此开关重新授予使用资格。

订阅同步保留这两个产品明确关闭的门店状态；首次购买仍默认开启，其他产品同步行为保持。新页面提供中英文、保存反馈、失败重试及门店切换时的过期响应保护。

## 模块、API与数据库审查

- AppGate负责商业资格、运行判定及既有开关审计；tenantadmin控制器只解析管理员范围并调用应用服务，不直接访问Repository。
- 管理API复用tenant_admin角色、tenant.admin.manage权限及账号门店范围；管理请求不依赖被关闭产品的AppGate，产品业务请求继续受AppGate控制。
- PUT为明确目标状态，重复请求不重复审计。同门店通过stores行锁串行写入，状态与审计同事务。
- 只修改store_app_settings既有状态；使用复合范围与唯一键，没有迁移、依赖、环境配置、资金、积分、营业记录或订阅数据结构变化。
- API契约见 docs/api/2026-10-24-store-product-switches.md。包含缺失／非法请求400、无权限403、无效门店404、不可开启409、数据库错误500及写请求同源保护。

## 验证记录

后端初轮51项定向测试通过，包含AppGate命令／权限／商业门店有效期、开关服务、控制器、订阅同步及隔离PostgreSQL。随后扩展两产品的真实JPA持久化和订阅同步用例，并补充请求校验的统一错误码，最终18项受影响测试通过、失败0、错误0；其余33项AppGate命令／权限／订阅有效期实现未再变化，复用初轮通过结果。最终证据见 target/store-products-final-tests.log。

PostgreSQL严格使用本工作区target/local-postgres-current.txt指向的127.0.0.1:61644/postgres，测试使用随机隔离schema并在结束后清理。先复现订阅同步会重新开启手动关闭门店，再修复SQL条件。钱包数据库夹具使用现有SelectedStoreBillingService按门店购买；不修改产品购买规则。

15项前端测试通过，覆盖编译后的实际页面组件中英文渲染、开关状态／保存、到期提示、错误、重复点击、切店竞态及现有入口链接／二维码上下文回归。初次运行旧前端用例时Node未启用TypeScript加载，改用现有Node22的--experimental-strip-types后通过，没有修改依赖或旧用例。

vue-tsc与Vite生产构建通过，git diff --check通过。浏览器控制通道两次超时，未取得真实浏览器手机／桌面布局截图，未声称完成视觉验收；组件运行交互与构建结果不替代该检查。没有全量后端测试或生产验证声明。

证据：target/store-product-sync-red.log、store-products-backend-tests.log、store-products-final-tests.log、store-products-frontend-final.log、store-products-build.log。

## 剩余风险与回退

已打开的员工页面需刷新才能同步移除入口，后端访问判定使用最新状态。发布前还需真实浏览器检查移动和桌面中英文布局。

未来若发布后需回退，可恢复旧后端和前端制品，无数据库结构回退。门店开关数据保留，不恢复数据库覆盖业务数据；旧版订阅同步可能重新开启已关闭门店，应在再次恢复旧版前知悉此兼容限制。
