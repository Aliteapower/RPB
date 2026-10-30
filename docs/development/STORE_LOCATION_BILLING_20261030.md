# 门店地址、定位校测与计费一致性开发记录

日期：2026-10-30；工作目录：D:\RPB。用户授权本地实现与验证，不提交、推送或部署。

## 门店来源核查

管理员右上角门店资料、平台租户的经营主体/门店、产品订阅门店项目及现场打卡定位均引用同一 tenant_id / stores.id。公共地址使用 stores.share_address，定位点单独放在 workforce_clock_sites；新增定位点不会创建门店或增加计费店数。

原管理员资料接口的负责人/联系电话属于经营主体，Logo属于租户。本次保留该行为并补充共享范围提示。新定位配置接口仅可修改本店地址和定位点，名称、编号、经营主体只读。

## 模块职责

- StoreClockSettings DTO / Repository / Service / Controller：读取同源身份、地址与站点双版本原子保存；员工管理权限可改定位点，修改公共地址另需 tenant.admin.manage。内容相同的已成功请求可重试，陈旧版本拒绝，审计失败整笔回滚。
- ClockLocationCheck：独立采集当前位置，管理员向只读校测接口提交目标草稿；返回距离、精度、半径、判定原因。更换门店、目标点或卸载组件时丢弃迟到结果。不会覆盖目标点或产生打卡/位置证据。
- 员工距离预览：使用当前会话已冻结的目标或实际选中班次策略，只显示近似距离；真实打卡仍由服务器检查位置和权限。旧员工不会因配置门店地点自动切换为现场模式。
- ProductSubscriptionService / SelectedStoreBillingService / PlatformTenantStructureService：门店生命周期和商业操作先锁定租户行（FOR NO KEY UPDATE），串行确定有效门店集合。普通产品仍按全部活跃门店计价，钱包仍按选定门店计价。
- 商业事件快照与历史查询：新事件 JSON 增加版本1的门店/经营主体身份、金额、期间与状态快照；历史接口按租户与订阅双条件查询，仅平台计费管理员可读。历史组件不以当前门店名称代替过去名称。

## 计费边界

关闭产品功能不等于停账或退款。停用门店排除后续报价、单店续费和当前活跃门店汇总，保留已购项目、原期间及事件；整单续费保留停用店旧项。恢复门店重新计入已购活跃项目，不购买或延长期间。删除门店沿用原取消项目逻辑并刷新汇总。

当前汇总表示当前门店项目集合，不作为历史实收凭据。过去商业事件金额和快照不重写；旧事件未保存名称快照时明确显示不可用。新增快照只对之后产生的事件生效。

## 验证与限制

验证证据位于 target/store-location-billing-20261030；数据库读取 target/local-postgres-current.txt，端口61644，仅使用各测试独立UUID schema，没有升级或修改现有运行时业务schema。

首轮后端36项通过：地址和站点原子保存/重试、跨权限及陈旧版本拒绝、审计回滚、校测距离与精度/时效/越界、无写入副作用；普通及钱包计费停用/恢复/续费/删除；并发开通等待生命周期提交后按有效门店计价；历史名称/金额/期间冻结及跨租户隔离。API认证、写请求来源和历史接口权限通过。

最终后端合并19个测试类108项，失败/错误/跳过均0；其中原WorkforceWorkModePostgresTest的39项打卡/工资回归通过，覆盖历史策略冻结、地址隐私、自由员工、普通员工核准及工资历史。其余覆盖原资料修改、订阅报价/期间、AppGate、计费API、迁移边界及新增路由精确放行。首轮36项与回归72项没有重复计数。

相关前端交互57项通过（包含workmode 26项），另商业历史组件4项通过，共61项；覆盖无副作用校测、未知保存结果原载荷重试、当前门店身份、只读地址权限、停用店禁续费、历史名称/金额/旧记录提示、请求切换与权限错误。npm run build（vue-tsc与Vite）通过。独立只读审查未发现阻塞问题；git diff --check通过，仅有既有LF/CRLF提示。与任务开始时基线逐文件哈希核对，既有workforce后端文件均未修改，新增模块与原服务组合使用。

证据：focused.log、regression.log、build/surefire-reports、frontend-related.log、frontend-history.log、build.log；均在上述target目录。商业历史交互测试命令为 node --loader ./scripts/tests/workforce-loader.mjs --test scripts/tests/platform-subscription-history.test.mjs（直接node --test未加载Vue编译器，不能运行该测试）。

没有新增数据库迁移、依赖或外部地图服务；没有改写历史工资/打卡策略JSON。本次未进行真实手机GPS、微信定位或生产账单验收；设备定位精度与权限仍需现场验收。

## 关联文档

- [实施计划](../superpowers/plans/2026-10-30-store-location-billing.md)
- [接口契约](../api/2026-10-30-store-location-billing.md)
- [发布说明](../release-notes/2026-10-30-store-location-billing.md)
