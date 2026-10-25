# 酒水寄存交付与本地验证（2026-10-25）

## 实现范围

- 后端 `com.rpb.reservation.winestorage`：申请、收存、拒绝、撤销、规则版本、独立物品台账、领取意图及明细、照片及一次绑定、业务流水、幂等命令、再次寄存及纠错说明。
- 前端 `src/modules/wine-storage`：顾客存酒卡与商户管理、门店选择、中英文/编号检索、新酒名、逐瓶开瓶、批量领取、双向授权、历史、规则及照片追加。表单、收存、照片、领取展示和后续证据各有独立组件。
- 共享目录通过 catalog 应用边界接入，产品建议只在所属商户审核；审核关联旧产品或创建当前门店 storage-only、NULL 价格/税务的新产品。目录维护支持保持空价，销售报价拒绝未配置产品。
- 钱包增加概要入口及专用码跳转；复用现有扫码/二维码、身份、租户/门店资料、AppGate、商业授权及 RBAC。新业务关闭后保留查询及受控旧业务交付。
- V077 迁移；接口说明 `docs/api/WINE_STORAGE_API.md`；操作指南 `WINE_STORAGE_GUIDE.md`；设计及发布说明同目录。
- 没有修改资金、积分、卡次数或商户可售库存；没有生产发布、提交或推送。

## 工作区及数据库

工作区 D:\RPB，原有 8 个钱包组件改动、3 份未跟踪钱包验收资料及 rpb信息.txt 保留。新增代码与相关接入改动保留在当前工作区。

所有数据库验证读取 `target/local-postgres-current.txt`，使用 127.0.0.1:61644 / postgres，用户 postgres、空密码。运行时归属 D:\RPB。本次 HTTP/页面数据位于独立 schema `wallet_browser_fbe64f4b60cf410aaa938064f4c0dbb8`；8086 后端、5186 环回代理，没有替代或改写原 8081 运行时、public 或指针原业务 schema。自动测试另建随机隔离 schema，执行 V001-V077 迁移并仅清理本测试创建的 schema。

夹具照片是本地生成的虚构 PNG，用于验证上传、鉴权和证据绑定，不作为实物识别或真实手机照片验收。

## 命令与结果

以下各轮有重叠用例，不能相加当作独立测试总数。

| 验证 | 结果 | 证据 |
|---|---|---|
| 目录、卡、钱包资金/积分/凭证/交易、寄存组合回归 | 38 组 / 268 项，0 失败、0 错误、0 跳过 | target/wine-storage-validation/broad-suite.json；target/wine-storage-tests.log |
| 目录、卡、AppGate、图片及寄存专项 | 17 组 / 89 项，0 失败、0 错误、0 跳过 | target/wine-storage-validation/final-suite.json；target/wine-storage-final-tests.log |
| 最后受影响寄存模块专项 | 6 组 / 31 项，0 失败、0 错误、0 跳过 | target/wine-storage-final-module.log |
| 前端扫码、相机状态、卡扫码/恢复、目录边界、寄存重试 | 24 项通过 | target/wine-storage-frontend-tests.log |
| Vue 类型检查及 Vite 构建 | 通过 | target/wine-storage-build.log |
| 真实 HTTP 事务与权限流程 | 7 组通过 | target/wine-storage-validation/api-acceptance.json |
| 严格数量/期限与附件输入 HTTP | 5 组通过，非法输入 400，未扣瓶数/未创建新规则 | target/wine-storage-validation/strict-api.json |
| 现有门店开关 API | 包含新产品，关闭仍可读历史/管理开关，重新开启恢复新业务 | target/wine-storage-validation/product-switch.json |
| 跨 JVM 命令重试 | 实际重启后返回原交付结果，仅扣减一次、仅一条交付流水 | target/wine-storage-validation/restart-replay-result.json |
| 实际表名的资金/积分/卡隔离复核 | 补跑 1 项通过，0 失败/错误 | target/wine-storage-finance-test.log；target/wine-storage-validation/financial-isolation-test.json |
| 隔离租户资金/积分/卡守恒 | 全部寄存 HTTP/页面流程后，8 张真实资金/积分/卡业务表仍为 0 行 | target/wine-storage-validation/financial-conservation.json |
| 空价目录编辑及历史快照 | 修改中英文名称后价格/税务仍 NULL、渠道仍仅 storage；旧寄存名不变 | target/wine-storage-validation/catalog-maintenance.json |

主要命令：

```powershell
mvn -q '-Dtest=Storage*Test,Catalog*Test,Card*Test,Wallet*Test,StoreProductSettingsServiceTest,ProductLineEntryRegistryTest' package
mvn -q '-Dtest=Storage*Test,Catalog*Test,Card*Test,AppGateServiceTest,StoreProductSettingsServiceTest,ProductLineEntryRegistryTest,WalletProofImagesTest' package
mvn -q '-Dtest=Storage*Test' package
node --experimental-strip-types --test src/test/frontend/wine-storage.test.mjs src/test/frontend/cards-scan.test.mjs src/test/frontend/cards-recovery.test.mjs src/test/frontend/catalog-boundary.test.mjs src/test/frontend/wallet-camera.test.mjs
npm run build
node scripts/validation/wine-storage-api-acceptance.mjs
node scripts/validation/wine-storage-strict-api.mjs
```

夹具脚本拒绝覆盖已存在的本次 fixture 元数据。复核时只使用该 fixture 的 schema/会话，不对真实租户运行脚本。启动脚本从指针读取数据库并复制 task-owned runtime JAR，避免 Windows 运行中的 JAR 锁住下一次构建。

## 验收覆盖

| 场景 | 证据 |
|---|---|
| 申请不直接成为物品；确认、拒绝、撤销 | StoragePostgresTest.pendingConfirmRejectCancelAndImmutableSnapshots + HTTP |
| 中英文及编号；未审核建议不可选；审核关联/新建；重复审核不重复建品 | StoragePostgresTest.suggestionsArePrivateUnpricedAndTenantStoreCustomerPhotosAreIsolated / newNameCanBeLinkedAndCorrectionsRemainAppendOnly + HTTP |
| 整瓶部分领取、开瓶整件、同店批量及重试 | partialOpenedBatchAndReplay + HTTP、页面 |
| 商户码必须顾客显式授权；授权超时、通用身份码拒绝 | merchantIntentRequiresCustomerAuthorizationAndExpires / changedQuantityCancelsAndForeignAuthorizationDoNotDebit + 前端、HTTP、页面 |
| 并发不同领取意图不超扣；同命令并发只交付一次 | simultaneousIntentsNeverOverdraw / concurrentRetryOfSameHandoverReturnsOneCommittedResult |
| 提前申请，到期后交付整批拒绝；数量变化整批回滚 | preExpiryIntentCannotDeliverAtExpiryAndBatchRollsBack / changedQuantityCancelsAndForeignAuthorizationDoNotDebit + HTTP |
| 自然月月末、闰年、门店时区、夏令时、到期瞬间 | StorageRulesTest |
| 规则/产品更名不回写旧快照 | pendingConfirmRejectCancelAndImmutableSnapshots + catalog-maintenance.json |
| 实际领取后再次寄存产生新 pending；不覆盖旧记录；实际收存修正数量正确占用原领取额度 | delistingDoesNotBlockDeliveryAndRedepositCreatesNewPending / verifiedRedepositQuantityDoesNotConsumeBottlesThatWereNotReceivedAgain |
| 租户、门店、顾客隔离；跨店/跨顾客批量拒绝；照片归属、阶段、重复证据 | StorageAccessTest、StoragePostgresTest + 真实 HTTP |
| 关闭/未授权/到期/门店关闭拦截新业务，受控履约仍需权限 | StorageAccessTest、StoreProductSettingsServiceTest + HTTP |
| 产品下架不阻止旧物品领取 | delistingDoesNotBlockDeliveryAndRedepositCreatesNewPending + HTTP |
| 空价不是 0，更新仍可空价，销售渠道不能接受未定价产品 | StorageCatalogPricingTest + HTTP、目录页面 |
| 小数/字符串/null/溢出数量和小数期限 | StorageJsonTest + strict-api.json |
| Map 键序不影响重试；同键异参拒绝 | equivalentObjectKeyOrderReplaysTheOriginalCommittedCommand |

## 页面验证

已在本地真实服务上验证钱包“存酒卡”入口和概要。测试租户没有开钱包计划，寄存仍能通过独立业务入口使用，资金/积分建账不因此产生。

顾客选择虚构物品 2 瓶，页面生成专用 QR；商户通过组件的粘贴解析入口核对并确认，状态变为已交付，剩余从 3 变成 1。反向流程：商户生成 1 瓶确认意图，顾客解析后明确授权，授权后仍剩 1；商户确认交付后变成 0。验证的是 QR 展示、专用码解析及真实后端动作，不是手机摄像头识别。

中文/英文界面、照片缩略图、历史/到期时间、钱包入口及未定价目录编辑已检查；用户填写的中文规则及备注不随语言切换改写。截图位于 target/wine-storage-validation/：wallet-entry.png、customer-final.png、customer-en.png、customer-qr.png、merchant-qr.png、staff-delivered.png、reverse-delivered.png、catalog-unpriced.png。

## 已定位并修复的验证问题

- 初始日期测试无实现时失败，随后实现自然月规则。
- 新增 JSON 反例证明默认 Jackson 会截断小数；寄存请求改为 opt-in 严格整数，随后通过正反例及 HTTP 400 验证，不修改其他模块的全局反序列化配置。
- 原目录对 NULL 税务调用翻译会抛 Invalid arguments，改为“未定价/税务待配置”；实际页面空价编辑及快照验证通过。
- 继承目录测试的投放版本夹具被更新后未同步，修正夹具版本引用。
- 早期并发 Maven 编译曾造成临时 class 缺失，后续按序运行，最终证据均来自完成的验证轮次。
- 本机 Windows JDK Unix socket 初始化失败，按既有本地指南加工作区临时 socket 目录和 simple HTTP client，仅用于测试进程。
- Windows 运行中的目标 JAR 导致 repackage 无法改名；停止本任务进程后打包成功，启动改为独立 runtime JAR 副本，生产配置未改。
- 验证脚本最初使用不存在的 card_holdings 名称，最终改为实际 customer_cards、card_entries、card_purchase_orders、card_payment_links 并直接断言，未跳过实际卡表；HTTP/页面后守恒查询为 0。
- 前端旧相机测试需要 Node 22 的 --experimental-strip-types；按项目测试方式启用后通过。

## 未验证项及边界

未验收真实 iPhone/微信、Android 摄像头及相册/HEIC 转换、真实手机原图与方向；未单独做操作系统文件选择器完整验收，也未做大数据量/高并发压力测试。浏览器未执行真实资金支付或真实积分发放，相关资金/积分行为由 PostgreSQL 回归测试覆盖。本任务没有生产发布和线上验证。

本版不实现跨店批量、过期领取、延期、自动处置、瓶内毫升估算或余量扣减。纠错只追加说明，不重写已完成数量或期限。后续商业价格需平台按既有方式配置，迁移没有虚构订阅价格或给真实租户自动开通。

本次隔离测试后端及代理在验收完成后停止；随机 schema、虚构记录、日志、构建和截图保留供复核。未停止原本地服务。接口、数据库、TDD 和代码自审详见 WINE_STORAGE_REVIEW.md。
