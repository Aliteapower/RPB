# 统一扫码设备兼容实现与验证

日期：2026-11-04。项目：D:/RPB。方案：[开发方案](../superpowers/plans/2026-11-04-scan-device-compatibility.md)。本次仅本地开发，未提交、推送或生产部署。

## 已实现

- 默认保持相机扫码；本机设置可选择 HID 扫码枪。同浏览器内按租户、门店隔离，钱包设置、存酒设置与统一扫码页折叠设置共用。保存失败明确提示仅会话生效。
- 扫码枪输入组件只处理专用输入框，Enter 完成，不拦截整页键盘，不请求相机、USB 或串口授权。支持手工识别、失焦恢复、后台暂停、处理中清空残留和禁止排队。
- 员工统一扫码、消费收款、原账单收款、选客、充值积分、卡业务、取酒与商品条码维护接入共享适配器。公共顾客扫描商户码和顾客存酒申请继续使用原相机组件。
- 统一扫码枪核销页成功后自动接收下一张，保留最近 10 条核销摘要。成功授权码在会话中有限去重；原服务端授权和幂等不变。明确拒绝后可继续，未决交易仍要求原员工恢复结果。
- 未开始办理时可替换个人码顾客；消费表单打开后停止接收。明确 paid 后自动清空顾客和金额并恢复接收，保留金额及查看原账单链接；confirmation_required 保持原账单流程，未知结果不放行。相机模式维持原成功结果页。
- 商户直接存酒新增瓶身扫码选品：扫描本店唯一可用酒品，每次加入一瓶未开瓶酒到草稿；扫码枪连续接收，相机单次选品。上限 50 行、每行最多 10000 瓶；开瓶、照片、标签、备注、来源、规则、存放位置有差异时不静默合并。必须原收存确认后才登记库存。
- 商品维护扫码仍只填写条码，原格式、唯一性及保存规则不变；未匹配或多匹配不自动建商品。保留前导零，超长内容完整接收后校验拒绝，不能截断为有效的另一条码。

## 架构与影响

`shared-commerce/scanning` 负责设备偏好、设置、输入及员工适配；`staff-scan` 负责连续核销状态；`wine-storage/merchant` 负责瓶身查询和草稿加瓶。业务规则仍由原模块及后端负责。

无 API、后端、权限、订阅、数据库迁移、依赖或运行配置修改。设备设置不授予业务资格。未启动后端、执行 SQL 或改动真实业务数据。初始工作区的钱包展示和 EmployeeStores 等无关修改未被本次编辑覆盖。

## 测试与检查

| 检查 | 结果 / 证据 |
| --- | --- |
| 本次关联行为测试 | 169 项通过，`target/scan-device-final-focused.log` |
| 偏好和输入 | 默认值、租户/门店隔离、存储失败、同页共享、Enter/重复回车、后台/disabled、手动识别、失焦及账号切换 |
| 顾客码 | 连续不同码、成功码重复拦截、个人码替换、消费锁定、paid 自动接收、待确认原路由、未决恢复及权限拒绝 |
| 商品码 | 一维码/前导零、同码多瓶、无匹配/多匹配、数量/50行上限、开瓶/照片/标签/规则/位置保护、迟到查询丢弃、不自动提交 |
| 原相机及相邻业务 | 原 staff-scan、卡操作、存酒页面及原 ProductBarcodeScanner 回归；完整前端回归见下文 |
| 独立代码审查 | APPROVE；修复超长输入截断、50行上限后复查，追加 paid 连续状态也已复查 |
| 差异检查 | `git -c core.whitespace=cr-at-eol diff --check` 通过；保留原测试文件 CRLF，不做换行重写 |

关联命令：

```powershell
node --test src/test/frontend/scan-device.test.mjs src/test/frontend/receipt-barcode.test.mjs src/test/frontend/staff-scan-page.test.mjs src/test/frontend/wine-storage-page.test.mjs src/test/frontend/cards-operations-ui.test.mjs
```

红绿证据：`target/scan-device-red.log`、`staff-device-red.log`、`bottle-device-red.log`；审查边界补测 `scan-device-review-red.log`（2 项失败）与 `scan-device-review-green.log`；付款连续 `scan-device-payment-red.log`（1 项失败）与 `scan-device-payment-green.log`。

## 完整前端回归和基线

部分现有测试需 `--experimental-transform-types --loader ./scripts/tests/workforce-loader.mjs`，不能以裸 node 的 Vue/TS 加载错误判业务失败。根目录的 Vite SSR 测试停留过久后，转用独立源码副本，保留原工作区。

- 源码副本：`target/scan-device-validation-20261104-062142/`。
- 除三个 Vite SSR 文件外的完整前端套件：1140 项，1113 通过、27 失败；`frontend-all.log`。
- 原钱包 Vite SSR 三文件独立运行：14 项全部通过；`vite-scan-regression.log`。使用 `--test-concurrency=1 --test-timeout=90000` 避免并发测试服务冲突。
- 去掉本次改动的对照副本：`target/scan-device-baseline-20261104-0627/`，只在副本恢复本次已有文件的 HEAD，保留主工作区。运行失败所在三个原测试文件得到同样 27 项失败，逐项名称一致；`baseline.log`、`target/scan-device-baseline-comparison.json`。
- 基线失败分布：`attendance-rules-ui.test.mjs` 1 项、`workforce.test.mjs` 25 项（当前日期与固定排班日期预期不符）、`product-history-surfaces.test.mjs` 1 项（历史卡报表测试缺少当前组件需要的钱包 workspace）。不扩大本次范围修改这些测试或业务。
- 最终扫码枪边界和付款补充后，重新运行本次关联 169 项全通过；不把完整套件称为全绿。

## 尚未完成的验收

### 本期浏览器目标

按用户最新要求，本期先面向 Windows 桌面版 Google Chrome、Microsoft Edge 当前稳定版。现有扫码枪输入使用标准文本框、Enter、focus 和 visibilitychange，不依赖浏览器扩展、WebUSB 或串口接口。本次仅明确兼容范围，没有新增浏览器专用代码。

| 浏览器 | 软件流程检查方式 | 当前状态 |
| --- | --- | --- |
| Chrome（Windows） | 本地测试环境键盘模拟扫码；检查回车、连续接收、失焦/后台、状态锁定、门店设置及相邻页布局 | 待浏览器实际验收 |
| Edge（Windows） | 与 Chrome 相同，独立检查其浏览器配置中的设备偏好保存 | 待浏览器实际验收 |

没有扫码枪时，先使用键盘输入或粘贴完整测试码后按 Enter 验证软件流程，继续使用既有组件自动化测试；这不代表完成 HID 设备验收。恢复浏览器访问后，无需等待扫码枪即可执行上述两种浏览器的软件验收。

浏览器工具实际返回：`Local browser use is disabled in Work settings`。因此未完成登录后桌面、390px/320px、中英文截图、相邻页几何对齐和真实 DOM 焦点验收；组件 renderer 测试不能替代浏览器焦点、布局或相机设备验收。

未接入实体扫码枪。交付前仍需用实际设备确认 HID 模式、Enter/CRLF 后缀、键盘布局、前导零、手机屏幕二维码和瓶身一维码识读，以及真实连续扫描的焦点恢复。不要用真实扣款/核销作为软件开发测试数据。

## 构建与交付

最终源码同步到上述验证副本后执行 `npm run build`（vue-tsc + 日期输入检查 + Vite），已通过，日志 `final-build.log`（Vite built in 31.46s）。关键源文件指纹位于 `target/scan-device-final-source-hashes.json`；此构建为本地验证产物，不代表生产发布。

本机可随时把扫码方式改回相机。若将来撤销实现，仅撤销本次前端与文档变更并重建，保留其他未提交内容；无需数据库回退或后端重启。生产发布仍需另外明确授权。
