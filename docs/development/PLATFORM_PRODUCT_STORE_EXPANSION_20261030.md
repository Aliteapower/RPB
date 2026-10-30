# 平台产品线按门店开通与追加修复

范围：所有产品线复用现有SelectedStoreBillingService及门店主数据，不新增计费表或服务。已有订阅默认单店续费，另提供“开通新增门店”；首次购买选择目标门店。未选择门店、停用或删除门店、钱包缺经营主体、无有效价格均不能提交。旧legacy_grant仍按原转换入口处理，不扩大转换语义。

API契约：现有purchase/renew/items/{id}/renew在显式storeIds时使用按门店服务；原非钱包不传storeIds仍兼容原路径。add-stores和quote扩展至所有有效产品线。平台管理员及platform.billing.manage（或原租户管理权限）不变。tenant/subscription/app必须匹配；storeIds必须属于目标租户的活跃未删除门店，追加不能含已有条目。保留钱包SGD与经营主体校验。追加自操作时间开始独立期间，不修改旧条目及旧事件快照。显式选店请求沿用持久化幂等与版本检查；不重复计费。

测试清单：多产品首次选店购买→追加另一门店→旧条目及历史不变→追加幂等回放；跨租户与错误产品、重复门店、停用/删除门店、过期版本、钱包限制；页面切换产品清空选店、追加和续费分离、报价只含所选门店、加载错误与保存保护。仅本地实现和验证，本轮未获新功能生产部署授权。

## 根因及模块边界

PlatformTenantBillingPage原本仅在wallet产品显示选店与追加入口；Controller按wallet路由SelectedStoreBillingService，该服务又硬编码wallet。其他产品一旦有订阅，页面只展示已购条目，新创建门店自然不在列表中。修复将选择与追加能力推广到既有模块中的所有有效产品；门店仍来自listTenantStores，与租户结构的集团门店是同一数据源，不新建计费门店档案。

门店候选过滤提取为storeBillingEligibility.selectableBillingStores，页面负责操作模式，应用服务负责租户/产品/版本/门店校验及独立期间，持久化层继续承担锁、幂等和快照。未增加跨模块依赖、数据库表或新endpoint。项目未提供architecture-review技能文件，本轮未改变模块边界。

## 验证记录

证据目录target/billing-store-expansion-20261030。按target/local-postgres-current.txt使用61644端口的既有postgres数据库，在随机生成的store_billing_test_*隔离schema运行PostgreSQL集成测试；测试自身创建并清理schema，不重置原业务schema。未使用生产数据库。

红灯：新增两项按门店集成测试在原服务REQUEST_INVALID，页面回归原4项中3项失败、权限错误态1项通过。修复后的最终结果另见验证汇总。

测试中发现旧静态UI检查禁止页面出现任何checkbox，与已存在的钱包选店及本轮多门店选择不符。将其改为检查selectedStoreIds及追加入口；原产品开关禁止项保持。另修正测试对“停用未购买门店会增加已有订阅版本”的错误假设，实际订阅金额未变时版本也不变。

## 审查

按API review、TDD review、code-review、release-note核对。所有写路径仍经平台权限检查，显式选店请求使用持久化幂等，追加包含已有条目、错误产品、外租户门店、停用/删除门店、无价格及过期版本不能落账。通过租户计费锁串行化与门店生命周期操作；并发相同版本追加只成功一次。真实授权同步测试核对新店开通、未购店不开通，以及旧店已手动关闭的产品不被重新开启。

草稿或合成页面不计为生产验收。既有生产模板入口版本及其他未提交钱包改动均保留。本轮未提交、推送、部署，也没有替20000000实际购买任何门店产品。

## 最终验证汇总

- 后端42项通过：PostgreSQL集成13项（包括六类产品、真实授权同步、并发与跨租户），原服务、报价、模块边界、AppGate、UI契约和Controller权限29项。命令及日志：backend-verified.log，最后仅修正外租户测试夹具必填时区等字段后重跑受影响13项，postgres-final.log最终0失败0错误；其他29项业务代码及配置未改变，沿用同轮结果。
- 前端15项相关回归通过：完整计费页面组件的多产品追加/首次购买/单店续费/切换清空/无价格/权限错误/已有取消条目过滤，以及原钱包重试与商业历史回归，frontend-verified.log。
- Vue类型检查通过；Vite对当前源码独立构建通过，产物source-dist及source-build.log。最初npm转发--outDir的命令参数被错误解释为构建根目录，已用Vite CLI显式outDir重新构建源码，不以那次产物作为本轮验证或发布包。
- 差异检查使用core.whitespace=cr-at-eol匹配项目既有换行，通过；35个既有无关文件SHA256均未改变。
- Code Review：已审范围无阻断项，APPROVE。无生产变更；待后续单独授权提交部署后，再用20000000做线上页面及商业记录验收。

## 后续生产状态

已按用户授权部署功能提交1e358d15，20000000牛车水店真实开通与单店续费、原店期限及历史快照不变验证通过。见[生产发布记录](PLATFORM_PRODUCT_STORE_EXPANSION_PRODUCTION_20261030.md)。上文本地状态保留为实施阶段证据。
