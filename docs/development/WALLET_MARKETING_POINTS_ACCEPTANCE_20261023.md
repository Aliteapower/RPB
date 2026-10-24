# 钱包营销与手动积分实现验收

日期：2026-10-23。项目：D:\RPB。状态：实现及本地验证完成；未提交、未推送、未部署。原有改动、设计方案与无关文件保留。

## 实现范围

| 模块 | 已实现及边界 |
| --- | --- |
| wallet.marketing | 可修改基础档、四档活动草稿、修订/发布/停用/归档、有效时间和优先级、严格单档报价；每次只选一档 |
| 顾客充值 | 本人申请、零余额首次开户、待单恢复、付款声明/取消/历史、原门店PayNow码；商户核实本金前不入账 |
| wallet.funding | 本金/赠送/冻结分量、FIFO批次、按比例或本金优先、分项消费与原单恢复、批次退出、赠送单独退出 |
| loyalty | 独立积分计划/账户/账本/兑换/冲正/审计；不抵现金、无配送；适配层不直接访问积分私表 |
| 接口与页面 | 中英文、独立权限、精确路由、会话作用域、组件版本头、账户预览版本、真实批次详情、分项报表 |
| 迁移 | V059–V062；旧余额和冻结全为本金，历史池不伪造旧批次，旧未用报价失效，积分顾客身份保护 |

实际请求和权限见 [API实现说明](../api/WALLET_MARKETING_POINTS_IMPLEMENTED_20261023.md)。迁移与回退风险见 [发布说明](WALLET_MARKETING_POINTS_RELEASE_NOTES_20261023.md)。

## 自动验证

后端相关测试分批运行，修改后重跑受影响范围，最终22个suite、175项：0失败、0错误、0跳过。包括资金57、营销13、积分17、分摊算法5、迁移1、组件报表3、缓存兼容3、接口/权限/CSRF及App Gate等。没有把局部验证称为全仓库所有测试。

关键命令：

```powershell
mvn '-Dtest=Wallet*Test,FundingAllocationTest,Points*Test,AppGateRequiredPermissionTest,AppGateServiceTest' test -q
mvn '-Dtest=WalletMarketingPostgresTest,WalletFundingApiTest,WalletStaffMarketingApiTest,PointsPostgresTest' package -q
mvn '-Dtest=WalletMarketingPostgresTest#strictDtoRejectsClientAmountsQuantityArraysAndForeignIdentity,WalletFundingMigrationPostgresTest' package -q
mvn '-Dtest=WalletMarketingPostgresTest' test -q
node --experimental-transform-types --test --test-timeout=90000 --test-concurrency=1 src/test/frontend/wallet*.test.mjs
npm run build
git diff --check
```

前端钱包全量68项：0失败、0取消。包含实际Vue组件、报价过期/晚到/切换、服务端扣款分项及冻结版本、未知结果重试、异常实收退款预览、积分与资金上下文切换等。vue-tsc及Vite生产构建通过，diff检查无错误；现有3个Java文件提示Git后续将LF转换为CRLF，不属于检查失败。

后端native PostgreSQL均读取本工作区 `target/local-postgres-current.txt`，当前61644，postgres库、postgres用户、空密码，测试自建随机schema。验证包括带余额、冻结及待退款的旧数据迁移、旧退款完成/取消、历史消费退款与新消费计数、分摊精度、FIFO混合策略、部分退款、批次退出后消费退款、并发与幂等。

最终测试汇总：`target/wallet-marketing-e2e-20261023/backend-test-summary.json`；最后营销13项日志 `target/marketing-proxy-final-tests.log`。前端日志 `target/wallet-frontend-tests.log` 和 `target/wallet-final-frontend-build.log`。

## 真实浏览器与API联调

使用独立schema `wallet_browser_3ffcfb34bb58465e8b53db0dea00756b`、测试租户及测试顾客。backend仅127.0.0.1:8083，静态站/代理仅127.0.0.1:5183；会话鉴权保持启用，商户与顾客使用独立夹具会话，本轮未重新验收登录流程。

实际运行jar与当前构建jar SHA256相同：`CC9539F93393951ACAE43D4D98F797CFEE23C8A769C58D45343F16CEBD76C8B1`。前端使用最终 `dist`。16:20之后验证运行日志 `backend-verified.log` 无ERROR/异常；较早发现的Spring代理字段访问错误另保留在旧日志，修复后重启再完成全部下列业务。

| 操作 | 真实页面及数据库结果 |
| --- | --- |
| 商户开关与发布 | 开启自助与积分，发布默认四档草稿，成功保存版本与活动状态 |
| 顾客选择100+10 | 单选档位，服务端报价本金100/赠送10/合计110，提交生成本人待核实单 |
| 申请与付款声明 | 两阶段账户均0、无资金流水；声明只记录时间及备注 |
| 商户确认现金100 | 留空纸质收据成功；账户本金100、赠送10、合计110，只有一笔入账 |
| 顾客出码、商户扫码 | 从顾客实际渲染的WC图片，经商户图片识别入口选中同一钱包；不是直接注入扫码结果 |
| 消费55 | 确认前服务端预览本金50/赠送5；确认后双方同一账单已结清，实际扣款与预览一致；余额55=本金50+赠送5 |
| 停用活动 | 原已入账赠送不消失，活动状态更新为停用 |
| 充值码与普通充值 | 从实际WP图片识别同一钱包；员工普通充值5+0，留空现金收据确认，余额60=本金55+赠送5 |
| 积分独立 | 手动+100→兑换30=70；手动扣10→60→管理员原单冲正→70；期间钱包资金不变；双方明细可见兑换说明 |
| 原消费退款预览 | 输入11，显示返还原本金10、赠送1、现金0；仅预览未提交，最终余额不变 |
| 真实批次详情 | 顾客消费流水展开服务端批次ID及50/5分项，无伪造旧批次 |
| 报表 | 实收现金105、充值本金105、赠送10、消费本金50/赠送5、余额本金55/赠送5；积分不进入金额，总账组件与批次不一致数均0 |

实际浏览器仅验收桌面视口的上述关键流程；前端组件与响应式样式检查不等于手机实机验收。真实银行转账、刷卡设备与摄像头授权/对焦未测试；二维码端到端使用真实页面生成图片及真实解码入口。

## 保护性检查与证据

最终17项API保护及核对通过，消费前另复验14项。包含未登录401、未知金额/数量/赠送/身份字段400、普通充值金额字符串、旧组件客户端409、退款版本必填、充值原键与新键不重复入账、已消费码不重复扣款、过期码原成功请求回放、积分余额及报表分量核对。全部检查前后资金及积分余额、版本、流水数相同。

证据保存在 `D:\RPB\target\wallet-marketing-e2e-20261023`：

- pending-customer-request.json、declared-not-credited.json、credited-campaign.json、after-scan-charge.json、points-redemption.json、final.json。
- guard-checks.json（17项）及reconciliation.json。
- merchant-charge-preview.png、merchant-charge-paid.png、customer-wallet-final.png、customer-funding-lot.png、customer-points-70.png、refund-preview-original-split.png。
- backend-test-summary.json、backend-verified.log、backend-verified-error.log。

本轮临时服务验收后停止，测试schema与证据保留；原8081服务、原前端及61644 PostgreSQL不停止、不更换数据库。已有无关文档与信息文件SHA256与开发前记录一致。没有生产充值、扣款、退款或发布动作。


## 追加验收：PayNow 与顾客直接确认

- 前端钱包全量 72/72 通过（target/wallet-followup-frontend.log）；随后复用既有付款方式样式，组件 27/27 复验通过（target/wallet-followup-component-final.log）。最终 vue-tsc 与 Vite 构建通过（target/wallet-followup-build.log）。
- 后端营销 PostgreSQL 14、资金 API 6、商户营销 API 6、本地安全 4，合计 30/30，通过并打包（target/wallet-followup-backend-final.log）。数据库仍使用指针端口 61644，新建随机测试 schema。
- 测试先覆盖移除顾客报价按钮的失败，再实现；新增覆盖无档位禁用、PayNow 未配置提示、自动报价失败不创建、重复点击、网络响应丢失后复用报价/幂等键、切店丢弃旧响应。后端覆盖 PayNow 未配置拒绝报价、报价后配置失效拒绝创建、恢复配置后待核实及二维码、计划停用渠道不重新提供。
- 真实浏览器在原隔离 schema 新建专用四档测试活动，选择100+10和PayNow，直接确认创建待核实单 24d43e88-dadb-47a3-aa71-2d588c06e132；显示二维码，解析实际二维码 TLV 54 为100.00。点击付款声明后仍待核实；钱包6000分=本金5500+赠送500，资金流水3条、积分70均不变。
- 测试单已取消，专用测试活动归档，PayNow测试配置恢复停用。再次浏览器验证PayNow选项保留并显示配置提示，未产生充值入账或银行交易。原8081/5178服务和61644数据库未切换。
- 证据目录 target/wallet-marketing-e2e-20261023：customer-paynow-direct-confirm-final.png、customer-paynow-pending-summary.png、customer-paynow-pending-qr.png、customer-paynow-missing-setup.png、paynow-followup-result.json、paynow-followup-final.json、paynow-followup-restored.json。新后端使用 backend-paynow-followup.jar；最终前端样式构建刷新验证完成。

本次失败定位仅涉及测试夹具：模拟响应缺少 createdAt、错误正文形状与实际API不符，以及测试修改既有钱包配置未带当前version；均依据真实契约修正后重跑通过。尝试重新发布无新修订的已停用旧测试活动被409正确拒绝，改为创建独立测试活动，未放宽生命周期校验。
