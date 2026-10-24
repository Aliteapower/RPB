# 钱包设置按功能拆分：验收与发布说明

2026-10-24，D:\RPB；本轮完成开发和测试，不提交、不推送、不部署。线上设置页仅只读检查，未更改线上配置。

## Changed

原settings长页面改为设置目录，只显示用途、当前状态及入口：

- 充值收款方式：`/stores/{storeId}/wallet/settings/channels`，独立编辑现金／PayNow／信用卡，保留同钱包计划下门店共用的影响提示。
- PayNow收款账户：`/stores/{storeId}/wallet/settings/paynow`，复用原收款资料组件、账户归属核实与保存接口。
- 门店与经营主体资料：沿用`/stores/{storeId}/admin/profile`，不重复实现平台资料维护。

原`/wallet/settings`地址保留为目录；两个子页支持直接访问、刷新，顶部返回及底部文字链接均回设置目录，目录返回更多。子页保存后刷新当前上下文并显示成功提示，目录随保存结果展示启用的充值方式及PayNow是否配置。

中英文文案独立维护。目录不挂载表单、不请求PayNow收款资料；员工无wallet.settings.manage时不挂载子页表单或请求收款账户。原平台资料入口权限仍交由平台页面执行。

## 模块与审查

WalletSettingsPanel仅负责设置目录；WalletChannelSettings负责充值渠道表单；WalletStaffSettings仅编排功能页、权限和保存提示；WalletPayNowSettings业务逻辑未改。WalletStaffManagement移除设置职责，保留对账、退款及异常处理。

已有工作区的WalletSettingsPanel仅有换行状态，本轮在其上做预期功能修改；其他10个原有组件改动、两份未跟踪验收材料、本线程前轮复验材料及私密文件保留。未读取私密文件，未修改其他产品线。

代码审查结论：APPROVE。未发现未解决的P0／P1问题。路由名称唯一；继承原员工工作区、门店作用域和后端权限；保存分别调用原program及collection-profile接口，版本与幂等规则保留。目录和子页职责清晰，没有后台领域边界改动。

## TDD及自动化结果

- 新路由测试先失败：5项原测试通过，缺settings/channels导致1项失败，证据target/wallet-settings-red.log。
- 新增运行时组件验证：目录无表单及无PayNow读取、独立目的地址、子页权限、仅更新当前门店program及版本、PayNow核实必选、仅保存收款资料、渠道空选／不可用／无经营主体阻止提交、冲突提示。
- 末次定向测试：69项通过，0失败，target/wallet-settings-targeted-final.log。
- 干净目录完整钱包前端回归：136项通过，0失败、0跳过，target/wallet-settings-clean-regression.log。
- Vue类型检查及Vite生产构建通过，target/wallet-settings-build.log。
- git diff --check通过。

干净验证目录由HEAD归档加本轮明确改动和新文件构成，node_modules只读复用原依赖；没有复制其他未跟踪材料。完整命令为node --experimental-transform-types --test --test-timeout=90000 --test-concurrency=1，加该目录wallet-*.test.mjs全清单。

## Troubleshooting

原工作目录并行测试先出现Vite WebSocket 24678端口冲突及模块加载超时；串行仍卡在原wallet-cash-topup的Vite加载。先读日志及原测试，不修改产品代码或未涉改动的现金充值测试。在干净目录中，相同现金测试3项在约1.7秒通过，完整136项约9.7秒全部通过。说明问题与原工作目录的测试运行环境有关；具体Vite内部卡点未完全定位，不把原目录运行称为通过。

仅终止已核实属于本轮的并行／串行／诊断测试进程，没有关闭其他项目或原开发服务。

## 隔离浏览器与真实数据库

遵循target/local-postgres-current.txt，数据库127.0.0.1:61644/postgres、postgres空密码。复用本线程持有的wallet_transactions_0983618362a147a0b77cb19477406228隔离schema，独立8091后端和5191前端代理。后台使用已发布jar，前端使用本轮构建，既有8081服务保留。

1. 从收银台更多进入设置，目录只有三个分类入口、当前充值方式、PayNow未配置及经营主体，不堆叠保存表单。
2. 进入充值方式页，取消信用卡并保存，显示设置已保存；直接刷新子页后信用卡仍未选中。随后恢复原来的三种渠道并保存，目录回显正确。
3. 进入PayNow页，只有原收款账户表单，无充值方式表单。设置测试手机号及名称并启用时，不勾选归属核实则保存按钮不可用；勾选后成功保存，成功提示及刷新回显正确。
4. PayNow每次保存／刷新后归属复选框清空，启用状态下再次保存仍须核实。测试后恢复原停用状态并清空测试账户资料，目录显示未配置。
5. 实测375 CSS像素窄屏下，中英文目录和PayNow表单没有横向溢出；保存目录及子页截图。浏览器视口设置考虑现有缩放，以实际innerWidth及scrollWidth为证。
6. 原资金及积分核对前后相同：6900分币、2积分，无绑定已完成订单4笔、相关消费账单2笔及积分流水2笔。测试仅变更隔离配置及测试会话，未发起真实充值、扣款或积分操作。

证据目录target/wallet-settings-e2e-20261024：settings-desktop.png、settings-mobile-375.png、settings-mobile-en.png、settings-final.png、paynow-mobile.png、paynow-mobile-en.png、balances-before.json、balances-after.json及进程／服务日志。真实手机设备未验证。

## Migration / Permission / API

没有数据库迁移，没有新增或调整API合约、环境变量、依赖及AppGate权限。原渠道版本控制、幂等、经营主体共享规则和PayNow共用资料归属确认保持。新增仅为前端可书签页面，原settings地址仍可用；订位、排队、入座及清洁等其他产品线未改。

## Risk / Rollback

原工作目录Vite测试加载问题仍待单独调查，完整测试已在干净目录通过；本轮未验证真实手机硬件。应用尚未发布，线上界面保持原状。

回退仅恢复本轮前端路由、编排、目录、表单组件及文案，不恢复数据库、不回写资金或积分。隔离测试配置按原渠道及停用状态恢复，版本号递增属正常保存结果。

收尾已核实命令行后关闭本轮8091／5191进程，恢复浏览器视口并关闭临时验收页。原8081后端PID302868及61644数据库PID310268保留，隔离数据及证据保留。未产生提交或部署。
